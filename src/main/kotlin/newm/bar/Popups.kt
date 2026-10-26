package newm.bar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.compose.compose
import dev.caulis.log.Logger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val log = Logger("bar")

/** The bars' popups: one Compose part above everything, where the open popup is. */
fun BarModel.showPopups(): AutoCloseable = wm.caulis.compose(place = { m ->
    popup?.takeIf { it.bar.monitor === m }?.let(::popupRect)
}) { m ->
    val open = popup
    if (open != null && open.bar.monitor === m) PopupContent(this, open)
}

@Composable
private fun PopupContent(model: BarModel, popup: Popup) {
    val colors = model.colorsFor(popup.bar)
    // Popups are solid (no blur behind them): a little more opaque than the bar.
    val surface = colors.island.copy(alpha = (colors.island.alpha + 0.16f).coerceAtMost(0.97f))
    val text = TextStyle(color = colors.text, fontSize = 13.sp, fontFamily = popup.bar.fontFamily)
    val appear = remember(popup) { Animatable(0f) }
    LaunchedEffect(popup) { appear.animateTo(1f, tween(150)) }
    val shape = RoundedCornerShape(if (popup.kind == PopupKind.TOOLTIP) 9.dp else 14.dp)
    Box(
        Modifier.fillMaxSize()
            .padding(POPUP_SHADOW.dp)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1 - appear.value) * (if (popup.bar.top) -6f else 6f) * density
            }
            .shadow(10.dp, shape, ambientColor = Color(0x38000000), spotColor = Color(0x50000000))
            .background(surface, shape)
            .border(1.dp, colors.edge, shape)
            .padding(if (popup.kind == PopupKind.TOOLTIP) 9.dp else 12.dp),
    ) {
        when (popup.kind) {
            PopupKind.CALENDAR -> Calendar(model, popup, colors, text)
            PopupKind.VOLUME -> VolumePopup(model, colors, text)
            PopupKind.NETWORK -> NetworkPopup(model, colors, text)
            PopupKind.BATTERY -> BatteryPopup(model, colors, text)
            PopupKind.POWER -> PowerPopup(model, popup, colors, text)
            PopupKind.TRAY_MENU -> TrayMenu(model, popup, colors, text)
            PopupKind.TOOLTIP -> BasicText(popup.text, style = text.copy(fontSize = 12.sp))
        }
    }
}

/** A row of a popup that lights up under the pointer and does [onClick]. */
@Composable
private fun MenuRow(colors: BarColors, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered && enabled) colors.hover else Color.Transparent, tween(100))
    Box(
        Modifier.fillMaxWidth().height(30.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .hoverable(source)
            .pointerInput(enabled, onClick) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Release && enabled) onClick()
                    }
                }
            }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

// --- the calendar ------------------------------------------------------------------------------

@Composable
private fun Calendar(model: BarModel, popup: Popup, colors: BarColors, text: TextStyle) {
    val today = model.now.toLocalDate()
    val month = today.withDayOfMonth(1).plusMonths(popup.monthOffset.toLong())
    val locale = Locale.getDefault()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    Column {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(colors, Glyph.CHEVRON_LEFT) { popup.monthOffset -= 1 }
            Box(Modifier.weight(1f).pointerInput(Unit) {
                awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) popup.monthOffset = 0 }
            }, contentAlignment = Alignment.Center) {
                BasicText("${month.month.getDisplayName(DateTextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }} ${month.year}", style = text.copy(fontWeight = FontWeight.SemiBold))
            }
            IconButton(colors, Glyph.CHEVRON_RIGHT) { popup.monthOffset += 1 }
        }
        Spacer(Modifier.height(6.dp))
        val days = (0 until 7).map { firstDay.plus(it.toLong()) }
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            for (d in days) Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
                BasicText(d.getDisplayName(DateTextStyle.SHORT_STANDALONE, locale).take(2), style = text.copy(color = colors.dim, fontSize = 11.sp))
            }
        }
        // Six weeks from the week of the 1st.
        val lead = Math.floorMod(month.dayOfWeek.value - firstDay.value, 7)
        val start: LocalDate = month.minusDays(lead.toLong())
        for (week in 0 until 6) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            for (d in 0 until 7) {
                val day = start.plusDays((week * 7 + d).toLong())
                val inMonth = day.month == month.month
                val isToday = day == today
                val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
                Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(28.dp).background(if (isToday) colors.accent else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            "${day.dayOfMonth}",
                            style = text.copy(
                                color = when {
                                    isToday -> Color.White
                                    !inMonth -> colors.dim.copy(alpha = 0.45f)
                                    weekend -> colors.dim
                                    else -> colors.text
                                },
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconButton(colors: BarColors, glyph: Glyph, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier.size(28.dp).background(if (hovered) colors.hover else Color.Transparent, CircleShape).hoverable(source)
            .pointerInput(onClick) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onClick() } },
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, colors.text, 14.dp) }
}

