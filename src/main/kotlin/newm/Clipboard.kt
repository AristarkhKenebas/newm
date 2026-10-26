package newm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.compose.ComposeWidget
import dev.caulis.core.BytesOnClipboard
import dev.caulis.core.ClipboardData
import dev.caulis.core.TextOnClipboard
import dev.caulis.core.DrawingLevel
import dev.caulis.core.readClipboard
import dev.caulis.geometry.Rect
import dev.caulis.log.Logger
import newm.bar.SmallButton
import newm.bar.softShadow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val log = Logger("clipboard")

private val confEnabled = configuredValue("clipboard.enabled", true)
private val confSize = configuredValue("clipboard.size", 50)

/** One thing copied: text, or a picture (its PNG and a picture of it to show). */
class ClipEntry(val text: String?, val png: ByteArray?, val picture: ImageBitmap?, val width: Int, val height: Int) {
    val time: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    fun same(other: ClipEntry) = text != null && text == other.text || png != null && other.png != null && png.contentEquals(other.png)
}

/**
 * The clipboard's history (Logo+V, as Windows has it): what was copied lately — texts and
 * pictures — kept in memory only (never on disk), the newest first. Choosing one copies it
 * again; Delete takes it away. Things password managers mark as secret are not kept.
 * clipboard { enabled = false } turns it off; clipboard { size = 100 } keeps more.
 */
class ClipboardHistory(private val wm: Layout) {
    val entries = mutableStateListOf<ClipEntry>()
    var shown by mutableStateOf(false)
        private set
    private var opened by mutableIntStateOf(0)
    private var widget: ComposeWidget? = null

    // What newm itself put back on the clipboard (not kept again as new).
    private var ours: ClipboardData? = null

    fun start() {
        val caulis = wm.caulis
        caulis.clipboard.onClipboardChanged { data -> if (data != null && data !== ours && confEnabled()) take(data) }
        val w = ComposeWidget(caulis.desktop, { task -> caulis.post { task.run() } }, place = { m ->
            if (!shown) null else {
                val d = caulis.desktop
                val target = d.monitors.firstOrNull { it.area.contains(Rect(d.pointerX.toInt(), d.pointerY.toInt(), 1, 1)) } ?: d.monitors.firstOrNull()
                if (m !== target) null else {
                    val width = minOf(980, m.area.width - 80)
                    val height = minOf(620, m.area.height - 120)
                    Rect(m.area.x + (m.area.width - width) / 2, m.area.y + (m.area.height * 0.13).toInt(), width, height)
                }
            }
        }, level = DrawingLevel.ON_TOP, takesKeyboard = true) { ClipboardPanel(this) }
        widget = w
        caulis.addWidget(w)
        // A click elsewhere closes it.
        caulis.onPointerButton { _, pressed ->
            if (pressed && shown) {
                val d = caulis.desktop
                val area = d.monitors.firstNotNullOfOrNull { w.area(it) }
                if (area == null || !area.contains(Rect(d.pointerX.toInt(), d.pointerY.toInt(), 1, 1))) close()
            }
            false
        }
    }

