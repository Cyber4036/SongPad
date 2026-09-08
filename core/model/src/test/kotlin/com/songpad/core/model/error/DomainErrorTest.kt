package com.songpad.core.model.error

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DomainErrorTest {
    @Test
    fun `failures carry typed errors and foreign throwables are wrapped`() {
        val r: Result<Int> = domainFailure(CaptureError.PermissionDenied)
        assertThat(r.domainErrorOrNull()).isEqualTo(CaptureError.PermissionDenied)
        val foreign = Result.failure<Int>(IllegalStateException("boom"))
        assertThat(foreign.domainErrorOrNull()).isInstanceOf(DataError.Unexpected::class.java)
        val ran = runDomain({ DataError.Io(it.message ?: "") }) { error("disk") }
        assertThat(ran.domainErrorOrNull()).isEqualTo(DataError.Io("disk"))
    }
}
