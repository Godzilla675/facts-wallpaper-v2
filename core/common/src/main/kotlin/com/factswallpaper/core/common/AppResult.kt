package com.factswallpaper.core.common

/** Small, explicit result type used by repositories for user-triggered writes. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value
}

sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotFound(what: String) : AppError("$what not found")
    class InvalidOperation(message: String) : AppError(message)
    class Storage(cause: Throwable) : AppError("Local storage write failed", cause)
    class Unknown(cause: Throwable) : AppError("Unexpected failure", cause)
}

inline fun <T> runDomainCatching(block: () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (notFound: NoSuchElementException) {
        AppResult.Failure(AppError.NotFound(notFound.message ?: "Item"))
    } catch (io: java.io.IOException) {
        AppResult.Failure(AppError.Storage(io))
    } catch (t: Throwable) {
        AppResult.Failure(AppError.Unknown(t))
    }
