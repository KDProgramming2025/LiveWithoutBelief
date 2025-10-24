/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.e2e

/**
 * Lightweight abort flag to signal early termination of the E2E flow.
 * Keeps state entirely within the instrumentation side.
 */
object TestAbortState {
    @Volatile private var aborted: Boolean = false
    fun markAborted() { aborted = true }
    fun isAborted(): Boolean = aborted
    fun clear() { aborted = false }
}
