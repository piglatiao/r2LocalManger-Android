package com.r2manager.android.domain.security

import com.r2manager.android.core.constants.TransferConstants

/**
 * PIN 连续失败计数与锁定时长（P3，R-03）。
 *
 * 语义：连续 [TransferConstants.PIN_MAX_ATTEMPTS]（5）次失败锁定 [TransferConstants.PIN_LOCKOUT_MS]（30 秒）；
 * 锁定期间再次尝试直接返回剩余锁定时间；锁定到期后计数自动清零。
 *
 * `clock` 可注入，便于单元测试用假时钟推进。
 */
class PinAttemptTracker(
    private val clock: () -> Long = System::currentTimeMillis
) {

    private var failures: Int = 0
    private var lockedUntil: Long = 0L

    /**
     * 记录一次失败。
     * @return 若因此进入锁定（或已在锁定中），返回剩余锁定毫秒数；否则返回 0。
     */
    @Synchronized
    fun recordFailure(): Long {
        val remaining = remainingLockMs()
        if (remaining > 0L) return remaining

        failures++
        if (failures >= TransferConstants.PIN_MAX_ATTEMPTS) {
            lockedUntil = clock() + TransferConstants.PIN_LOCKOUT_MS
            failures = 0
            return TransferConstants.PIN_LOCKOUT_MS
        }
        return 0L
    }

    /**
     * @return 剩余锁定毫秒数；未锁定时为 0（并顺带清理已过期的锁定状态）。
     */
    @Synchronized
    fun isLockedOut(): Long = remainingLockMs()

    /** 解锁成功或用户重置后清除计数与锁定。 */
    @Synchronized
    fun reset() {
        failures = 0
        lockedUntil = 0L
    }

    /** @return 剩余可尝试次数（锁定中为 0）。 */
    @Synchronized
    fun remainingAttempts(): Int {
        if (remainingLockMs() > 0L) return 0
        return (TransferConstants.PIN_MAX_ATTEMPTS - failures).coerceAtLeast(0)
    }

    private fun remainingLockMs(): Long {
        val now = clock()
        val remaining = lockedUntil - now
        if (remaining > 0L) return remaining
        if (lockedUntil != 0L) {
            // 锁定已过期 → 清状态
            lockedUntil = 0L
            failures = 0
        }
        return 0L
    }
}
