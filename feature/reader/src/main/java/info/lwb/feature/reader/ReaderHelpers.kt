// Reader helpers and UI composition for the WebView based reader.
package info.lwb.feature.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.hilt.navigation.compose.hiltViewModel
import info.lwb.feature.reader.ui.AppearanceState
import info.lwb.feature.reader.ui.ArticleWebView
import info.lwb.feature.reader.ui.ReaderAppearanceSheet
import info.lwb.feature.reader.ui.loadAssetText
import info.lwb.feature.reader.ui.readerPalette
import info.lwb.feature.reader.ui.themeCssAssetPath
import info.lwb.ui.designsystem.ActionRail
import info.lwb.ui.designsystem.ActionRailItem
import kotlinx.coroutines.Job
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SCROLL_SAVE_DELAY_MS = 400L
private const val FAB_HIDE_DELAY_MS = 5_000L
private const val FAB_MARGIN_DP = 16f
private const val TAG_READER_WEB = "ReaderWeb"
private const val KIND_PARAGRAPH = "paragraph"
private const val JS_CLEAR_HIGHLIGHT = "window.lwbClearParagraphHighlight()"

// Holds scroll persistence callbacks.

private data class ScrollState(val initialY: Int, val onScroll: (Int) -> Unit)

@Composable
private fun rememberScrollState(articleId: String, vm: ScrollViewModel): ScrollState {
    val saved by vm
        .observe(articleId)
        .collectAsState(initial = 0)

    val appliedId = remember { mutableStateOf("") }

    val initialYState = remember { mutableStateOf(0) }

    LaunchedEffect(articleId, saved) {
        if (articleId.isNotBlank() && appliedId.value != articleId) {
            initialYState.value = saved
            appliedId.value = articleId
        }
    }

    val scope = rememberCoroutineScope()
    val lastY = remember { mutableStateOf(0) }
    val job = remember { mutableStateOf<Job?>(null) }

    fun schedule(y: Int) {
        if (articleId.isBlank()) {
            return
        }
        lastY.value = y
        job.value?.cancel()
        job.value = scope.launch {
            delay(SCROLL_SAVE_DELAY_MS)
            try {
                vm.save(articleId, lastY.value)
            } catch (_: Throwable) {
                // ignore
            }
        }
    }
    return ScrollState(
        initialY = initialYState.value,
        onScroll = { schedule(it) },
    )
}

// region CSS + runtime appearance
@Composable
private fun rememberCss(bg: ReaderSettingsRepository.ReaderBackground): String {
    val ctx = LocalContext.current
    val palette = readerPalette(bg)
    return try {
        loadAssetText(ctx, themeCssAssetPath(palette))
    } catch (_: Throwable) {
        ""
    }
}

@Composable
private fun ApplyRuntimeAppearance(
    ready: Boolean,
    ui: ReaderUiState,
    webRef: androidx.compose.runtime.MutableState<android.webkit.WebView?>,
) {
    LaunchedEffect(ready, ui.fontScale, ui.lineHeight, ui.background) {
        if (ready) {
            val palette = readerPalette(ui.background)
            val js = "lwbApplyReaderVars(${ui.fontScale}, ${ui.lineHeight}, '${palette.background}')"
            webRef.value?.evaluateJavascript(js, null)
        }
    }
}
// endregion

// region FAB / Rail state
private data class FabState(val visible: Boolean, val showAppearance: Boolean, val confirmExit: Boolean)

@Composable
private fun rememberFabController(): Triple<FabState, () -> Unit, (FabState) -> Unit> {
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(true) }
    var showAppearance by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    fun showTemp() {
        visible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(FAB_HIDE_DELAY_MS)
            visible = false
        }
    }

    DisposableEffect(Unit) { onDispose { hideJob?.cancel() } }

    val state = FabState(visible, showAppearance, confirmExit)
    val set: (FabState) -> Unit = { s ->
        visible = s.visible
        showAppearance = s.showAppearance
        confirmExit = s.confirmExit
    }
    return Triple(state, ::showTemp, set)
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ReaderActionRail(
    show: Boolean,
    onAppearance: () -> Unit,
    onListen: () -> Unit,
    userLabel: String?,
    onSignOut: (() -> Unit)?,
) {
    if (!show) {
        return
    }
    val fabMargin = Dp(FAB_MARGIN_DP)
    // Keep any scrim / backdrop fullscreen; only inset the actual rail cluster.
    Box(Modifier.fillMaxSize()) {
        ActionRail(
            modifier = Modifier.align(Alignment.BottomEnd),
            items = buildList {
                if (!userLabel.isNullOrBlank()) {
                    add(
                        ActionRailItem(
                            icon = Icons.Filled.Settings,
                            label = userLabel,
                            onClick = { /* no-op */ },
                            role = info.lwb.ui.designsystem.ActionRole.Header,
                        ),
                    )
                    add(
                        ActionRailItem(
                            icon = Icons.Filled.Edit,
                            label = "Sign out",
                            onClick = { onSignOut?.invoke() },
                            role = info.lwb.ui.designsystem.ActionRole.Footer,
                        ),
                    )
                }
                add(
                    ActionRailItem(
                        icon = Icons.Filled.Settings,
                        label = "Appearance",
                        onClick = { onAppearance() },
                    ),
                )
                add(
                    ActionRailItem(
                        icon = Icons.Filled.PlayArrow,
                        label = "Listen",
                        onClick = { onListen() },
                    ),
                )
            },
            mainIcon = Icons.Filled.Settings,
            mainContentDescription = "Reader actions",
            edgePadding = fabMargin,
        )
    }
}
// endregion

