package com.r2manager.android.data.remote.s3

import com.r2manager.android.core.constants.TransferConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分片规划单测：与桌面版 `R2Client._getMultipartPartSize` 逐字对齐。
 */
class MultipartPlanTest {

    private val mb = 1024L * 1024L

    @Test
    fun smallFileUsesDefault64Mb() {
        // 300MB / 10000 / 5MB = 6 → 6*5MB = 30MB < 64MB → 取 64MB
        assertEquals(64L * mb, MultipartPlan.partSize(300L * mb))
    }

    @Test
    fun hugeFileGrowsPartSize() {
        // 1TB / 10000 / 5MB = 20.97 → ceil = 21 → 21*5MB = 105MB
        val oneTb = 1024L * mb * 1024L
        assertEquals(21L * 5L * mb, MultipartPlan.partSize(oneTb))
    }

    @Test
    fun partCountRoundsUp() {
        assertEquals(2, MultipartPlan.partCount(64L * mb + 1, 64L * mb))
        assertEquals(1, MultipartPlan.partCount(1L, 64L * mb))
        assertEquals(0, MultipartPlan.partCount(0L, 64L * mb))
    }

    @Test
    fun multipartThresholdIsStrictlyGreaterThan300Mb() {
        assertFalse(MultipartPlan.shouldUseMultipart(300L * mb))
        assertTrue(MultipartPlan.shouldUseMultipart(300L * mb + 1))
        assertEquals(300L * mb, TransferConstants.LARGE_UPLOAD_THRESHOLD_BYTES)
    }

    @Test
    fun partLengthHandlesLastShorterPart() {
        assertEquals(64L, MultipartPlan.partLength(74L, 64L, 1))
        assertEquals(10L, MultipartPlan.partLength(74L, 64L, 2))
        assertEquals(0L, MultipartPlan.partLength(74L, 64L, 3))
    }
}
