package com.a02.draw.core.common.result

sealed interface AppError {
    data object Network : AppError
    data class Http(val code: Int, val message: String? = null) : AppError
    data class Database(val message: String? = null) : AppError
    data class Validation(val message: String) : AppError
    data class Unknown(val message: String? = null) : AppError
}
