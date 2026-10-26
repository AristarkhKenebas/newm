package newm.bar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Wi-Fi ---------------------------------------------------------------------------------------

/** Wi-Fi from the bar: on and off, the networks in reach, a password asked for in place. */
@Composable
internal fun WifiPopup(model: BarModel, popup: Popup, colors: BarColors, text: TextStyle) {
    val nm = model.network
    LaunchedEffect(Unit) { nm.refresh(); if (nm.wifiEnabled) nm.scan() }
    Column {
        Header("Wi-Fi", colors, text) {
            if (nm.wifiEnabled) SmallButton("Scan", colors, text) { nm.scan() }
            Spacer(Modifier.width(8.dp))
            Switch(nm.wifiEnabled, colors) { nm.setWifi(it) }
        }
        if (!nm.available) return@Column BasicText(tr("NetworkManager is not running."), style = text.copy(color = colors.dim), modifier = Modifier.padding(6.dp))
        nm.problem?.let { BasicText(tr(it), style = text.copy(color = colors.danger, fontSize = 12.sp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        if (nm.wired) ListRow("Wired connection", colors, text, subtitle = "Connected", selected = true, leading = { GlyphIcon(Glyph.WIRED, colors.accent, 16.dp) })
        if (!nm.wifiEnabled) return@Column BasicText(tr("Wi-Fi is off."), style = text.copy(color = colors.dim), modifier = Modifier.padding(6.dp))
        if (nm.networks.isEmpty()) BasicText(tr("Looking for networks…"), style = text.copy(color = colors.dim), modifier = Modifier.padding(6.dp))
        Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState())) {
            for (n in nm.networks) {
                val connecting = nm.connecting == n.ssid
                val open = popup.expanded == n.ssid
                ListRow(
                    n.ssid, colors, text,
                    subtitle = when {
                        connecting -> "Connecting…"
                        n.active -> "Connected"
                        n.known -> "Saved"
                        n.secure -> null
                        else -> "Open network"
                    },
                    selected = n.active,
                    onClick = {
                        when {
                            n.active || n.known && open -> popup.expanded = if (open) null else n.ssid
                            nm.needsPassword(n) -> { popup.expanded = if (open) null else n.ssid; popup.password = ""; if (!open) model.wantKeyboard() }
                            else -> nm.connect(n)
                        }
                    },
                    leading = { GlyphIcon(Glyph.WIFI, if (n.active) colors.accent else colors.text, 16.dp, level = n.strength / 100f) },
                    trailing = { if (n.secure) GlyphIcon(Glyph.LOCK, colors.dim, 11.dp) },
                )
                if (open) Column(Modifier.padding(start = 39.dp, end = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (n.active || n.known) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (n.active) SmallButton("Disconnect", colors, text) { nm.disconnect(); popup.expanded = null }
                            else SmallButton("Connect", colors, text, accent = true) { nm.connect(n); popup.expanded = null }
                            SmallButton("Forget", colors, text) { nm.forget(n); popup.expanded = null }
                        }
                    } else {
                        PasswordField(popup.password, colors, text, onChange = { popup.password = it },
                            onSubmit = { if (popup.password.length >= 8) { nm.connect(n, popup.password); popup.password = ""; popup.expanded = null } },
                            onCancel = { popup.expanded = null; popup.password = "" })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(if (popup.password.isNotEmpty() && popup.password.length < 8) tr("At least 8 characters") else "", style = text.copy(color = colors.dim, fontSize = 11.sp))
                            Spacer(Modifier.weight(1f))
                            SmallButton("Connect", colors, text, accent = true) {
                                if (popup.password.length >= 8) { nm.connect(n, popup.password); popup.password = ""; popup.expanded = null }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Bluetooth -----------------------------------------------------------------------------------

/** Bluetooth from the bar: on and off, your devices, finding and pairing new ones. */
@Composable
internal fun BluetoothPopup(model: BarModel, popup: Popup, colors: BarColors, text: TextStyle) {
    val bt = model.bluetooth
    LaunchedEffect(Unit) { bt.refresh() }
    Column {
        Header("Bluetooth", colors, text) {
            if (bt.powered) SmallButton(if (bt.scanning) "Stop" else "Find devices", colors, text) { bt.scan() }
            Spacer(Modifier.width(8.dp))
            Switch(bt.powered, colors) { bt.switchPower(it) }
        }
        bt.problem?.let { BasicText(tr(it), style = text.copy(color = colors.danger, fontSize = 12.sp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        if (!bt.powered) return@Column BasicText(tr("Bluetooth is off."), style = text.copy(color = colors.dim), modifier = Modifier.padding(6.dp))
        val paired = bt.devices.filter { it.paired }
        val found = bt.devices.filter { !it.paired }
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            if (paired.isNotEmpty()) Section("Your devices", colors, text)
            for (d in paired) DeviceRow(model, popup, d, colors, text)
            if (bt.scanning || found.isNotEmpty()) Section(if (bt.scanning) "Nearby (looking…)" else "Nearby", colors, text)
            for (d in found) DeviceRow(model, popup, d, colors, text)
            if (paired.isEmpty() && found.isEmpty() && !bt.scanning) BasicText(tr("No devices yet: “Find devices” looks for them."), style = text.copy(color = colors.dim, fontSize = 12.sp), modifier = Modifier.padding(6.dp))
        }
    }
}

@Composable
private fun DeviceRow(model: BarModel, popup: Popup, d: BluetoothDevice, colors: BarColors, text: TextStyle) {
    val bt = model.bluetooth
    val busy = bt.busy == d.path
    val open = popup.expanded == d.path
    var icon by remember(d.icon) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(d.icon) { IconTheme.load(d.icon, TRAY_ICON_PIXELS, null, post = { model.wm.caulis.post { it() } }) { icon = it } }
    ListRow(
        d.name, colors, text,
        subtitle = when {
            busy && d.connected -> "Disconnecting…"
            busy -> if (d.paired) "Connecting…" else "Pairing…"
            d.connected -> "Connected" + (d.battery?.let { " · battery $it%" } ?: "")
            d.paired -> null
            else -> "Click to pair"
        },
        selected = d.connected,
        onClick = {
            when {
                d.paired -> popup.expanded = if (open) null else d.path
                else -> bt.connect(d)
            }
        },
        leading = {
            val picture = icon
            if (picture != null) Image(picture, null, Modifier.size(16.dp), filterQuality = FilterQuality.High) else GlyphIcon(Glyph.BLUETOOTH, colors.text, 16.dp)
        },
    )
    if (open) Row(Modifier.padding(start = 39.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (d.connected) SmallButton("Disconnect", colors, text) { bt.disconnect(d); popup.expanded = null }
        else SmallButton("Connect", colors, text, accent = true) { bt.connect(d); popup.expanded = null }
        SmallButton("Forget", colors, text) { bt.forget(d); popup.expanded = null }
    }
}

// --- sound ---------------------------------------------------------------------------------------

/** Sound from the bar: the volume and the microphone, which device each uses. */
@Composable
internal fun SoundPopup(model: BarModel, colors: BarColors, text: TextStyle) {
    val sound = model.sound
    LaunchedEffect(Unit) { sound.refresh() }
    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        val out = sound.outputs.firstOrNull { it.isDefault } ?: sound.outputs.firstOrNull()
        Header("Sound", colors, text)
        if (out == null) {
            BasicText(tr("No sound device"), style = text.copy(color = colors.dim), modifier = Modifier.padding(6.dp))
        } else {
            LevelRow(Glyph.SPEAKER, out.volume, out.muted, colors, text, onMute = { sound.toggleMute(out, true) }) { sound.setVolume(out, it, true) }
            if (sound.outputs.size > 1) {
                Section("Output", colors, text)
                for (d in sound.outputs) ListRow(d.description, colors, text, selected = d.isDefault, onClick = { sound.use(d, true) },
                    leading = { if (d.isDefault) GlyphIcon(Glyph.CHECK, colors.accent, 12.dp) })
            }
        }
        val mic = sound.inputs.firstOrNull { it.isDefault } ?: sound.inputs.firstOrNull()
        if (mic != null) {
            Section("Microphone", colors, text)
            LevelRow(Glyph.MIC, mic.volume, mic.muted, colors, text, onMute = { sound.toggleMute(mic, false) }) { sound.setVolume(mic, it, false) }
            if (sound.inputs.size > 1) for (d in sound.inputs) ListRow(d.description, colors, text, selected = d.isDefault, onClick = { sound.use(d, false) },
                leading = { if (d.isDefault) GlyphIcon(Glyph.CHECK, colors.accent, 12.dp) })
        }
    }
}

// A level: a Pixel's wide slider, its icon inside (a click on it mutes), the percent at the right.
@Composable
private fun LevelRow(glyph: Glyph, percent: Int, muted: Boolean, colors: BarColors, text: TextStyle, onMute: (() -> Unit)?, onSet: (Int) -> Unit) {
    LevelSlider(glyph, percent / 100f, colors, text, if (muted) tr("off") else "$percent%", Modifier.padding(vertical = 3.dp), dim = muted, onIcon = onMute) { level ->
        onSet(Math.round(level * 100))
    }
}

// --- battery and power profiles ----------------------------------------------------------------------

@Composable
internal fun BatteryAndPowerPopup(model: BarModel, colors: BarColors, text: TextStyle) {
    val b = model.status.battery
    val profiles = model.powerProfiles
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (b != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
            GlyphIcon(Glyph.BATTERY, colors.text, 38.dp, level = b.percent / 100f, bolt = b.plugged,
                accent = if (b.charging || b.full) colors.accent else if (b.percent <= 20) colors.warning else colors.text)
            Spacer(Modifier.width(12.dp))
            Column {
                BasicText(tr("${b.percent}%"), style = text.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold))
                val minutes = b.minutesLeft
                BasicText(tr(when {
                    b.full -> "Fully charged"
                    b.charging && minutes != null -> "Charging · full in ${hoursOf(minutes)}"
                    b.charging -> "Charging"
                    b.plugged -> "Plugged in · held at its charge limit"
                    minutes != null -> "${hoursOf(minutes)} left"
                    else -> "On battery"
                }), style = text.copy(color = colors.dim, fontSize = 12.sp))
            }
        }
        model.status.brightness?.let { level ->
            LevelRow(Glyph.BRIGHTNESS, level, false, colors, text, onMute = null) { setBrightness(model, it / 100f) }
        }
        if (profiles.profiles.isNotEmpty()) {
            Section("Power mode", colors, text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (p in profiles.profiles) {
                    val (glyph, name) = when (p) {
                        "power-saver" -> Glyph.LEAF to "Power saver"
                        "balanced" -> Glyph.BALANCE to "Balanced"
                        "performance" -> Glyph.BOLT to "Performance"
                        else -> Glyph.POWER to p
                    }
                    ChoiceTile(glyph, name, p == profiles.active, colors, text, Modifier.weight(1f)) { profiles.use(p) }
                }
            }
        }
        CaffeineRow(model, colors, text)
    }
}

// Caffeine: the screen stays on for a while (or until turned off), whatever the energy settings say.
@Composable
private fun CaffeineRow(model: BarModel, colors: BarColors, text: TextStyle) {
    val wm = model.wm
    val until = wm.caffeineUntil
    // The minutes left, looked at again every few seconds while it is on.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (until != null) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(5000)
        }
    }
    val on = until != null && until > now
    Section("Caffeine", colors, text)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(Glyph.COFFEE, if (on) colors.accent else colors.text, 18.dp, level = if (on) 1f else 0f)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(tr("The screen stays on"), style = text)
            BasicText(when {
                !on -> tr("Off")
                until == Long.MAX_VALUE -> tr("On until turned off")
                else -> tr("On · %s more").replace("%s", hoursOf(((until - now) / 60_000L).toInt() + 1))
            }, style = text.copy(color = colors.dim, fontSize = 11.sp))
        }
        Switch(on, colors) { wm.caffeine(if (it) 0 else null) }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val left = if (on && until != Long.MAX_VALUE) ((until!! - now) / 60_000L).toInt() + 1 else -1
        for ((minutes, label) in listOf(30 to "30 min", 60 to "1 h", 120 to "2 h", 0 to "∞")) {
            // The chosen one: the time asked for (what is left is at most that, more than the shorter one).
            val chosen = on && if (minutes == 0) until == Long.MAX_VALUE else left in (minutes / 2 + 1)..minutes && (minutes != 30 || left <= 30)
            ChoiceChip(label, chosen, colors, text, Modifier.weight(1f)) { wm.caffeine(if (chosen) null else minutes) }
        }
    }
}

internal fun hoursOf(minutes: Int): String = tr(if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min")

// --- colours ---------------------------------------------------------------------------------

/** The colours: made from the wallpaper in one of five styles (as a Pixel's), or the classic Nord ones; a palette's code to copy. */
@Composable
internal fun ColorsPopup(model: BarModel, colors: BarColors, text: TextStyle) {
    val seed = model.wm.wallpaperSeed
    val (source, style) = NewmColors.setting()
    val dark = model.scheme == dev.caulis.core.appearance.ColorScheme.DARK
    Column {
        Header("Colors", colors, text) {
            model.palette?.let { p -> SmallButton("Copy code", colors, text) { model.wm.caulis.desktop.copyText?.invoke(p.code); model.toast("Palette copied: ${p.code}") } }
        }
        val styles = listOf(
            dev.caulis.core.appearance.PaletteStyle.TONAL_SPOT to "Tonal",
            dev.caulis.core.appearance.PaletteStyle.VIBRANT to "Vibrant",
            dev.caulis.core.appearance.PaletteStyle.EXPRESSIVE to "Expressive",
            dev.caulis.core.appearance.PaletteStyle.NEUTRAL to "Neutral",
            dev.caulis.core.appearance.PaletteStyle.MONOCHROME to "Monochrome",
        )
        for ((s, name) in styles) {
            val palette = seed?.let { dev.caulis.core.appearance.Palette(it, s) }
            ListRow(name, colors, text, subtitle = if (seed == null) "The wallpaper is not read yet" else "From the wallpaper",
                selected = source == "wallpaper" && style == s, enabled = seed != null,
                onClick = { NewmColors.choose("wallpaper:${s.name.lowercase()}"); model.refreshPalette() },
                leading = { palette?.let { Swatches(it.scheme(dark)) } })
        }
        ListRow("Classic", colors, text, subtitle = "newm's Nord colours", selected = source == "nord" || source == "classic",
            onClick = { NewmColors.choose("nord"); model.refreshPalette() },
            leading = { Swatches(null) })
    }
}

// Four colours of a scheme (or Nord's) in a little square.
@Composable
private fun Swatches(scheme: dev.caulis.core.appearance.PaletteScheme?) {
    fun c(x: dev.caulis.render.Color) = androidx.compose.ui.graphics.Color(x.red, x.green, x.blue)
    val four = scheme?.let { listOf(c(it.primary), c(it.secondary), c(it.tertiary), c(it.surfaceContainer)) }
        ?: listOf(androidx.compose.ui.graphics.Color(0xFF88C0D0), androidx.compose.ui.graphics.Color(0xFF5E81AC), androidx.compose.ui.graphics.Color(0xFFB48EAD), androidx.compose.ui.graphics.Color(0xFFECEFF4))
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val h = size.width / 2
        val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        four.forEachIndexed { k, color ->
            drawRoundRect(color, androidx.compose.ui.geometry.Offset((k % 2) * h, (k / 2) * h), androidx.compose.ui.geometry.Size(h - 1, h - 1), r)
        }
    }
}
