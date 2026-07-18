package com.a02.draw.core.common.result

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> = apply {
    if (this is AppResult.Success) action(data)
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> = apply {
    if (this is AppResult.Failure) action(error)
}
