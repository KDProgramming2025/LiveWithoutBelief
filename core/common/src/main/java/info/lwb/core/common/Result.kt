/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.core.common

/**
 * Represents the outcome of an operation that can either succeed with [Success] containing data,
 * fail with [Error] containing a [Throwable], or still be in progress as [Loading].
 *
 * The generic type [T] is covariant (out) so that a `Result<Subtype>` can be used where a
 * `Result<Supertype>` is expected.
 */
sealed class Result<out T> {
    /**
     * Successful result wrapping the produced [data].
     */
    data class Success<T>(val data: T) : Result<T>()

    /**
     * Failed result capturing the underlying [throwable].
     */
    data class Error(val throwable: Throwable) : Result<Nothing>()

    /**
     * Loading state indicating the operation has started but not yet produced a value.
     */
    data object Loading : Result<Nothing>()
}

/**
 * Executes [block] if this result is a [Result.Success] passing the successful value.
 * Returns the original receiver for fluent chaining.
 */
inline fun <T> Result<T>.onSuccess(block: (T) -> Unit): Result<T> {
    if (this is Result.Success) {
        block(data)
    }
    return this
}

/**
 * Executes [block] if this result is a [Result.Error] passing the failure cause.
 * Returns the original receiver for fluent chaining.
 */
inline fun <T> Result<T>.onError(block: (Throwable) -> Unit): Result<T> {
    if (this is Result.Error) {
        block(throwable)
    }
    return this
}
