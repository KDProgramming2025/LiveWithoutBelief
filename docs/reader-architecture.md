# Reader Feature Architecture

Date: 2025-10-02
Status: Active

## Problem
Two different `ReaderViewModel` classes existed in the same feature module and served unrelated responsibilities (content acquisition vs. reading session state). This created ambiguity, brittle imports, and increased risk of incorrect DI resolution.

## Decision
Split responsibilities explicitly via distinct, intention-revealing names:

- `ArticlesViewModel` (was `viewmodels.ReaderViewModel`): Fetches article list & individual article content through domain use cases. No UI-specific pagination or appearance logic.
- `ReaderSessionViewModel` (was `feature.reader.ReaderViewModel`): Manages the *in-session* reading experience: HTML parsing to blocks, pagination, appearance (font scale, line height, background), and reading progress persistence.

## Rationale
Aligns with SRP and clean architecture layering:
- Data acquisition is a domain-facing concern (pure flows of `Result<T>`).
- Reading session transforms content into UI affordances (pages, styles) and tracks ephemeral user adjustments. This logic is presentation-layer specific.

Reduces cognitive overhead and prevents accidental import collisions inside Compose screens (`hiltViewModel<...>()`).

## Responsibilities Breakdown
| Concern | ArticlesViewModel | ReaderSessionViewModel |
|---------|-------------------|------------------------|
| Load articles list | Yes | No |
| Load article content | Yes | No |
| Observe & persist reading progress | No | Yes |
| HTML -> blocks parse | No | Yes |
| Pagination | No | Yes |
| Appearance (font scale, line height, background) | No | Yes |
| Exposed unified UI state | No (separate flows) | Yes (`ReaderUiState`) |

## Data Flow Overview
```
GetArticlesUseCase ─┐
                     ├─> ArticlesViewModel.articles (Result<List<Article>>)
GetArticleContentUseCase ─> ArticlesViewModel.articleContent (Result<ArticleContent>)

ArticleContent.html (when success) ─> ReaderSessionViewModel.parseHtmlToBlocks() ─> blocks ─> paginate() ─> pages
User appearance adjustments ─> ReaderSessionViewModel (fontScale, lineHeight, background) ─> uiState
User page changes ─> pageIndexState ─> progressRepo.update()
```

## DI / Hilt Notes
Both remain `@HiltViewModel`; scoping is independent. They are resolved in `ReaderRoute` explicitly into separate local vals:
- `val svcVm: ArticlesViewModel = hiltViewModel()`
- `val vm: ReaderSessionViewModel = hiltViewModel()`

No cross-dependency between them to avoid a cycle. Communication path is via navigation argument (`articleId`) only (WebView loads remote `indexUrl`).

## Future Extensions
- Introduce a domain use case that returns pre-parsed block models to move parsing out of the session VM if needed.
- Add bookmarking and annotation repositories; extend `ReaderSessionViewModel` with lightweight delegators rather than bloating.
- Consider a unified `ReaderScreenState` sealed interface if later we blend acquisition & session into a single screen.
- Multi-language support: reinject language setting into both VMs; augment `ReaderSessionViewModel` to invalidate pagination on language switch.

## Testing Strategy
- `ArticlesViewModelTest`: Fake use cases emitting sequences of `Result` states; assert state flows update.
- `ReaderSessionViewModelTest`: Supply fake `ReadingProgressRepository` & `ReaderSettingsRepository`; verify pagination, progress persistence boundaries, and appearance mutation.

## Naming Guidelines
Avoid generic `*ViewModel` names when multiple roles exist; prefer `*Session`, `*Browser`, `*Library`, or `*Acquisition` suffixes to encode intent.

## Migration Notes
No external API exposed yet; change is internal to the feature module so refactor risk is low. If later a public contract emerges, expose only necessary read-only interfaces instead of full ViewModel types.

## Status / Ownership
Owned by reader feature maintainers. Architectural review required if adding cross-cutting responsibilities (bookmarks/comments) to keep separation clear.

---
This document should be updated if additional responsibilities shift between the two ViewModels or a merge is reconsidered.
