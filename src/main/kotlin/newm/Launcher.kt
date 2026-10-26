package newm

import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.core.appearance.ColorScheme
import dev.caulis.log.Logger
import newm.bar.BarColors
import newm.bar.softShadow
import newm.bar.IconTheme
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("launcher")

/** How often each program was started from the launcher (~/.local/state/newm/launcher.json): the frequent ones come first. */
internal object LaunchCounts {
    private val file: Path by lazy {
        val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.startsWith("/") } ?: "${System.getenv("HOME") ?: System.getProperty("user.home")}/.local/state"
        Path.of(state, "newm", "launcher.json")
    }
    private val counts: MutableMap<String, Int> by lazy {
        try {
            Json.parseObject(Files.readString(file)).mapValuesTo(HashMap()) { (it.value as? Number)?.toInt() ?: 0 }
        } catch (_: Exception) {
            HashMap()
        }
    }

    fun of(app: DesktopApp): Int = counts[app.id.ifEmpty { app.name }] ?: 0

    fun started(app: DesktopApp) {
        val key = app.id.ifEmpty { app.name }
        counts[key] = (counts[key] ?: 0) + 1
        try {
            privateFolder(file.parent)
            Files.writeString(file, Json.write(counts))
        } catch (e: Exception) {
            log.warn("The launcher's counts cannot be kept: $e")
        }
    }
}

/**
 * newm's launcher (Logo+D, five fingers): a search in the upper middle of the screen. Type a
 * few letters, Enter starts the first; the arrows or the pointer choose another; Escape or
 * Logo+D again closes it. With nothing typed: the programs you start most, then all.
 */
@Composable
internal fun LauncherPanel(wm: Layout, apps: List<DesktopApp>, opened: Int, closing: Boolean = false) {
    val colors = wm.barModel.colorsNow()
    var query by remember(opened) { mutableStateOf("") }
    var selected by remember(opened) { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val icons = remember { mutableStateMapOf<String, ImageBitmap?>() }
    // (It comes and goes as newm's launcher animation runs: the compositor shows it see-through
    // and smaller then, nothing here is drawn anew for it.)
    LaunchedEffect(opened) { runCatching { focus.requestFocus() } }

    // The wheel and the touchpad: what they scroll is added up and the list glides after it, a
    // frame at a time while there is something left (Compose scrolled by whole steps: jerky).
    var scrollWanted by remember(opened) { mutableStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var gliding by remember(opened) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun glide(by: Float) {
        scrollWanted += by
        if (gliding?.isActive == true) return
        gliding = scope.launch {
            while (Math.abs(scrollWanted) > 0.5f) {
                androidx.compose.runtime.withFrameNanos { }
                val step = scrollWanted * 0.3f
                list.scrollBy(step)
                scrollWanted -= step
            }
            scrollWanted = 0f
        }
    }

    // Nothing typed: the frequent ones first (then by name); else the matches, the frequent first among equals.
    val shown = remember(apps, query) {
        if (query.isBlank()) apps.sortedWith(compareByDescending<DesktopApp> { LaunchCounts.of(it) }.thenBy { it.name.lowercase() })
        else DesktopApp.matching(apps, query)
    }
    val frequent = if (query.isBlank()) shown.takeWhile { LaunchCounts.of(it) > 0 }.take(6).size else 0
    // (Kept in the list, not read here: reading it while composing composed the whole list anew at each row the pointer passed.)
    LaunchedEffect(shown) { if (selected >= shown.size) selected = (shown.size - 1).coerceAtLeast(0) }
    // Chosen with the keys: scrolled into sight if it is not (the pointer only chooses what it is on:
    // scrolling then moved the list under a still pointer, which chose the next one, on and on).
    var byKeys by remember(opened) { mutableIntStateOf(0) }
    LaunchedEffect(byKeys) {
        if (byKeys == 0 || shown.isEmpty()) return@LaunchedEffect
        val visible = list.layoutInfo.visibleItemsInfo.filter { it.offset >= 0 && it.offset + it.size <= list.layoutInfo.viewportEndOffset }.map { it.index }
        if (selected !in visible) {
            // Below: it becomes the last one in sight; above: the first.
            val target = if (selected > (visible.maxOrNull() ?: 0)) selected - visible.size + 1 else selected
            list.animateScrollToItem(target.coerceAtLeast(0))
        }
    }

    fun start(app: DesktopApp) {
        LaunchCounts.started(app)
        log.info { "Starting ${app.name}" }
        query = ""
        wm.launchApp(app.command)
    }

    val text = TextStyle(color = colors.text, fontSize = 14.sp)
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.fillMaxSize().padding(16.dp)
            .softShadow(20.dp, 14.dp)
            .background(colors.island.copy(alpha = 0.97f), shape)
            .border(1.dp, colors.edge, shape),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            // The search.
            Row(Modifier.fillMaxWidth().height(50.dp).background(colors.text.copy(alpha = 0.06f), RoundedCornerShape(13.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(18.dp)) {
                    val r = size.minDimension * 0.33f
                    drawCircle(colors.dim, r, Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(2.dp.toPx()))
                    drawLine(colors.dim, Offset(size.width * 0.66f, size.height * 0.66f), Offset(size.width * 0.92f, size.height * 0.92f), 2.dp.toPx(), StrokeCap.Round)
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) BasicText(newm.bar.tr("Search programs"), style = text.copy(color = colors.dim, fontSize = 18.sp))
                    BasicTextField(
                        query, { query = it; selected = 0; byKeys++ },
                        Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown, Key.Tab -> { selected = (selected + 1).coerceAtMost(shown.size - 1); byKeys++; true }
                                Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); byKeys++; true }
                                Key.Enter, Key.NumPadEnter -> { shown.getOrNull(selected)?.let(::start); true }
                                Key.Escape -> { wm.exitOverlay(); true }
                                else -> false
                            }
                        },
                        singleLine = true,
                        textStyle = text.copy(fontSize = 18.sp),
                        cursorBrush = SolidColor(colors.accent),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (shown.isEmpty()) {
                BasicText(if (apps.isEmpty()) newm.bar.tr("Looking for programs…") else newm.bar.tr("Nothing called “%s”").replace("%s", query), style = text.copy(color = colors.dim), modifier = Modifier.padding(14.dp))
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(opened) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (e.type == PointerEventType.Scroll) {
                            val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            glide(dy * 64.dp.toPx())
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
            }) {
                itemsIndexed(shown, key = { _, app -> app.id.ifEmpty { app.name } }) { i, app ->
                    if (query.isBlank() && i == 0 && frequent > 0) Label(newm.bar.tr("Frequent"), colors)
                    if (query.isBlank() && i == frequent) Label(newm.bar.tr("All programs"), colors)
                    AppRow(app, { selected == i }, colors, text, icons, wm, intro = -1, onHover = { selected = i }, onClick = { start(app) })
                }
            }
        }
    }
}