// --- volume and brightness --------------------------------------------------------------------

@Composable
private fun VolumePopup(model: BarModel, colors: BarColors, text: TextStyle) {
    val v = model.status.volume
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (v != null) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).background(if (v.muted) colors.hover else Color.Transparent, CircleShape)
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) { model.wm.caulis.run("volume mute"); model.status.refreshSoon() } } },
                    contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.SPEAKER, if (v.muted) colors.dim else colors.text, 18.dp, level = v.percent / 100f, slash = v.muted)
                }
                Spacer(Modifier.width(8.dp))
                Slider(colors, v.percent / 100f, Modifier.weight(1f)) { level -> setVolume(model, level) }
                Spacer(Modifier.width(8.dp))
                BasicText("${v.percent}%", style = text.copy(color = colors.dim, fontSize = 12.sp), modifier = Modifier.width(36.dp))
            }
        } else {
            BasicText("No sound device", style = text.copy(color = colors.dim))
        }
        model.status.brightness?.let { b ->
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.BRIGHTNESS, colors.text, 18.dp) }
                Spacer(Modifier.width(8.dp))
                Slider(colors, b / 100f, Modifier.weight(1f)) { level -> setBrightness(model, level) }
                Spacer(Modifier.width(8.dp))
                BasicText("$b%", style = text.copy(color = colors.dim, fontSize = 12.sp), modifier = Modifier.width(36.dp))
            }
        }
        MenuRow(colors, onClick = { model.close(); model.wm.caulis.launch(firstInstalled("pavucontrol", "pwvucontrol", "helvum") ?: "pavucontrol") }) {
            BasicText("Sound settings…", style = text)
        }
    }
}

