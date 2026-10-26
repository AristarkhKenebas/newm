package newm

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
import newm.bar.IconTheme
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
            Files.createDirectories(file.parent)
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
internal fun LauncherPanel(wm: Layout, apps: List<DesktopApp>, opened: Int) {
    val colors = if (wm.caulis.colorSchemeNow == ColorScheme.DARK) BarColors.DARK else BarColors.LIGHT
    var query by remember(opened) { mutableStateOf("") }
    var selected by remember(opened) { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val icons = remember { mutableStateMapOf<String, ImageBitmap?>() }
    val appear = remember(opened) { Animatable(0f) }
    LaunchedEffect(opened) {
        runCatching { focus.requestFocus() }
        appear.animateTo(1f, tween(170))
    }

    // Nothing typed: the frequent ones first (then by name); else the matches, the frequent first among equals.
    val shown = remember(apps, query) {
        if (query.isBlank()) apps.sortedWith(compareByDescending<DesktopApp> { LaunchCounts.of(it) }.thenBy { it.name.lowercase() })
        else DesktopApp.matching(apps, query)
    }
    val frequent = if (query.isBlank()) shown.takeWhile { LaunchCounts.of(it) > 0 }.take(6).size else 0
    if (selected >= shown.size) selected = (shown.size - 1).coerceAtLeast(0)
    LaunchedEffect(selected, shown) { if (shown.isNotEmpty()) list.animateScrollToItem(selected) }

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
            .graphicsLayer { alpha = appear.value; scaleX = 0.96f + 0.04f * appear.value; scaleY = scaleX; translationY = (1 - appear.value) * -10f * density }
            .shadow(18.dp, shape)
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
                    if (query.isEmpty()) BasicText("Search programs", style = text.copy(color = colors.dim, fontSize = 18.sp))
                    BasicTextField(
                        query, { query = it; selected = 0 },
                        Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown, Key.Tab -> { selected = (selected + 1).coerceAtMost(shown.size - 1); true }
                                Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); true }
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
                BasicText(if (apps.isEmpty()) "Looking for programs…" else "Nothing called “$query”", style = text.copy(color = colors.dim), modifier = Modifier.padding(14.dp))
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(shown, key = { _, app -> app.id.ifEmpty { app.name } }) { i, app ->
                    if (query.isBlank() && i == 0 && frequent > 0) Label("Frequent", colors)
                    if (query.isBlank() && i == frequent) Label("All programs", colors)
                    AppRow(app, i == selected, colors, text, icons, wm, onHover = { selected = i }, onClick = { start(app) })
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText("↑ ↓ choose   ·   Enter start   ·   Esc close", style = text.copy(color = colors.dim, fontSize = 11.sp), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun Label(title: String, colors: BarColors) {
    BasicText(title.uppercase(), style = TextStyle(color = colors.dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
        modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp))
}

@Composable
private fun AppRow(app: DesktopApp, selected: Boolean, colors: BarColors, text: TextStyle, icons: MutableMap<String, ImageBitmap?>, wm: Layout, onHover: () -> Unit, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    val bg by animateColorAsState(if (selected) colors.accent.copy(alpha = 0.18f) else Color.Transparent, tween(90))
    val key = app.icon.ifEmpty { app.id }
    LaunchedEffect(key) {
        if (key.isNotEmpty() && key !in icons) {
            icons[key] = null
            IconTheme.load(key, 64, null, post = { wm.caulis.post { it() } }) { icons[key] = it }
        }
    }
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(bg, RoundedCornerShape(12.dp)).hoverable(source)
            .pointerInput(onClick) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onClick() } }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val picture = icons[key]
        if (picture != null) {
            Image(picture, null, Modifier.size(32.dp), filterQuality = FilterQuality.High)
        } else {
            Box(Modifier.size(32.dp).background(colors.accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                BasicText(app.name.take(1).uppercase(), style = text.copy(fontWeight = FontWeight.Bold))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(app.name, style = text.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (app.comment.isNotBlank()) BasicText(app.comment, style = text.copy(color = colors.dim, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) BasicText("↵", style = text.copy(color = colors.dim, fontSize = 14.sp))
    }
}
