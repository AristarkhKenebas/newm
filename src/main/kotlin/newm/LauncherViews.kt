package newm

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import newm.bar.BarColors
import newm.bar.IconTheme

/**
 * The launcher's categories (launcher { view = "categories" }): a heading and the XDG
 * categories of the programs under it, in this order — a program goes under the first heading
 * one of its categories is in; none: "Other".
 */
internal val DEFAULT_LAUNCHER_CATEGORIES: Map<String, List<String>> = linkedMapOf(
    "Internet" to listOf("Network", "WebBrowser", "Email", "Chat", "InstantMessaging", "FileTransfer"),
    "Office" to listOf("Office", "WordProcessor", "Spreadsheet", "Presentation", "Calendar", "ContactManagement"),
    "Development" to listOf("Development", "IDE", "Debugger", "RevisionControl"),
    "Graphics" to listOf("Graphics", "2DGraphics", "3DGraphics", "RasterGraphics", "VectorGraphics", "Photography", "Viewer"),
    "Sound & Video" to listOf("AudioVideo", "Audio", "Video", "Player", "Recorder", "Music"),
    "Games" to listOf("Game"),
    "Education" to listOf("Education", "Science"),
    "System" to listOf("System", "Settings", "TerminalEmulator", "Monitor", "PackageManager", "HardwareSettings"),
    "Accessories" to listOf("Utility", "TextEditor", "Calculator", "Archiving", "FileManager", "Accessibility"),
)

/** The heading of [app] among [headings] (heading → XDG categories), "Other" when none fits. */
internal fun categoryOf(app: DesktopApp, headings: Map<String, List<String>>): String {
    if (app.categories.isEmpty()) return "Other"
    for ((heading, categories) in headings) if (app.categories.any { it in categories }) return heading
    return "Other"
}

/** The headings with programs under them, in [headings]' order ("Other" last). */
internal fun categoriesOf(apps: List<DesktopApp>, headings: Map<String, List<String>>): List<String> {
    val used = apps.map { categoryOf(it, headings) }.toSet()
    return headings.keys.filter { it in used } + listOfNotNull("Other".takeIf { it in used && it !in headings })
}

/** Where a step of [by] (±1 across, ±[columns] down) takes the choice [from] in a grid of [count]; it stays when there is nothing there. */
internal fun gridStep(from: Int, by: Int, count: Int): Int {
    if (count <= 0) return 0
    val to = from + by
    return if (to < 0 || to >= count) from.coerceIn(0, count - 1) else to
}

/** A page of the launcher's grid: [columns] x [rows] programs, the choice lit, dots for the pages below. */
@Composable
internal fun LauncherGrid(
    shown: List<DesktopApp>, page: Int, columns: Int, rows: Int, selected: () -> Int, colors: BarColors, text: TextStyle,
    icons: MutableMap<String, ImageBitmap?>, wm: Layout, onHover: (Int) -> Unit, onClick: (DesktopApp) -> Unit, onPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val perPage = (columns * rows).coerceAtLeast(1)
    val pages = ((shown.size + perPage - 1) / perPage).coerceAtLeast(1)
    Column(modifier) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            for (r in 0 until rows) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0 until columns) {
                        val i = page * perPage + r * columns + c
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            shown.getOrNull(i)?.let { app ->
                                GridCell(app, { selected() == i }, colors, text, icons, wm, onHover = { onHover(i) }, onClick = { onClick(app) })
                            }
                        }
                    }
                }
            }
        }
        // The pages, as dots (a click goes to one).
        if (pages > 1) Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            for (p in 0 until pages) {
                Box(Modifier.padding(horizontal = 4.dp).size(if (p == page) 9.dp else 7.dp)
                    .background(if (p == page) colors.accent else colors.dim.copy(alpha = 0.45f), CircleShape)
                    .pointerInput(p) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onPage(p) } })
            }
        }
    }
}

// A program in the grid: its icon, big, and its name under it (two lines at most).
@Composable
private fun GridCell(app: DesktopApp, selected: () -> Boolean, colors: BarColors, text: TextStyle, icons: MutableMap<String, ImageBitmap?>, wm: Layout, onHover: () -> Unit, onClick: () -> Unit) {
    val source = androidx.compose.runtime.remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    val lit = colors.accent.copy(alpha = 0.18f)
    Column(
        Modifier.fillMaxSize().padding(4.dp)
            .drawBehind { if (selected()) drawRoundRect(lit, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())) }
            .hoverable(source)
            .pointerInput(onClick) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onClick() } }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LauncherIcon(app, 56, colors, text, icons, wm)
        Spacer(Modifier.height(8.dp))
        BasicText(app.name, style = text.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
    }
}

/** A result's picture, [sizeDp] big: a glyph for what is not a program, else its icon (its first letter while there is none). */
@Composable
internal fun LauncherIcon(app: DesktopApp, sizeDp: Int, colors: BarColors, text: TextStyle, icons: MutableMap<String, ImageBitmap?>, wm: Layout) {
    val glyph = when (app.kind) {
        "calc" -> newm.bar.Glyph.CALCULATOR
        "command" -> newm.bar.Glyph.TERMINAL
        "action" -> newm.bar.Glyph.SETTINGS
        "file" -> newm.bar.Glyph.FILE
        else -> null
    }
    // (Results that are not programs draw a glyph: no icon looked up for them.)
    val key = if (glyph == null) app.icon.ifEmpty { app.id } else ""
    val iconPixels = with(androidx.compose.ui.platform.LocalDensity.current) { sizeDp.dp.roundToPx() }
    LaunchedEffect(key, iconPixels) {
        if (key.isNotEmpty() && "$key@$iconPixels" !in icons) {
            icons["$key@$iconPixels"] = null
            // At the size it is drawn in pixels: drawn as it is, not shrunk anew each frame.
            IconTheme.load(key, iconPixels, null, post = { wm.caulis.post { it() } }) { icons["$key@$iconPixels"] = it }
        }
    }
    val picture = icons["$key@$iconPixels"]
    val corner = RoundedCornerShape((sizeDp / 4).dp)
    when {
        glyph != null -> Box(Modifier.size(sizeDp.dp).background(colors.accent.copy(alpha = 0.16f), corner), contentAlignment = Alignment.Center) {
            newm.bar.GlyphIcon(glyph, colors.text, (sizeDp / 2).dp)
        }
        picture != null -> Image(picture, null, Modifier.size(sizeDp.dp), filterQuality = FilterQuality.Low)
        else -> Box(Modifier.size(sizeDp.dp).background(colors.accent.copy(alpha = 0.25f), corner), contentAlignment = Alignment.Center) {
            BasicText(app.name.take(1).uppercase(), style = text.copy(fontWeight = FontWeight.Bold, fontSize = (sizeDp * 0.4).sp))
        }
    }
}

/** The categories' column (launcher { view = "categories" }): "All" and each heading; the chosen one lit. */
@Composable
internal fun CategoryColumn(categories: List<String>, chosen: String, colors: BarColors, text: TextStyle, onChoose: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(176.dp).fillMaxHeight().padding(end = 10.dp)) {
        for (c in listOf("") + categories) {
            val on = c == chosen
            Box(
                Modifier.fillMaxWidth().height(36.dp)
                    .background(if (on) colors.accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                    .pointerInput(c) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onChoose(c) } }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(newm.bar.tr(c.ifEmpty { "All" }), style = text.copy(fontSize = 13.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) colors.text else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
