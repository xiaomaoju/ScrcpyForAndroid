package io.github.miuzarte.scrcpyforandroid.pages

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.compose.LocalActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.scrcpy.*
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverContentHeight
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverDisplay
import io.github.miuzarte.scrcpyforandroid.widgets.ScrcpyVideoSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val LocalCoverPanel = staticCompositionLocalOf { false }
internal val LocalCoverPagePicker = staticCompositionLocalOf<() -> Unit> { {} }
private enum class CoverDrawer { Closed, Tools, Trackpad, Software }

/** The software slot is kept composed while the trackpad covers it. It owns no video preview. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun CoverControlScreen(
    scrcpy: Scrcpy,
    session: Scrcpy.Session.SessionInfo?,
    pageNames: List<String>,
    onSelectPage: (Int) -> Unit,
    onLeave: () -> Unit,
    exitLabel: String = stringResource(R.string.cover_exit),
    interactionEnabled: Boolean = true,
    software: @Composable () -> Unit,
) {
    var mouse by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var pagePicker by rememberSaveable { mutableStateOf(false) }
    var forceTwoPane by rememberSaveable { mutableStateOf(false) }
    var drawer by rememberSaveable { mutableStateOf(CoverDrawer.Closed) }
    var imeRequest by remember { mutableIntStateOf(0) }
    var lastWidth by rememberSaveable { mutableIntStateOf(1080) }
    var lastHeight by rememberSaveable { mutableIntStateOf(1920) }
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val textStyles = MiuixTheme.textStyles
    val panelTextStyles = textStyles.copy(
        main = textStyles.main.copy(fontSize = 13.sp),
        paragraph = textStyles.paragraph.copy(fontSize = 13.sp),
        body1 = textStyles.body1.copy(fontSize = 12.sp),
        body2 = textStyles.body2.copy(fontSize = 11.sp),
        button = textStyles.button.copy(fontSize = 13.sp),
        headline1 = textStyles.headline1.copy(fontSize = 13.sp),
        headline2 = textStyles.headline2.copy(fontSize = 12.sp),
        subtitle = textStyles.subtitle.copy(fontSize = 12.sp),
        title4 = textStyles.title4.copy(fontSize = 14.sp),
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        val previousInsets = window?.decorView?.let(ViewCompat::getRootWindowInsets)
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (previousBehavior != null) controller?.systemBarsBehavior = previousBehavior
            listOf(WindowInsetsCompat.Type.statusBars(), WindowInsetsCompat.Type.navigationBars()).forEach { type ->
                if (previousInsets?.isVisible(type) != false) controller?.show(type) else controller?.hide(type)
            }
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    val inputEnabled = interactionEnabled && session?.controlEnabled == true && session.width > 0 && session.height > 0
    val showSoftware = !mouse || details || session == null
    var cursor by remember(session?.controlSessionId) { mutableStateOf(CoverPoint(.5f, .5f)) }
    val dispatcher = remember(scrcpy, session?.controlSessionId) {
        val id = session?.controlSessionId
        CoverInputDispatcher { event -> if (id != null) scrcpy.injectCoverInput(id, event) }
    }
    val input = remember(dispatcher) {
        CoverInputController(dispatcher::submit, { cursor = it }, session?.mouseHover ?: true)
    }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var videoSize by remember { mutableStateOf(IntSize.Zero) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    val videoWidth = session?.width?.takeIf { it > 0 } ?: lastWidth
    val videoHeight = session?.height?.takeIf { it > 0 } ?: lastHeight
    LaunchedEffect(session?.width, session?.height) {
        if (session != null && session.width > 0 && session.height > 0) {
            lastWidth = session.width
            lastHeight = session.height
        }
    }
    DisposableEffect(input, mouse, inputEnabled, videoWidth, videoHeight, imeVisible) {
        onDispose { longPressJob?.cancel(); input.cancel() }
    }
    DisposableEffect(dispatcher, input) {
        onDispose { input.cancel(); dispatcher.close() }
    }
    DisposableEffect(lifecycleOwner, input) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { longPressJob?.cancel(); input.cancel() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(inputEnabled) { if (!inputEnabled) keyboard?.hide() }

    fun clearGesture() { longPressJob?.cancel(); input.cancel() }

    fun openDrawer(next: CoverDrawer) {
        clearGesture(); keyboard?.hide(); pagePicker = false; drawer = next
    }

    fun directEvent(event: MotionEvent): Boolean {
        if (!inputEnabled || mouse || videoSize.width <= 0 || videoSize.height <= 0) return true
        val bounds = fitVideoContent(videoWidth, videoHeight, videoSize.width, videoSize.height)
        fun point(index: Int) = CoverPoint((event.getX(index) - bounds.left) / bounds.width, (event.getY(index) - bounds.top) / bounds.height)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> input.touchDown(event.getPointerId(event.actionIndex), point(event.actionIndex))
            MotionEvent.ACTION_MOVE -> input.touchMove((0 until event.pointerCount).associate { event.getPointerId(it) to point(it) })
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> input.touchUp(event.getPointerId(event.actionIndex), point(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> input.cancel()
        }
        return true
    }

    fun trackpadEvent(event: MotionEvent): Boolean {
        if (!inputEnabled || padSize.width <= 0 || padSize.height <= 0) return true
        fun center(exclude: Int = -1): CoverPoint? {
            val indexes = (0 until event.pointerCount).filter { it != exclude }
            if (indexes.isEmpty()) return null
            return CoverPoint(indexes.sumOf { event.getX(it).toDouble() }.toFloat() / indexes.size / padSize.width,
                indexes.sumOf { event.getY(it).toDouble() }.toFloat() / indexes.size / padSize.height)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                input.padDown(center()!!)
                longPressJob?.cancel()
                longPressJob = scope.launch { delay(ViewConfiguration.getLongPressTimeout().toLong()); input.padLongPress() }
            }
            MotionEvent.ACTION_POINTER_DOWN -> { longPressJob?.cancel(); input.padPointersChanged(center()) }
            MotionEvent.ACTION_POINTER_UP -> { longPressJob?.cancel(); input.padPointersChanged(center(event.actionIndex)) }
            MotionEvent.ACTION_MOVE -> input.padMove(center()!!, event.pointerCount,
                ViewConfiguration.get(context).scaledTouchSlop.toFloat() / minOf(padSize.width, padSize.height))
            MotionEvent.ACTION_UP -> { longPressJob?.cancel(); input.padUp() }
            MotionEvent.ACTION_CANCEL -> { longPressJob?.cancel(); input.cancel() }
            MotionEvent.ACTION_SCROLL -> input.scroll(event.getAxisValue(MotionEvent.AXIS_HSCROLL), event.getAxisValue(MotionEvent.AXIS_VSCROLL))
        }
        return true
    }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(Color.Black).testTag("cover-control")) {
        // IME changes the fit, but must not swap the user's controls while typing.
        var heightWithoutIme by remember { mutableStateOf(maxHeight) }
        if (!imeVisible) SideEffect { heightWithoutIme = maxHeight }
        var previousRail by remember { mutableStateOf<Boolean?>(null) }
        val automaticRail = coverNeedsControlRail(videoWidth, videoHeight, maxWidth.value,
            (if (imeVisible) heightWithoutIme else maxHeight).value, previousRail)
        SideEffect { previousRail = automaticRail }
        val rail = LocalCoverDisplay.current && automaticRail && !forceTwoPane
        val railWidth = minOf(40.dp, maxWidth)
        val leftWidth = if (rail) (maxWidth - railWidth - 4.dp).coerceAtLeast(0.dp)
            else coverVideoPaneWidth(videoWidth, videoHeight, maxWidth.value, maxHeight.value, 120f, 4f).dp
        val controlHeight = minOf(40.dp, ((maxHeight - 8.dp) / 3).coerceAtLeast(0.dp))
        val panelVisible = !rail || drawer != CoverDrawer.Closed
        val softwareVisible = if (rail) drawer == CoverDrawer.Software else showSoftware
        val trackpadVisible = if (rail) drawer == CoverDrawer.Trackpad else !showSoftware
        val panelWidth = if (rail) minOf(180.dp, leftWidth) else (maxWidth - leftWidth - 4.dp).coerceAtLeast(0.dp)
        DisposableEffect(rail, panelVisible, trackpadVisible, input) {
            onDispose { clearGesture() }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(leftWidth).fillMaxHeight().clipToBounds().testTag("cover-video")
                .onSizeChanged { if (videoSize != it) { input.cancel(); videoSize = it } }) {
                val bounds = fitVideoContent(videoWidth, videoHeight, videoSize.width, videoSize.height)
                ScrcpyVideoSurface(
                    modifier = Modifier.align(Alignment.Center).size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() }).testTag("cover-image"),
                    session = session,
                    imeRequestToken = imeRequest,
                    onImeCommitText = { text -> if (inputEnabled) submitImeText(scrcpy, text, session!!.keyInjectMode) { error, _ -> AppRuntime.snackbar(error.message.orEmpty()) } },
                    onImeDeleteSurroundingText = { before, after -> if (inputEnabled) submitImeDeleteSurroundingText(scrcpy, before, after) },
                    onImeKeyEvent = { event -> if (inputEnabled) submitImeKeyEvent(scrcpy, event, session!!.keyInjectMode, session.forwardKeyRepeat) else false },
                )
                Box(Modifier.matchParentSize().pointerInteropFilter { directEvent(it) }.testTag("cover-direct-touch"))
                if (mouse && inputEnabled) Canvas(Modifier.matchParentSize()) {
                    val x = bounds.left + cursor.x * bounds.width
                    val y = bounds.top + cursor.y * bounds.height
                    val unit = 12.dp.toPx()
                    val path = Path().apply { moveTo(x, y); lineTo(x + unit, y + unit * .7f); lineTo(x + unit * .43f, y + unit * .86f); lineTo(x + unit * .18f, y + unit * 1.4f); close() }
                    drawPath(path, Color.Black, style = Stroke(3.dp.toPx()))
                    drawPath(path, Color.White)
                }
                if (session == null) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .8f)), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.cover_stopped), color = Color.White, fontSize = 12.sp)
                }
            }
            if (rail) Column(Modifier.width(railWidth).fillMaxHeight().background(Color(0xff171e1b)).testTag("cover-control-rail")) {
                CoverRailAction(Icons.AutoMirrored.Rounded.ExitToApp, exitLabel) { clearGesture(); keyboard?.hide(); onLeave() }
                CoverRailAction(if (mouse) Icons.Rounded.Mouse else Icons.Rounded.Tune, stringResource(R.string.cover_tools), selected = panelVisible || mouse) {
                    openDrawer(if (panelVisible) CoverDrawer.Closed else CoverDrawer.Tools)
                }
                CoverRailAction(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cover_remote_back), inputEnabled) { input.key(KeyEvent.KEYCODE_BACK) }
                CoverRailAction(Icons.Rounded.Home, stringResource(R.string.cover_remote_home), inputEnabled) { input.key(KeyEvent.KEYCODE_HOME) }
                CoverRailAction(Icons.Rounded.RecentActors, stringResource(R.string.cover_remote_recent), inputEnabled) { input.key(KeyEvent.KEYCODE_APP_SWITCH) }
            } else Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().height(controlHeight).background(Color(0xff171e1b))) {
                    CoverAction(if (mouse) Icons.Rounded.Mouse else Icons.Rounded.TouchApp,
                        stringResource(if (mouse) R.string.cover_switch_touch else R.string.cover_switch_mouse), selected = true) {
                        longPressJob?.cancel(); input.cancel(); keyboard?.hide(); mouse = !mouse; pagePicker = false
                    }
                    CoverAction(Icons.Rounded.Tune, stringResource(R.string.cover_details), selected = showSoftware) {
                        clearGesture(); keyboard?.hide(); if (forceTwoPane || !mouse || session == null) pagePicker = !pagePicker else details = !details
                    }
                    CoverAction(Icons.AutoMirrored.Rounded.ExitToApp, exitLabel) {
                        longPressJob?.cancel(); input.cancel(); keyboard?.hide(); onLeave()
                    }
                }
                Spacer(Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth().height(controlHeight).background(Color(0xff171e1b))) {
                    CoverAction(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cover_remote_back), inputEnabled) { input.key(KeyEvent.KEYCODE_BACK) }
                    CoverAction(Icons.Rounded.Home, stringResource(R.string.cover_remote_home), inputEnabled) { input.key(KeyEvent.KEYCODE_HOME) }
                    CoverAction(Icons.Rounded.RecentActors, stringResource(R.string.cover_remote_recent), inputEnabled) { input.key(KeyEvent.KEYCODE_APP_SWITCH) }
                }
            }
        }
        if (rail && panelVisible) Box(Modifier.width(leftWidth).fillMaxHeight()
            .background(if (drawer == CoverDrawer.Trackpad) Color.Transparent else Color.Black.copy(alpha = .25f))
            .clickable { openDrawer(CoverDrawer.Closed) }.testTag("cover-drawer-scrim"))
        // One software tree stays mounted in one slot across both layouts and drawer changes.
        Column(Modifier.offset(x = if (!panelVisible) maxWidth + 4.dp else if (rail) leftWidth - panelWidth else leftWidth + 4.dp,
            y = if (rail) 0.dp else controlHeight + 4.dp)
            .width(panelWidth).height(if (rail) maxHeight else (maxHeight - (controlHeight + 4.dp) * 2).coerceAtLeast(0.dp))
            .background(if (rail && drawer == CoverDrawer.Trackpad) Color.Transparent else Color(0xff151e19))
            .then(if (!panelVisible) Modifier.clearAndSetSemantics {} else Modifier)
            .testTag("cover-panel")) {
            if (rail) Row(Modifier.fillMaxWidth().height(32.dp).background(Color(0xff151e19)), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(when (drawer) {
                    CoverDrawer.Trackpad -> R.string.cover_mouse_pad
                    CoverDrawer.Software -> R.string.cover_details
                    else -> R.string.cover_tools
                }), color = Color(0xffd2dbce), fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Box(Modifier.size(32.dp).clickable { openDrawer(CoverDrawer.Closed) }
                    .semanticsLabel(stringResource(R.string.cover_close_panel)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = Color(0xffd2dbce))
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("cover-software")) {
                    val showPadHint = maxHeight >= 80.dp
                    CompositionLocalProvider(LocalCoverContentHeight provides maxHeight, LocalCoverPanel provides true,
                        LocalCoverPagePicker provides { pagePicker = true }) {
                        Box(Modifier.fillMaxSize().offset(x = if (softwareVisible) 0.dp else panelWidth + 4.dp)
                            .graphicsLayer { alpha = if (softwareVisible) 1f else 0f }
                            .then(if (!softwareVisible) Modifier.clearAndSetSemantics {} else Modifier)) {
                            MiuixTheme(textStyles = panelTextStyles) { software() }
                        }
                    }
                    if (trackpadVisible) Box(Modifier.fillMaxSize().background(Color(0xff151e19).copy(alpha = if (rail) .38f else 1f))
                        .onSizeChanged { if (padSize != it) { clearGesture(); padSize = it } }
                        .pointerInteropFilter { if (imeVisible) true else trackpadEvent(it) }.testTag("cover-trackpad"), contentAlignment = Alignment.Center) {
                        if (rail) {
                            if (showPadHint) Text(stringResource(if (imeVisible) R.string.cover_keyboard else if (inputEnabled) R.string.cover_trackpad_hint else R.string.cover_control_disabled),
                                color = Color(0xffd2dbce), fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xff151e19).copy(alpha = .85f)).padding(8.dp))
                        } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(if (imeVisible) Icons.Rounded.Keyboard else Icons.Rounded.Mouse, null, Modifier.size(30.dp), tint = Color(0xffc0d9a0))
                            if (showPadHint) Text(stringResource(if (imeVisible) R.string.cover_keyboard else if (inputEnabled) R.string.cover_trackpad_hint else R.string.cover_control_disabled),
                                color = Color(0xffb7c5b1), fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                        }
                    }
                    if (rail && drawer == CoverDrawer.Tools) Column(Modifier.fillMaxSize().background(Color(0xff20271f)).verticalScroll(rememberScrollState())) {
                        Row {
                            CoverTool(stringResource(R.string.cover_touch_mode), selected = !mouse) { mouse = false; openDrawer(CoverDrawer.Closed) }
                            CoverTool(stringResource(R.string.cover_mouse_pad), selected = mouse) { mouse = true; openDrawer(CoverDrawer.Trackpad) }
                        }
                        Row {
                            CoverTool(stringResource(R.string.cover_details)) { openDrawer(CoverDrawer.Software) }
                            CoverTool(stringResource(R.string.cover_keyboard), enabled = inputEnabled) { clearGesture(); drawer = CoverDrawer.Closed; imeRequest++ }
                        }
                        CoverMenuItem(stringResource(R.string.cover_two_pane)) { openDrawer(CoverDrawer.Closed); forceTwoPane = true }
                    }
                    if (pagePicker) Column(Modifier.fillMaxSize().background(Color(0xff20271f)).verticalScroll(rememberScrollState())) {
                        pageNames.forEachIndexed { index, name -> CoverMenuItem(name) { onSelectPage(index); details = true; pagePicker = false } }
                        CoverMenuItem(stringResource(R.string.cover_keyboard), inputEnabled) { clearGesture(); pagePicker = false; if (rail) drawer = CoverDrawer.Closed; imeRequest++ }
                        if (forceTwoPane) CoverMenuItem(stringResource(R.string.cover_auto_layout)) { openDrawer(CoverDrawer.Closed); forceTwoPane = false }
                        CoverMenuItem(stringResource(R.string.button_cancel)) { pagePicker = false }
                    }
                }
        }
    }
}

@Composable
private fun ColumnScope.CoverRailAction(icon: ImageVector, label: String, enabled: Boolean = true, selected: Boolean = false, action: () -> Unit) {
    Box(Modifier.weight(1f).fillMaxWidth().background(if (selected) Color(0xff293c2d) else Color.Transparent)
        .clickable(enabled = enabled, role = Role.Button, onClick = action).semanticsLabel(label), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(21.dp), tint = if (enabled) Color(0xffd2dbce) else Color.Gray)
    }
}

@Composable
private fun RowScope.CoverTool(label: String, enabled: Boolean = true, selected: Boolean = false, action: () -> Unit) {
    Box(Modifier.weight(1f).heightIn(min = 44.dp).background(if (selected) Color(0xff293c2d) else Color.Transparent)
        .clickable(enabled = enabled, role = Role.Button, onClick = action).padding(6.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 12.sp, color = if (enabled) Color(0xffd2dbce) else Color.Gray)
    }
}

@Composable
private fun RowScope.CoverAction(icon: ImageVector, label: String, enabled: Boolean = true, selected: Boolean = false, action: () -> Unit) {
    Box(Modifier.weight(1f).fillMaxHeight().background(if (selected) Color(0xff293c2d) else Color.Transparent)
        .clickable(enabled = enabled, role = Role.Button, onClick = action).semanticsLabel(label), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(21.dp), tint = if (!enabled) Color.Gray else if (selected) Color(0xffd2edaf) else Color(0xffd2dbce))
    }
}

private fun Modifier.semanticsLabel(label: String) = this.semantics { contentDescription = label }

@Composable
private fun CoverMenuItem(text: String, enabled: Boolean = true, action: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(enabled = enabled, role = Role.Button, onClick = action).padding(10.dp)) {
        Text(text, fontSize = 13.sp, color = if (enabled) Color.White else Color.Gray)
    }
}