// region Appearance sheet + dialog
@Composable
private fun AppearanceSheetOverlay(
    visible: Boolean,
    ui: ReaderUiState,
    vm: ReaderSessionViewModel,
    onDismiss: () -> Unit,
) {
    if (!visible) {
        return
    }
    val state = AppearanceState(
        fontScale = ui.fontScale,
        lineHeight = ui.lineHeight,
        background = ui.background,
        onFontScale = vm::onFontScaleChange,
        onLineHeight = vm::onLineHeightChange,
        onBackground = vm::onBackgroundChange,
    )
    ReaderAppearanceSheet(visible = true, state = state, onDismiss = { onDismiss() })
}

@Composable
private fun ConfirmExitDialog(show: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (!show) {
        return
    }
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(text = "Leave reader?") },
        text = { Text(text = "Exit the reader screen?") },
        confirmButton = { TextButton(onClick = { onConfirm() }) { Text("Exit") } },
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text("Cancel") } },
    )
}
// endregion

@Composable
@Suppress("LongMethod", "ComplexMethod", "CognitiveComplexMethod")
// Entry point composable
internal fun ReaderIndexScreen(
    url: String,
    vm: ReaderSessionViewModel,
    onNavigateBack: (() -> Unit)? = null,
    @Suppress("UnusedParameter") onRequireLogin: (() -> Unit)? = null,
    signedIn: Boolean = false,
    userLabel: String? = null,
    onSignOut: (() -> Unit)? = null,
) {
    // Back navigation consumed via BackHandler when confirm-exit path reached.

    val ui by vm.uiState.collectAsState()
    val scrollVm: ScrollViewModel = hiltViewModel()
    val scroll = rememberScrollState(ui.articleId, scrollVm)
    val webRef = remember { mutableStateOf<android.webkit.WebView?>(null) }
    val ready = remember { mutableStateOf(false) }
    val css = rememberCss(ui.background)
    val (fab, showFabTemp, setFab) = rememberFabController()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var mediaDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val haptics = LocalHapticFeedback.current

    ReaderBackHandler(
        fab = fab,
        setFab = setFab,
        onNavigateBack = onNavigateBack,
        backDispatcher = backDispatcher,
    )

    ApplyRuntimeAppearance(ready = ready.value, ui = ui, webRef = webRef)

    Scaffold { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            // WebView + content
            ReaderArticleWeb(
                url = url,
                css = css,
                ui = ui,
                scrollInitial = scroll.initialY,
                onTap = showFabTemp,
                onScroll = scroll.onScroll,
                ready = ready,
                webRef = webRef,
                onParagraph = { id, text ->
                    // Treat paragraph long-press the same as the "question" button
                    // 1) Haptic feedback and highlight the paragraph in WebView
                    // 2) Trigger the same login flow by reusing the media question dialog pipeline
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                        info.lwb.core.common.log.Logger.d(TAG_READER_WEB) {
                            "ParaQuestion: launching id=" + id + " len=" + text.length
                        }
                    } catch (_: Throwable) {
                        // ignore
                    }
                    try {
                        webRef.value?.evaluateJavascript(
                            "(function(){return window.lwbHighlightParagraph('" + id + "');})()",
                        ) { res ->
                            try {
                                info.lwb.core.common.log.Logger.d(TAG_READER_WEB) {
                                    "ParaQuestion: highlight result id=" + id + " res=" + res
                                }
                            } catch (_: Throwable) {
                                // ignore
                            }
                        }
                    } catch (_: Throwable) {
                        // ignore highlight errors
                    }
                    // Use the same dialog/login flow as media question
                    mediaDialog = KIND_PARAGRAPH to id
                },
                onMediaQuestion = { kind, src ->
                    mediaDialog = kind to src
                },
            )
            // Overlays (dialogs, rails, etc.) rendered above WebView
            ReaderOverlays(
                fab = fab,
                setFab = setFab,
                ui = ui,
                vm = vm,
                showFabTemp = showFabTemp,
                onNavigateBack = onNavigateBack,
                backDispatcher = backDispatcher,
                mediaDialog = mediaDialog,
                clearMediaDialog = {
                    // If we opened the media dialog from a paragraph, also clear highlight on dismiss
                    if (mediaDialog?.first == KIND_PARAGRAPH) {
                        try {
                            webRef.value?.evaluateJavascript(JS_CLEAR_HIGHLIGHT, null)
                        } catch (_: Throwable) {
                            // ignore
                        }
                    }
                    mediaDialog = null
                },
                onRequireLogin = onRequireLogin,
                signedIn = signedIn,
                userLabel = userLabel,
                onSignOut = onSignOut,
            )
        }
    }
}

