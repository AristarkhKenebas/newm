package newm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.compose.compose
import dev.caulis.core.appearance.ColorScheme
import dev.caulis.geometry.Rect
import newm.bar.BarColors
import newm.bar.softShadow

/** The keys' help (Logo+/): every key of the configuration and what it does, over the screen's middle. */
internal fun Layout.showKeyHelp(): AutoCloseable {
    // A click anywhere closes it.
    caulis.onPointerButton { _, pressed ->
        if (pressed && keyHelpShown) keyHelpShown = false
        false
    }
    return caulis.compose(appearance = { _ -> keyHelpFade.appearance(rise = 10f, smallest = 0.95f) }, moving = { keyHelpFade.moving }, place = { m ->
        // On the monitor with the pointer.
        val d = caulis.desktop
        val target = d.monitors.firstOrNull { it.area.contains(Rect(d.pointerX.toInt(), d.pointerY.toInt(), 1, 1)) } ?: d.monitors.firstOrNull()
        // (Never over the lock screen.)
        if (!keyHelpFade.visible || m !== target || lockShown()) {
            null
        } else {
            val w = minOf(1060, m.area.width - 80)
            val h = minOf(keyHelpHeight(), m.area.height - 80)
            Rect(m.area.x + (m.area.width - w) / 2, m.area.y + (m.area.height - h) / 2, w, h)
        }
    }) { _ -> if (keyHelpFade.visible || keyHelpShown) KeyHelpPanel(this) }
}

// Rows of two keys, a title and the gestures below.
// (Its margins and title, the rows, the touchpad's title and rows, a little room below them.)
private fun Layout.keyHelpHeight(): Int = 60 + 40 + (helpRows(keyHelp()).size + 1) / 2 * 32 + 42 + (gestureHelp().size + 1) / 2 * 26 - 2

/** Keys that do the same (the same words) in one row: their combinations, and the words. */
internal fun helpRows(keys: List<Pair<String, String>>): List<Pair<List<String>, String>> {
    val rows = LinkedHashMap<String, MutableList<String>>()
    var unnamed = 0
    for ((combo, what) in keys) rows.getOrPut(what.ifEmpty { "\u0000${unnamed++}" }) { ArrayList() } += combo
    return rows.map { (what, combos) -> combos to (if (what.startsWith("\u0000")) "" else what) }
}

// The gestures as the configuration binds them (gesture_bindings, mouse { }, gestures { on() }).
private val confSwipe = configuredValue<List<String?>>("gesture_bindings.swipe", listOf(null, "swipe-3"))
private val confZoom = configuredValue<List<String?>>("gesture_bindings.swipe_to_zoom", listOf(null, "swipe-4"))
private val confMoveResize = configuredValue<List<String?>>("gesture_bindings.move_resize", listOf("L", "move-1", "swipe-2"))
private val confLauncher = configuredValue<List<String?>>("gesture_bindings.launcher", listOf(null, "swipe-5"))
private val confOwnGestures = configuredValue("gestures.on", emptyList<Map<String, Any?>>())
private val confCGestures = configuredValue("gestures.c.enabled", true)

/** A gesture in words: "3 fingers", "Super + 2 fingers" ([modifier]: newm's letter, "L"; [kind]: "swipe-3", "move-1"). */
internal fun gestureWords(modifier: String?, kind: String?): String {
    val fingers = kind?.substringAfter('-', "")?.toIntOrNull() ?: 0
    val what = when {
        kind == null -> ""
        fingers == 1 -> "1 finger"
        fingers > 1 -> "$fingers fingers"
        else -> kind
    }
    return listOfNotNull(modifierWord(modifier), what.ifEmpty { null }).joinToString(" + ")
}

private fun modifierWord(modifier: String?): String? = when (modifier) {
    null, "" -> null
    "L" -> "Super"
    "A" -> "Alt"
    "C" -> "Ctrl"
    "S" -> "Shift"
    else -> modifier
}

/** The touchpad's and the mouse's gestures and what they do (in English: the panel translates them). */
internal fun gestureHelp(): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    val touchpad = confCGestures()
    val moveResize = confMoveResize()
    if (touchpad) {
        confSwipe().let { b -> b.getOrNull(1)?.let { out += gestureWords(b[0], it) to "Move over the wall" } }
        confZoom().let { b -> b.getOrNull(1)?.let { out += "${gestureWords(b[0], it)} up / down" to "Zoom out / in" } }
        confLauncher().let { b -> b.getOrNull(1)?.let { out += gestureWords(b[0], it) to "Launcher" } }
    }
    // The mouse with the modifier held (mouse { superMove, moveButton, resizeButton }).
    val mod = modifierWord(moveResize.getOrNull(0))
    if (mod != null && moveResize.getOrNull(1) != null) {
        if (confSuperMove() == "motion") out += "$mod + mouse" to "Move the window"
        else {
            mouseButtonCode(confMoveButton())?.let { out += "$mod + ${mouseButtonName(it)} drag" to "Move the window" }
            mouseButtonCode(confResizeButton())?.let { out += "$mod + ${mouseButtonName(it)} drag" to "Resize the window" }
        }
    }
    if (touchpad) moveResize.getOrNull(2)?.let { out += gestureWords(moveResize[0], it) to "Resize the window" }
    for (g in confOwnGestures()) {
        val kind = g["kind"] as? String ?: continue
        val way = g["direction"] as? String
        val words = gestureWords(g["modifier"] as? String, kind) + (way?.let { " $it" } ?: "")
        out += words to ((g["description"] as? String)?.ifBlank { null } ?: "Your own")
    }
    return out
}

