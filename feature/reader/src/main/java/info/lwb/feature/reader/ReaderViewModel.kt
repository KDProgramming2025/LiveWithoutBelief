package info.lwb.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.lwb.data.repo.repositories.ReadingProgressRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val progressRepo: ReadingProgressRepository,
    private val settingsRepository: ReaderSettingsRepository,
) : ViewModel() {

    private val articleIdState = MutableStateFlow("")
    private val htmlBodyState = MutableStateFlow("")
    private val blocksState = MutableStateFlow<List<ContentBlock>>(emptyList())
    private val pageIndexState = MutableStateFlow(0)

    val fontScale = settingsRepository.fontScale.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0)
    val lineHeight = settingsRepository.lineHeight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.2)

    private val pagesState = combine(blocksState, fontScale) { blocks, scale ->
        paginate(blocks, fontScale = scale)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val headings = pagesState.map { buildHeadingItems(it) }

    val uiState: StateFlow<ReaderUiState> = combine(
        articleIdState,
        pagesState,
        pageIndexState,
        fontScale,
        lineHeight,
    ) { articleId, pages, pageIndex, fScale, lHeight ->
        ReaderUiState(
            articleId = articleId,
            pages = pages,
            currentPageIndex = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
            fontScale = fScale,
            lineHeight = lHeight,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState.EMPTY)

    fun loadArticle(articleId: String, htmlBody: String) {
        articleIdState.value = articleId
        htmlBodyState.value = htmlBody
        blocksState.value = parseHtmlToBlocks(htmlBody)
        // Observe existing progress
        viewModelScope.launch {
            progressRepo.observe(articleId).collect { p ->
                if (p != null && p.totalPages > 0) {
                    pageIndexState.value = p.pageIndex.coerceIn(0, p.totalPages - 1)
                }
            }
        }
    }

    fun onPageChange(newIndex: Int) {
        pageIndexState.value = newIndex
        persistProgress()
    }

    fun onFontScaleChange(v: Double) = viewModelScope.launch { settingsRepository.setFontScale(v) }
    fun onLineHeightChange(v: Double) = viewModelScope.launch { settingsRepository.setLineHeight(v) }

    private fun persistProgress() {
        val pages = pagesState.value
        val articleId = articleIdState.value
        if (articleId.isBlank() || pages.isEmpty()) return
        viewModelScope.launch {
            progressRepo.update(articleId, pageIndexState.value, pages.size)
        }
    }
}

data class ReaderUiState(
    val articleId: String,
    val pages: List<Page>,
    val currentPageIndex: Int,
    val fontScale: Double,
    val lineHeight: Double,
) {
    companion object { val EMPTY = ReaderUiState("", emptyList(), 0, 1.0, 1.2) }
}