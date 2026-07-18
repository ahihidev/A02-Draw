package com.a02.draw.data.mapper

import android.database.sqlite.SQLiteException
import com.a02.draw.core.common.result.AppError
import java.io.IOException
import retrofit2.HttpException

internal fun Throwable.toAppError(): AppError = when (this) {
    is IOException -> AppError.Network
    is HttpException -> AppError.Http(code = code(), message = message())
    is SQLiteException -> AppError.Database(message)
    else -> AppError.Unknown(message)
}