@Composable
private fun Label(title: String, colors: BarColors) {
    BasicText(title.uppercase(), style = TextStyle(color = colors.dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
        modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp))
}

@Composable
private fun AppRow(app: DesktopApp, selected: () -> Boolean, colors: BarColors, text: TextStyle, icons: MutableMap<String, ImageBitmap?>, wm: Layout, intro: Int, onHover: () -> Unit, onClick: () -> Unit) {
    // The first rows come in one after another ([intro]: its place; -1: shown at once).
    val come = remember { Animatable(if (intro >= 0) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (intro >= 0) {
            kotlinx.coroutines.delay(40L + intro * 22L)
            come.animateTo(1f, tween(220))
        }
    }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    // The chosen one lit: read only while drawing, so choosing another redraws two rows, not
    // the whole list composed anew (that cost a heavy frame at each row the pointer passed).
    val lit = colors.accent.copy(alpha = 0.18f)
    val key = app.icon.ifEmpty { app.id }
    val iconPixels = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.roundToPx() }
    LaunchedEffect(key) {
        if (key.isNotEmpty() && key !in icons) {
            icons[key] = null
            // At the size it is drawn in pixels: drawn as it is, not shrunk anew each frame.
            IconTheme.load(key, iconPixels, null, post = { wm.caulis.post { it() } }) { icons[key] = it }
        }
    }
    Row(
        Modifier.fillMaxWidth().height(58.dp)
            // A layer of its own only while it comes in (each one costs a picture drawn apart).
            .then(if (come.value < 1f) Modifier.graphicsLayer {
                alpha = come.value
                translationY = (1 - come.value) * 10f * density
            } else Modifier)
            .drawBehind { if (selected()) drawRoundRect(lit, cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())) }.hoverable(source)
            .pointerInput(onClick) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onClick() } }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val picture = icons[key]
        if (picture != null) {
            Image(picture, null, Modifier.size(40.dp), filterQuality = FilterQuality.Low)
        } else {
            Box(Modifier.size(40.dp).background(colors.accent.copy(alpha = 0.25f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                BasicText(app.name.take(1).uppercase(), style = text.copy(fontWeight = FontWeight.Bold))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(app.name, style = text.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (app.comment.isNotBlank()) BasicText(app.comment, style = text.copy(color = colors.dim, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BasicText("↵", style = text.copy(color = colors.dim, fontSize = 14.sp), modifier = Modifier.graphicsLayer { alpha = if (selected()) 1f else 0f })
    }
}