// EOF

@Composable
private fun ReaderArticleWeb(
    url: String,
    css: String,
    ui: ReaderUiState,
    scrollInitial: Int,
    onTap: () -> Unit,
    onScroll: (Int) -> Unit,
    ready: androidx.compose.runtime.MutableState<Boolean>,
    webRef: androidx.compose.runtime.MutableState<android.webkit.WebView?>,
    onParagraph: (String, String) -> Unit,
    onMediaQuestion: (String, String) -> Unit,
) {
    ArticleWebView(
        url = url,
        injectedCss = css,
        fontScale = ui.fontScale.toFloat(),
        lineHeight = ui.lineHeight.toFloat(),
        backgroundColor = readerPalette(ui.background).background,
        initialScrollY = scrollInitial,
        onTap = { onTap() },
        onScrollChanged = { onScroll(it) },
        onReady = { ready.value = true },
        onWebViewCreated = { w -> webRef.value = w },
        onParagraphLongPress = { id, text -> onParagraph(id, text) },
        onMediaQuestion = { kind, src -> onMediaQuestion(kind, src) },
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ReaderOverlays(
    fab: FabState,
    setFab: (FabState) -> Unit,
    ui: ReaderUiState,
    vm: ReaderSessionViewModel,
    showFabTemp: () -> Unit,
    onNavigateBack: (() -> Unit)?,
    backDispatcher: androidx.activity.OnBackPressedDispatcher?,
    mediaDialog: Pair<String, String>?,
    clearMediaDialog: () -> Unit,
    onRequireLogin: (() -> Unit)?,
    signedIn: Boolean,
    userLabel: String?,
    onSignOut: (() -> Unit)?,
) {
    ReaderActionRail(
        show = fab.visible,
        onAppearance = { setFab(fab.copy(showAppearance = true)) },
        onListen = { showFabTemp() },
        userLabel = userLabel,
        onSignOut = onSignOut,
    )
    AppearanceSheetOverlay(
        visible = fab.showAppearance,
        ui = ui,
        vm = vm,
        onDismiss = { setFab(fab.copy(showAppearance = false)) },
    )
    ConfirmExitDialog(
        show = fab.confirmExit,
        onDismiss = { setFab(fab.copy(confirmExit = false)) },
        onConfirm = {
            setFab(fab.copy(confirmExit = false))
            onNavigateBack?.invoke() ?: backDispatcher?.onBackPressed()
        },
    )
    MediaLoginOverlay(mediaDialog, clearMediaDialog, onRequireLogin, signedIn)
}

@Composable
private fun LoginRequiredDialog(
    data: Pair<String, String>?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (data == null) {
        return
    }
    val (kind, _) = data
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Sign in required") },
        text = { Text("To start a private discussion about this $kind, please sign in or register.") },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text("Cancel") }
        },
    )
}

@Composable
private fun MediaLoginOverlay(
    mediaDialog: Pair<String, String>?,
    clearMediaDialog: () -> Unit,
    onRequireLogin: (() -> Unit)?,
    signedIn: Boolean,
) {
    // If already signed in, bypass confirmation entirely.
    if (signedIn) {
        if (mediaDialog != null) {
            // Close the media dialog immediately; nothing else to do now.
            clearMediaDialog()
        }
        return
    }
    LoginRequiredDialog(
        data = mediaDialog,
        onDismiss = { clearMediaDialog() },
        onConfirm = {
            try {
                onRequireLogin?.invoke()
            } catch (_: Throwable) {
                // ignore
            }
        },
    )
}

@Composable
private fun ReaderBackHandler(
    fab: FabState,
    setFab: (FabState) -> Unit,
    onNavigateBack: (() -> Unit)?,
    backDispatcher: androidx.activity.OnBackPressedDispatcher?,
) {
    BackHandler(enabled = true) {
        if (fab.confirmExit) {
            setFab(fab.copy(confirmExit = false))
            onNavigateBack?.invoke() ?: backDispatcher?.onBackPressed()
        } else if (fab.showAppearance) {
            setFab(fab.copy(showAppearance = false))
        } else {
            setFab(fab.copy(confirmExit = true))
        }
    }
}
