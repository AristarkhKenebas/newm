package newm.bar

import androidx.compose.runtime.key
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
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
import dev.caulis.log.Logger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val log = Logger("bar")

/** The bars' popups: one Compose part above everything, where the open popup is (a click on it gives it the keys). */
fun BarModel.showPopups(): AutoCloseable {
    val model = this
    val caulis = wm.caulis
    var handle: AutoCloseable? = null
    val start = caulis.after(0) {
        // A scene as big as the biggest popup: a popup that grows (more networks, a device
        // found) only shows more of it, nothing made anew.
        val widget = dev.caulis.compose.ComposeWidget(caulis.desktop, post = { task -> caulis.post { task.run() } }, place = { m ->
            if (!popupFade.visible) {
                // Gone: forgotten.
                if (shownPopup != null && popup == null) caulis.post { if (!popupFade.visible && popup == null) shownPopup = null }
                null
            } else shownPopup?.takeIf { it.bar.monitor === m }?.let(::popupRect)
        }, takesKeyboard = true,
            sceneSize = { _ -> dev.caulis.compose.SceneSize(POPUP_WIDEST + 2 * POPUP_SHADOW, POPUP_TALLEST + 2 * POPUP_SHADOW) },
            // (Not shown until its height is measured: else its top came first, the rest after.)
            appearance = { _ ->
                if (shownPopup?.measured == 0) dev.caulis.compose.WidgetLook(alpha = 0f)
                else popupFade.appearance(rise = if (shownPopup?.bar?.top != false) -8f else 8f, smallest = 0.97f, unfold = shownPopup?.bar?.top != false)
            },
            moving = { popupFade.moving },
        ) { m ->
            val open = shownPopup
            if (open != null && open.bar.monitor === m) PopupContent(model, open)
        }
        popupWidget = widget
        val dialog = passwordDialog.start()
        val popups = caulis.desktop.addWidget(widget)
        handle = AutoCloseable { popups.close(); dialog.close() }
    }
    return AutoCloseable {
        start.close()
        handle?.close()
    }
}

@Composable
internal fun PopupContent(model: BarModel, popup: Popup) {
    val colors = model.colorsFor(popup.bar)
    // Popups are solid (no blur behind them): a little more opaque than the bar.
    val surface = colors.island.copy(alpha = (colors.island.alpha + 0.16f).coerceAtMost(0.97f))
    val text = TextStyle(color = colors.text, fontSize = 13.sp, fontFamily = popup.bar.fontFamily)
    val shape = RoundedCornerShape(if (popup.kind == PopupKind.TOOLTIP) 9.dp else 14.dp)
    val density = androidx.compose.ui.platform.LocalDensity.current
    // As wide as its place; as tall as it is (measured: its place follows), growing smoothly.
    Box(Modifier.width(with(density) { (model.popupWidth(popup) + 2 * POPUP_SHADOW).toDp() }).wrapContentHeight(Alignment.Top, unbounded = false)
        .onSizeChanged { size -> popup.measured = with(density) { size.height.toDp().value.toInt() } - 2 * POPUP_SHADOW }) {
    Box(
        Modifier.fillMaxWidth()
            .padding(POPUP_SHADOW.dp)
            .softShadow(14.dp, 10.dp)
            .background(surface, shape)
            .border(1.dp, colors.edge, shape)
            .animateContentSize(newm.NewmAnimations.spec("popupsResize", null, newm.NewmAnimations.POPUP_SPRING))
            .padding(if (popup.kind == PopupKind.TOOLTIP) 9.dp else 12.dp),
    ) {
        when (popup.kind) {
            PopupKind.CALENDAR -> Calendar(model, popup, colors, text)
            PopupKind.VOLUME -> SoundPopup(model, colors, text)
            PopupKind.NETWORK -> WifiPopup(model, popup, colors, text)
            PopupKind.BLUETOOTH -> BluetoothPopup(model, popup, colors, text)
            PopupKind.BATTERY -> BatteryAndPowerPopup(model, popup, colors, text)
            PopupKind.POWER -> PowerPopup(model, popup, colors, text)
            PopupKind.QUICK -> QuickPopup(model, popup, colors, text)
            PopupKind.NOTIFICATIONS -> NotificationsPopup(model, colors, text)
            PopupKind.COLORS -> ColorsPopup(model, colors, text)
            PopupKind.TRAY_MENU -> TrayMenu(model, popup, colors, text)
            PopupKind.TOOLTIP -> BasicText(popup.text, style = text.copy(fontSize = 12.sp))
        }
    }
    }
}