// A slider of 0..1 that follows the pointer while it is held ([onChange] as it moves).
@Composable
private fun Slider(colors: BarColors, value: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    var dragging by remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    val shown = dragging ?: value.coerceIn(0f, 1f)
    var lastSent by remember { mutableFloatStateOf(-1f) }
    Canvas(
        modifier.height(22.dp).pointerInput(Unit) {
            awaitPointerEventScope {
                var held = false
                while (true) {
                    val e = awaitPointerEvent()
                    val x = e.changes.firstOrNull()?.position?.x ?: continue
                    val level = (x / size.width).coerceIn(0f, 1f)
                    when (e.type) {
                        PointerEventType.Press -> { held = true; dragging = level }
                        PointerEventType.Move -> if (held) dragging = level
                        PointerEventType.Release -> if (held) { held = false; onChange(level); lastSent = level; dragging = null }
                        else -> {}
                    }
                    // While dragging: now and then, not on every move.
                    val d = dragging
                    if (held && d != null && Math.abs(d - lastSent) > 0.04f) { onChange(d); lastSent = d }
                }
            }
        },
    ) {
        val track = 5.dp.toPx()
        val y = size.height / 2
        drawRoundRect(colors.text.copy(alpha = 0.14f), Offset(0f, y - track / 2), Size(size.width, track), CornerRadius(track))
        drawRoundRect(colors.accent, Offset(0f, y - track / 2), Size(size.width * shown, track), CornerRadius(track))
        drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(size.width * shown, y))
        drawCircle(colors.accent, radius = 8.dp.toPx(), center = Offset(size.width * shown, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
    }
}

private fun setVolume(model: BarModel, level: Float) {
    val percent = (level * 100).toInt()
    Thread({
        runQuietly("wpctl", "set-mute", "@DEFAULT_AUDIO_SINK@", "0") || runQuietly("pactl", "set-sink-mute", "@DEFAULT_SINK@", "0")
        runQuietly("wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", "$percent%") || runQuietly("pactl", "set-sink-volume", "@DEFAULT_SINK@", "$percent%")
        model.status.refreshSoon()
    }, "newm-volume").apply { isDaemon = true }.start()
}

private fun setBrightness(model: BarModel, level: Float) {
    val percent = (level * 100).toInt().coerceAtLeast(1)
    Thread({
        if (!runQuietly("brightnessctl", "-q", "set", "$percent%")) log.warn("brightnessctl is not installed: the brightness cannot be set")
        model.status.refreshSoon()
    }, "newm-brightness").apply { isDaemon = true }.start()
}

private fun runQuietly(vararg command: String): Boolean = try {
    val p = ProcessBuilder(*command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
    p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0
} catch (_: Exception) {
    false
}

private fun firstInstalled(vararg programs: String): String? = programs.firstOrNull { p ->
    (System.getenv("PATH") ?: "/usr/bin").split(':').any { java.nio.file.Files.isExecutable(java.nio.file.Path.of(it, p)) }
}

// --- network and battery ---------------------------------------------------------------------

@Composable
private fun NetworkPopup(model: BarModel, colors: BarColors, text: TextStyle) {
    val n = model.status.network
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
            when (n.kind) {
                Network.Kind.WIFI -> GlyphIcon(Glyph.WIFI, colors.accent, 26.dp, level = n.strength / 100f)
                Network.Kind.WIRED -> GlyphIcon(Glyph.WIRED, colors.accent, 26.dp)
                Network.Kind.NONE -> GlyphIcon(Glyph.WIFI_OFF, colors.dim, 26.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                BasicText(when (n.kind) {
                    Network.Kind.WIFI -> n.name.ifEmpty { "Wi-Fi" }
                    Network.Kind.WIRED -> "Wired connection"
                    Network.Kind.NONE -> "Not connected"
                }, style = text.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (n.kind == Network.Kind.WIFI) BasicText("Signal ${n.strength}%", style = text.copy(color = colors.dim, fontSize = 12.sp))
            }
        }
        MenuRow(colors, onClick = {
            model.close()
            val editor = firstInstalled("nm-connection-editor", "iwgtk", "connman-gtk")
            model.wm.caulis.launch(editor ?: "${newm.defaultTerminal()} -e nmtui")
        }) { BasicText("Network settings…", style = text) }
    }
}

@Composable
private fun BatteryPopup(model: BarModel, colors: BarColors, text: TextStyle) {
    val b = model.status.battery ?: return BasicText("No battery", style = text.copy(color = colors.dim))
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
        GlyphIcon(Glyph.BATTERY, colors.text, 40.dp, level = b.percent / 100f, bolt = b.charging, accent = if (b.charging || b.full) colors.accent else if (b.percent <= 20) colors.warning else colors.text)
        Spacer(Modifier.width(14.dp))
        Column {
            BasicText("${b.percent}%", style = text.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            val minutes = b.minutesLeft
            BasicText(when {
                b.full -> "Fully charged"
                b.charging && minutes != null -> "Charging, full in ${hours(minutes)}"
                b.charging -> "Charging"
                minutes != null -> "${hours(minutes)} left"
                else -> "On battery"
            }, style = text.copy(color = colors.dim, fontSize = 12.sp))
        }
    }
}

private fun hours(minutes: Int): String = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"

// --- power -------------------------------------------------------------------------------------

@Composable
private fun PowerPopup(model: BarModel, popup: Popup, colors: BarColors, text: TextStyle) {
    val wm = model.wm
    // (Those that end the session ask once more.)
    val entries = listOf(
        Triple("lock", Glyph.LOCK, "Lock"),
        Triple("suspend", Glyph.MOON, "Suspend"),
        Triple("logout", Glyph.LOGOUT, "Log out"),
        Triple("reboot", Glyph.RESTART, "Restart"),
        Triple("poweroff", Glyph.POWER, "Shut down"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((id, glyph, label) in entries) {
            val asking = popup.confirming == id
            MenuRow(colors, onClick = {
                val final = id == "lock" || id == "suspend" || asking
                if (!final) {
                    popup.confirming = id
                    return@MenuRow
                }
                model.close()
                log.info { "Power menu: $id" }
                when (id) {
                    "lock" -> wm.ensureLocked()
                    "suspend" -> wm.caulis.launch("systemctl suspend")
                    "logout" -> wm.terminate()
                    "reboot" -> wm.caulis.launch("systemctl reboot")
                    "poweroff" -> wm.caulis.launch("systemctl poweroff")
                }
            }) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(glyph, if (asking) colors.danger else colors.text, 16.dp)
                    Spacer(Modifier.width(12.dp))
                    BasicText(if (asking) "$label? Click again" else label, style = text.copy(color = if (asking) colors.danger else colors.text, fontWeight = if (asking) FontWeight.SemiBold else FontWeight.Normal))
                }
            }
        }
    }
}

// --- a tray icon's menu --------------------------------------------------------------------------

@Composable
private fun TrayMenu(model: BarModel, popup: Popup, colors: BarColors, text: TextStyle) {
    val item = popup.item ?: return
    val all = popup.entries
    if (all == null) return BasicText("…", style = text.copy(color = colors.dim))
    val shown = popup.path.lastOrNull()?.children ?: all
    // (A long menu scrolls with the wheel.)
    Column(Modifier.padding(vertical = 1.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        if (popup.path.isNotEmpty()) MenuRow(colors, onClick = { popup.path = popup.path.dropLast(1) }) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.CHEVRON_LEFT, colors.dim, 12.dp)
                Spacer(Modifier.width(6.dp))
                BasicText(popup.path.last().label, style = text.copy(color = colors.dim, fontWeight = FontWeight.SemiBold), maxLines = 1)
            }
        }
        if (shown.isEmpty()) BasicText("(empty)", style = text.copy(color = colors.dim), modifier = Modifier.padding(9.dp))
        for (entry in shown) {
            if (entry.separator) {
                Box(Modifier.fillMaxWidth().height(9.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.text.copy(alpha = 0.1f)))
                }
                continue
            }
            MenuRow(colors, enabled = entry.enabled, onClick = {
                if (entry.children.isNotEmpty()) {
                    popup.path = popup.path + entry
                } else {
                    model.close()
                    model.tray.menuClicked(item, entry.id)
                }
            }) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    when (entry.toggle) {
                        "checkmark" -> Check(colors, entry.checked, round = false)
                        "radio" -> Check(colors, entry.checked, round = true)
                        else -> {}
                    }
                    if (entry.toggle.isNotEmpty()) Spacer(Modifier.width(8.dp))
                    BasicText(entry.label, style = text.copy(color = if (entry.enabled) colors.text else colors.dim.copy(alpha = 0.5f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (entry.children.isNotEmpty()) GlyphIcon(Glyph.CHEVRON_RIGHT, colors.dim, 12.dp)
                }
            }
        }
    }
}

@Composable
private fun Check(colors: BarColors, checked: Boolean, round: Boolean) {
    val shape = if (round) CircleShape else RoundedCornerShape(4.dp)
    Box(
        Modifier.size(14.dp).background(if (checked) colors.accent else Color.Transparent, shape).border(1.5.dp, if (checked) colors.accent else colors.dim, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Canvas(Modifier.size(8.dp)) {
            if (round) drawCircle(Color.White, size.minDimension / 3) else {
                val w = 1.6.dp.toPx()
                drawLine(Color.White, Offset(0f, size.height * 0.55f), Offset(size.width * 0.38f, size.height * 0.9f), w)
                drawLine(Color.White, Offset(size.width * 0.38f, size.height * 0.9f), Offset(size.width, size.height * 0.1f), w)
            }
        }
    }
}
