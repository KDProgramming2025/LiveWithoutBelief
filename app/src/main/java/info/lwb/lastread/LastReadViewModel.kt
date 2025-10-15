/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.lastread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-scoped ViewModel exposing the last-read article for UI consumption.
 */
@HiltViewModel
class LastReadViewModel @Inject constructor(private val repo: LastReadRepository) : ViewModel() {
    /** Reactive snapshot of the last-read article (null until something is opened). */
    val lastRead: StateFlow<LastRead?> = repo.flow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    /** Persist a newly opened article so Home can offer Continue Reading. */
    fun save(articleId: String, indexUrl: String) {
        viewModelScope.launch { repo.save(articleId, indexUrl) }
    }
}
