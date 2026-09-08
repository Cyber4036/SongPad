package com.songpad.core.model.logging

import com.songpad.core.model.error.DomainError

/** Tiny logging seam so core modules never import android.util.Log. :app binds an Android logger. */
interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, t: Throwable? = null)
    fun e(tag: String, message: String, t: Throwable? = null)

    /** Every typed failure that reaches a ViewModel goes through here, so a domain error is traceable end to end. */
    fun error(tag: String, error: DomainError) = e(tag, "${error::class.simpleName}: ${error.message}")

    object Noop : Logger {
        override fun d(tag: String, message: String) {}
        override fun i(tag: String, message: String) {}
        override fun w(tag: String, message: String, t: Throwable?) {}
        override fun e(tag: String, message: String, t: Throwable?) {}
    }
}
