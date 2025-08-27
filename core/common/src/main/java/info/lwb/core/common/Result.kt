package info.lwb.core.common

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val throwable: Throwable) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

inline fun <T> Result<T>.onSuccess(block: (T) -> Unit): Result<T> { if (this is Result.Success) block(data); return this }
inline fun <T> Result<T>.onError(block: (Throwable) -> Unit): Result<T> { if (this is Result.Error) block(throwable); return this }

