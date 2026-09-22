package com.r2manager.android.core.result

import com.r2manager.android.core.error.AppError
import com.r2manager.android.core.error.ErrorMapper

/**
 * 轻量结果封装，供仓库 / 引擎层在不抛异常时表达成功或失败。
 *
 * 与 [AppError] 配合：失败侧统一携带归一化后的错误模型。
 */
sealed class Outcome<out T> {

    /** 成功。 */
    data class Success<out T>(val value: T) : Outcome<T>()

    /** 失败。 */
    data class Failure(val error: AppError) : Outcome<Nothing>()

    /** 是否成功。 */
    val isSuccess: Boolean
        get() = this is Success

    /** 是否失败。 */
    val isFailure: Boolean
        get() = this is Failure

    /** 成功时取值，否则 null。 */
    fun getOrNull(): T? = (this as? Success)?.value

    /** 失败时取错误，否则 null。 */
    fun errorOrNull(): AppError? = (this as? Failure)?.error
}

/**
 * 变换成功值。
 *
 * @param transform 映射函数
 */
inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

/**
 * 在成功值上执行副作用。
 *
 * @param action 副作用函数
 */
inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) {
        action(value)
    }
    return this
}

/**
 * 在失败时执行副作用。
 *
 * @param action 副作用函数
 */
inline fun <T> Outcome<T>.onFailure(action: (AppError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) {
        action(error)
    }
    return this
}

/**
 * 把可能抛异常的代码块包成 [Outcome]，异常经 [ErrorMapper] 归一化。
 *
 * @param operation 操作名（用于日志与错误定位）
 * @param block 待执行代码块
 */
inline fun <T> outcomeOf(operation: String, block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    Outcome.Failure(ErrorMapper.fromThrowable(t, operation))
}

/**
 * [outcomeOf] 的挂起版本，供仓库 / 引擎的 `suspend` 方法使用。
 *
 * @param operation 操作名
 * @param block 挂起代码块
 */
suspend inline fun <T> suspendOutcomeOf(operation: String, crossinline block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    Outcome.Failure(ErrorMapper.fromThrowable(t, operation))
}
