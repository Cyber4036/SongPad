// Lock-free single-producer / single-consumer ring buffer of int16 PCM frames (§7.2).
//
// Producer: the Oboe callback thread (URGENT_AUDIO). It copies bytes in and returns. No allocation, no
// locks, no logging on that path — ever.
// Consumer: the writer thread, one priority level below, draining to disk.
//
// Capacity is a power of two and sized for >= 2 seconds of audio so a transient writer stall
// (page-cache flush, GC pause) cannot drop samples. Overrun is counted, never blocked on.
#pragma once

#include <atomic>
#include <cstdint>
#include <cstring>
#include <memory>

namespace songpad {

class RingBuffer {
public:
    explicit RingBuffer(uint32_t capacitySamples)
        : capacity_(roundUpPow2(capacitySamples)), mask_(capacity_ - 1),
          data_(new int16_t[capacity_]), head_(0), tail_(0), overruns_(0) {}

    uint32_t capacity() const { return capacity_; }

    // Producer side. Returns the number of samples written (< count on overrun).
    uint32_t write(const int16_t* src, uint32_t count) {
        const uint32_t head = head_.load(std::memory_order_relaxed);
        const uint32_t tail = tail_.load(std::memory_order_acquire);
        const uint32_t free = capacity_ - (head - tail);
        uint32_t n = count;
        if (n > free) { overruns_.fetch_add(count - free, std::memory_order_relaxed); n = free; }
        if (n == 0) return 0;
        const uint32_t pos = head & mask_;
        const uint32_t first = std::min(n, capacity_ - pos);
        std::memcpy(data_.get() + pos, src, first * sizeof(int16_t));
        if (n > first) std::memcpy(data_.get(), src + first, (n - first) * sizeof(int16_t));
        head_.store(head + n, std::memory_order_release);
        return n;
    }

    // Consumer side. Returns the number of samples read.
    uint32_t read(int16_t* dst, uint32_t maxCount) {
        const uint32_t tail = tail_.load(std::memory_order_relaxed);
        const uint32_t head = head_.load(std::memory_order_acquire);
        const uint32_t avail = head - tail;
        const uint32_t n = std::min(avail, maxCount);
        if (n == 0) return 0;
        const uint32_t pos = tail & mask_;
        const uint32_t first = std::min(n, capacity_ - pos);
        std::memcpy(dst, data_.get() + pos, first * sizeof(int16_t));
        if (n > first) std::memcpy(dst + first, data_.get(), (n - first) * sizeof(int16_t));
        tail_.store(tail + n, std::memory_order_release);
        return n;
    }

    uint32_t available() const {
        return head_.load(std::memory_order_acquire) - tail_.load(std::memory_order_acquire);
    }

    uint64_t overruns() const { return overruns_.load(std::memory_order_relaxed); }

    void clear() { tail_.store(head_.load(std::memory_order_acquire), std::memory_order_release); }

private:
    static uint32_t roundUpPow2(uint32_t v) {
        uint32_t p = 1;
        while (p < v) p <<= 1;
        return p;
    }

    const uint32_t capacity_;
    const uint32_t mask_;
    std::unique_ptr<int16_t[]> data_;
    std::atomic<uint32_t> head_;
    std::atomic<uint32_t> tail_;
    std::atomic<uint64_t> overruns_;
};

}  // namespace songpad