    // A new thing on the clipboard: read (a picture, else text) and kept first.
    private fun take(data: ClipboardData) {
        val types = data.mimeTypes
        // A password manager's secret: not kept.
        if (types.any { it.startsWith("x-kde-passwordManagerHint") }) return log.trace { "Not kept: marked secret" }
        val picture = types.firstOrNull { it == "image/png" } ?: types.firstOrNull { it.startsWith("image/") && it != "image/svg+xml" }
        val text = types.firstOrNull { it == "text/plain;charset=utf-8" } ?: types.firstOrNull { it == "UTF8_STRING" } ?: types.firstOrNull { it == "text/plain" } ?: types.firstOrNull { it.startsWith("text/") }
        val mime = picture ?: text ?: return
        readClipboard(data, mime) { bytes ->
            if (bytes == null || bytes.isEmpty()) return@readClipboard
            val entry = if (mime == picture) {
                val image = runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes) }.getOrNull() ?: return@readClipboard
                // (Kept as PNG: what is copied back is a PNG.)
                val png = if (mime == "image/png") bytes else image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes ?: return@readClipboard
                ClipEntry(null, png, image.toComposeImageBitmap(), image.width, image.height)
            } else {
                val s = String(bytes, Charsets.UTF_8).trimEnd('\u0000')
                if (s.isBlank()) return@readClipboard
                ClipEntry(s, null, null, 0, 0)
            }
            wm.caulis.post { add(entry) }
        }
    }

    private fun add(entry: ClipEntry) {
        entries.removeAll { it.same(entry) }
        entries.add(0, entry)
        while (entries.size > confSize().coerceIn(1, 500)) entries.removeAt(entries.size - 1)
        log.trace { "Kept: ${entry.text?.take(20) ?: "a picture ${entry.width}x${entry.height}"}" }
    }

    /** Copies [entry] again (and closes). */
    fun use(entry: ClipEntry) {
        val data: ClipboardData = entry.png?.let { BytesOnClipboard("image/png", it) } ?: TextOnClipboard(entry.text ?: "")
        ours = data
        wm.caulis.clipboard.setClipboard(data)
        entries.remove(entry)
        entries.add(0, entry)
        close()
    }

    fun remove(entry: ClipEntry) {
        entries.remove(entry)
    }

    fun clear() = entries.clear()

    fun toggle() = if (shown) close() else open()

    fun open() {
        if (!confEnabled()) return run { wm.caulis.notify(newm.bar.tr("The clipboard's history is off (clipboard { enabled = true } turns it on)."), title = "newm") }
        opened++
        shown = true
        widget?.let { wm.caulis.desktop.focusWidget(it) }
    }

    fun close() {
        if (!shown) return
        shown = false
        if (wm.caulis.desktop.keyboardWidget === widget) wm.caulis.desktop.focusWidget(null)
    }

    @Composable
    private fun ClipboardPanel(history: ClipboardHistory) {
        if (!history.shown) return
        val colors = wm.barModel.colorsNow()
        val text = TextStyle(color = colors.text, fontSize = 14.sp)
        var query by remember(opened) { mutableStateOf("") }
        var selected by remember(opened) { mutableIntStateOf(0) }
        val focus = remember { FocusRequester() }
        val list = rememberLazyListState()
        val appear = remember(opened) { Animatable(0f) }
        LaunchedEffect(opened) {
            runCatching { focus.requestFocus() }
            appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 520f))
        }
        val shown = history.entries.filter { e -> query.isBlank() || e.text?.contains(query, ignoreCase = true) == true || (e.png != null && "picture image".contains(query.lowercase())) }
        if (selected >= shown.size) selected = (shown.size - 1).coerceAtLeast(0)
        var byKeys by remember(opened) { mutableIntStateOf(0) }
        LaunchedEffect(byKeys) { if (byKeys > 0 && shown.isNotEmpty()) list.animateScrollToItem(selected) }
        val current = shown.getOrNull(selected)
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier.fillMaxSize().padding(16.dp)
                .softShadow(20.dp, 14.dp, shown = { appear.value.coerceIn(0f, 1f) })
                .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f); scaleX = 0.95f + 0.05f * appear.value; scaleY = scaleX; translationY = (1 - appear.value) * -14f * density }
                .background(colors.island.copy(alpha = 0.97f), shape)
                .border(1.dp, colors.edge, shape)
                .padding(14.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                // The search, and what can be done.
                Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(colors.text.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) BasicText(newm.bar.tr("Search what was copied"), style = text.copy(color = colors.dim, fontSize = 16.sp))
                        BasicTextField(
                            query, { query = it; selected = 0 }, singleLine = true, textStyle = text.copy(fontSize = 16.sp), cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionDown, Key.Tab -> { selected = (selected + 1).coerceAtMost(shown.size - 1); byKeys++; true }
                                    Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); byKeys++; true }
                                    Key.Enter, Key.NumPadEnter -> { current?.let(history::use); true }
                                    Key.Delete -> { current?.let(history::remove); true }
                                    Key.Escape -> { history.close(); true }
                                    else -> false
                                }
                            },
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    if (history.entries.isNotEmpty()) SmallButton("Clear all", colors, text) { history.clear() }
                }
                Spacer(Modifier.height(12.dp))
                if (history.entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        BasicText(newm.bar.tr("Nothing copied yet — what you copy shows up here."), style = text.copy(color = colors.dim))
                    }
                    return@Column
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    // The list.
                    LazyColumn(state = list, modifier = Modifier.width(360.dp).fillMaxHeight()) {
                        itemsIndexed(shown) { i, entry ->
                            EntryRow(entry, i == selected, colors, text, onHover = { selected = i }, onClick = { history.use(entry) }, onRemove = { history.remove(entry) })
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    // What the chosen one is, whole.
                    Box(Modifier.weight(1f).fillMaxHeight().background(colors.text.copy(alpha = 0.04f), RoundedCornerShape(14.dp)).padding(14.dp)) {
                        when {
                            current == null -> {}
                            current.picture != null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(current.picture, null, Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
                                Spacer(Modifier.height(8.dp))
                                BasicText(newm.bar.tr("${current.width} × ${current.height} · copied at ${current.time}"), style = text.copy(color = colors.dim, fontSize = 12.sp))
                            }
                            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                BasicText(current.text ?: "", style = text.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                BasicText(newm.bar.tr("↑ ↓ choose   ·   Enter copy   ·   Delete remove   ·   Esc close"), style = text.copy(color = colors.dim, fontSize = 11.sp))
            }
        }
    }

    @Composable
    private fun EntryRow(entry: ClipEntry, selected: Boolean, colors: newm.bar.BarColors, text: TextStyle, onHover: () -> Unit, onClick: () -> Unit, onRemove: () -> Unit) {
        val source = remember { MutableInteractionSource() }
        val hovered by source.collectIsHoveredAsState()
        LaunchedEffect(hovered) { if (hovered) onHover() }
        Row(
            Modifier.fillMaxWidth().height(if (entry.picture != null) 72.dp else 56.dp)
                .background(if (selected) colors.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(12.dp)).hoverable(source)
                .pointerInput(onClick) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onClick() } }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.picture != null) {
                Image(entry.picture, null, Modifier.size(56.dp).background(colors.text.copy(alpha = 0.06f), RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(newm.bar.tr("Picture"), style = text.copy(fontWeight = FontWeight.Medium))
                    BasicText("${entry.width} × ${entry.height} · ${entry.time}", style = text.copy(color = colors.dim, fontSize = 12.sp))
                }
            } else {
                Column(Modifier.weight(1f)) {
                    BasicText(entry.text!!.lines().first { it.isNotBlank() }.trim(), style = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val more = entry.text.trimEnd().lines().size
                    BasicText(if (more > 1) newm.bar.tr("$more lines · ${entry.time}") else entry.time, style = text.copy(color = colors.dim, fontSize = 12.sp))
                }
            }
            if (hovered || selected) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(26.dp).background(colors.text.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .pointerInput(onRemove) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); if (e.type == PointerEventType.Release) { e.changes.forEach { it.consume() }; onRemove() } } } },
                    contentAlignment = Alignment.Center) {
                    BasicText("✕", style = text.copy(color = colors.dim, fontSize = 12.sp))
                }
            }
        }
    }
}