/** A row of a popup that lights up under the pointer and does [onClick]. */
@Composable
private fun MenuRow(colors: BarColors, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered && enabled) colors.hover else Color.Transparent, newm.NewmAnimations.spec("hover", 100))
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
    val locale = dateLocale()
    // calendar { firstDay = "MONDAY" } (unset: the language's), weekend = [...], weekNumbers.
    val firstDay = confFirstDay()?.let { day -> runCatching { DayOfWeek.valueOf(day.trim().uppercase()) }.getOrNull() } ?: WeekFields.of(locale).firstDayOfWeek
    val weekendDays = confWeekend().mapNotNull { day -> runCatching { DayOfWeek.valueOf(day.trim().uppercase()) }.getOrNull() }.toSet()
    val weekNumbers = confWeekNumbers()
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
            if (weekNumbers) Box(Modifier.weight(0.8f).height(24.dp))
            for (d in days) Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
                BasicText(d.getDisplayName(DateTextStyle.SHORT_STANDALONE, locale).take(2), style = text.copy(color = colors.dim, fontSize = 11.sp))
            }
        }
        // Six weeks from the week of the 1st.
        val lead = Math.floorMod(month.dayOfWeek.value - firstDay.value, 7)
        val start: LocalDate = month.minusDays(lead.toLong())
        for (week in 0 until 6) androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            // The week's number (ISO's: the one with its Thursday).
            if (weekNumbers) Box(Modifier.weight(0.8f).height(32.dp), contentAlignment = Alignment.Center) {
                val thursday = start.plusDays((week * 7).toLong()).let { it.plusDays(Math.floorMod(DayOfWeek.THURSDAY.value - it.dayOfWeek.value, 7).toLong()) }
                BasicText("${thursday.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", style = text.copy(color = colors.dim.copy(alpha = 0.6f), fontSize = 10.sp))
            }
            for (d in 0 until 7) {
                val day = start.plusDays((week * 7 + d).toLong())
                val inMonth = day.month == month.month
                val isToday = day == today
                val weekend = day.dayOfWeek in weekendDays
                Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(28.dp).background(if (isToday) colors.accent else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            "${day.dayOfMonth}",
                            style = text.copy(
                                color = when {
                                    isToday -> onAccent(colors)
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

// A slider of 0..1 that follows the pointer while it is held ([onChange] as it moves).
@Composable
internal fun Slider(colors: BarColors, value: Float, modifier: Modifier, onChange: (Float) -> Unit) {
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

@Suppress("unused")
private fun setVolume(model: BarModel, level: Float) {
    val percent = (level * 100).toInt()
    Thread({
        runQuietly("wpctl", "set-mute", "@DEFAULT_AUDIO_SINK@", "0") || runQuietly("pactl", "set-sink-mute", "@DEFAULT_SINK@", "0")
        runQuietly("wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", "$percent%") || runQuietly("pactl", "set-sink-volume", "@DEFAULT_SINK@", "$percent%")
        model.status.refreshSoon()
    }, "newm-volume").apply { isDaemon = true }.start()
}

// One thread for brightnessctl: changes in order, and of many waiting only the last one (as the sound's volume).
private val brightnessWorker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "newm-brightness").apply { isDaemon = true } }
private val brightnessWanted = java.util.concurrent.atomic.AtomicInteger(-1)

internal fun setBrightness(model: BarModel, level: Float) {
    val percent = (level * 100).toInt().coerceIn(brightnessMinimum(), 100)
    // (A worker already waiting takes the new value; none waiting: one is started.)
    if (brightnessWanted.getAndSet(percent) != -1) return
    brightnessWorker.execute {
        val target = brightnessWanted.getAndSet(-1)
        if (target == -1) return@execute
        // brightness { set = "..." } ({percent}), else brightnessctl (of brightness.device, if given).
        val own = confSetBrightness()
        val device = confBrightnessDevice()?.takeIf { it.isNotBlank() }
        val done = when {
            own != null -> commandSucceeds(fillIn(own, "percent" to target, "device" to (device ?: "")))
            device != null -> runQuietly("brightnessctl", "-q", "--device=$device", "set", "$target%")
            else -> runQuietly("brightnessctl", "-q", "set", "$target%")
        }
        if (!done) log.warn(if (own != null) "The brightness cannot be set: \"$own\" failed" else "brightnessctl is not installed or did not answer: the brightness cannot be set")
        else log.trace { "Brightness $target%" }
        model.status.refreshSoon()
    }
}

/** Runs [command], its output thrown away; false when it failed or took over three seconds (then it is killed). */
internal fun runQuietly(vararg command: String): Boolean = try {
    val p = dev.caulis.ffi.Processes.start(ProcessBuilder(*command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD))
    p.outputStream.close()
    if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
        p.destroyForcibly()
        log.warn("${command.first()} took too long and was stopped")
        false
    } else p.exitValue() == 0
} catch (_: Exception) {
    false
}

private fun firstInstalled(vararg programs: String): String? = programs.firstOrNull { p ->
    (System.getenv("PATH") ?: "/usr/bin").split(':').any { java.nio.file.Files.isExecutable(java.nio.file.Path.of(it, p)) }
}

// --- network and battery ---------------------------------------------------------------------

// --- power -------------------------------------------------------------------------------------

@Composable
private fun PowerPopup(model: BarModel, popup: Popup, colors: BarColors, text: TextStyle) {
    val wm = model.wm
    // power { entries; confirm }: which, in which order; those in confirm ask once more (by default those that end the session).
    val confirm = confPowerConfirm()
    val entries = confPowerEntries().mapNotNull { id -> POWER_ENTRIES[id]?.let { (glyph, label) -> Triple(id, glyph, label) } }
        .filter { it.first != "hibernate" || model.canHibernate }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((id, glyph, label) in entries) {
            val asking = popup.confirming == id
            MenuRow(colors, onClick = {
                if (id in confirm && !asking) {
                    popup.confirming = id
                    return@MenuRow
                }
                model.close()
                runPowerEntry(id, wm)
            }) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(glyph, if (asking) colors.danger else colors.text, 16.dp)
                    Spacer(Modifier.width(12.dp))
                    BasicText(if (asking) tr("%s? Click again").replace("%s", tr(label)) else tr(label), style = text.copy(color = if (asking) colors.danger else colors.text, fontWeight = if (asking) FontWeight.SemiBold else FontWeight.Normal))
                }
            }
        }
    }
}

// The power menu's entries: their pictures and words.
private val POWER_ENTRIES = mapOf(
    "lock" to (Glyph.LOCK to "Lock"),
    "suspend" to (Glyph.MOON to "Suspend"),
    "hibernate" to (Glyph.HIBERNATE to "Hibernate"),
    "logout" to (Glyph.LOGOUT to "Log out"),
    "reboot" to (Glyph.RESTART to "Restart"),
    "poweroff" to (Glyph.POWER to "Shut down"),
)

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