@Composable
internal fun KeyHelpPanel(wm: Layout) {
    val colors = wm.barModel.colorsNow()
    val surface = colors.island.copy(alpha = 0.96f)
    val text = TextStyle(color = colors.text, fontSize = 13.sp)
    val shape = RoundedCornerShape(18.dp)
    val keys = helpRows(wm.keyHelp())
    Box(
        Modifier.fillMaxSize().padding(14.dp)
            .softShadow(18.dp, 14.dp)
            .background(surface, shape)
            .border(1.dp, colors.edge, shape)
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 12.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                newm.bar.IconButton(newm.bar.Glyph.CLOSE, colors, 28.dp) { wm.keyHelpShown = false }
                Spacer(Modifier.width(10.dp))
                BasicText(newm.bar.tr("Keys"), style = text.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.weight(1f))
                BasicText(newm.bar.tr("Any key closes this"), style = text.copy(color = colors.dim, fontSize = 12.sp))
            }
            Spacer(Modifier.height(12.dp))
            for (pair in keys.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    for ((combo, what) in pair) KeyRow(combo, what, colors, text, Modifier.weight(1f))
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            val gestures = gestureHelp()
            if (gestures.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                BasicText(newm.bar.tr("Touchpad"), style = text.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(6.dp))
            }
            for (pair in gestures.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    for ((how, what) in pair) Row(Modifier.weight(1f).height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cap(newm.bar.tr(how), colors)
                        Spacer(Modifier.width(10.dp))
                        BasicText(newm.bar.tr(what), style = text, maxLines = 1)
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KeyRow(combos: List<String>, what: String, colors: BarColors, text: TextStyle, modifier: Modifier) {
    Row(modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        val named = combos.map(::keyNames)
        // The same modifiers and one key each (Super+H, Super+←): the modifiers once, the keys "H / ←".
        val singles = named.all { it.size == 1 && it[0].size >= 1 }
        val mods = named.first().first().dropLast(1)
        val shared = singles && named.all { it[0].dropLast(1) == mods }
        val faint = text.copy(color = colors.dim, fontSize = 11.sp)
        Row(Modifier.width(240.dp), verticalAlignment = Alignment.CenterVertically) {
            if (shared) {
                for (m in mods) {
                    Cap(m, colors)
                    BasicText(newm.bar.tr("+"), style = faint, modifier = Modifier.padding(horizontal = 3.dp))
                }
                named.forEachIndexed { k, steps ->
                    if (k > 0) BasicText(newm.bar.tr(" / "), style = faint)
                    Cap(steps[0].last(), colors)
                }
            } else {
                named.take(2).forEachIndexed { c, steps ->
                    if (c > 0) BasicText(newm.bar.tr("or").let { " $it " }, style = faint)
                    steps.forEachIndexed { k, step ->
                        if (k > 0) BasicText(newm.bar.tr(" then "), style = faint)
                        step.forEachIndexed { n, part ->
                            if (n > 0) BasicText(newm.bar.tr("+"), style = faint, modifier = Modifier.padding(horizontal = 3.dp))
                            Cap(part, colors)
                        }
                    }
                }
            }
        }
        BasicText(newm.bar.tr(what).ifEmpty { "—" }, style = text.copy(color = if (what.isEmpty()) colors.dim else colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// A key as a little keycap.
@Composable
private fun Cap(name: String, colors: BarColors) {
    Box(
        Modifier.background(colors.text.copy(alpha = 0.08f), RoundedCornerShape(6.dp)).border(1.dp, colors.text.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) { BasicText(name, style = TextStyle(color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)) }
}

/**
 * A combination as newm writes it ("L-S-h", "L-", "L-a L-b") in words: a list of steps, each
 * a list of keys (["Super", "Shift", "H"]).
 */
internal fun keyNames(combo: String): List<List<String>> = combo.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { step ->
    val parts = step.split('-')
    val mods = parts.dropLast(1).mapNotNull {
        when (it) {
            "L" -> "Super"
            "S" -> "Shift"
            "C" -> "Ctrl"
            "A", "1" -> "Alt"
            else -> null
        }
    }.toMutableList()
    var key = parts.last()
    if (key.length == 1 && key != key.lowercase()) {
        if ("Shift" !in mods) mods += "Shift"
        key = key.lowercase()
    }
    val name = when {
        key.isEmpty() -> null // "L-": the modifier alone
        key.length == 1 -> key.uppercase()
        else -> KEY_NAMES[key] ?: key
    }
    if (name == null) listOf(newm.bar.tr("%s alone").replace("%s", mods.joinToString("+"))) else mods + name
}

private val KEY_NAMES = mapOf(
    "Return" to "Enter", "space" to "Space", "Left" to "←", "Right" to "→", "Up" to "↑", "Down" to "↓", "Escape" to "Esc",
    "slash" to "/", "minus" to "−", "equal" to "=", "plus" to "+", "comma" to ",", "period" to ".", "Print" to "PrtSc",
    "BackSpace" to "Backspace", "XF86MonBrightnessUp" to "Brightness +", "XF86MonBrightnessDown" to "Brightness −",
    "XF86AudioRaiseVolume" to "Volume +", "XF86AudioLowerVolume" to "Volume −", "XF86AudioMute" to "Mute", "XF86AudioMicMute" to "Mic mute",
    "XF86AudioPlay" to "Play", "XF86AudioNext" to "Next", "XF86AudioPrev" to "Previous",
)
