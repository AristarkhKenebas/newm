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

/** Wi-Fi from the bar: on and off, the networks in reach (more than five scroll), a password asked for in a window of its own. */
@Composable
internal fun WifiPopup(model: BarModel, popup: Popup, colors: BarColors, text: TextStyle) {
    val nm = model.network
    LaunchedEffect(Unit) { nm.refresh(); if (nm.wifiEnabled) nm.scan() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Header("Wi-Fi", colors, text) {
            if (nm.wifiEnabled) IconButton(Glyph.REFRESH, colors, spinning = nm.scanning) { nm.scan() }
            Spacer(Modifier.width(10.dp))
            Switch(nm.wifiEnabled, colors) { nm.setWifi(it) }
        }
        if (!nm.available) return@Column Note("NetworkManager is not running.", colors, text)
        nm.problem?.let { BasicText(tr(it), style = text.copy(color = colors.danger, fontSize = 12.sp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        if (nm.wired) TileRow(Glyph.WIRED, "Wired connection", colors, text, subtitle = "Connected", active = true)
        if (!nm.wifiEnabled) return@Column Note("Wi-Fi is off.", colors, text)
        if (nm.networks.isEmpty()) return@Column Note("Looking for networks…", colors, text)
        ScrollingRows(colors, nm.networks.size + if (popup.expanded != null) 1 else 0) {
            for (n in nm.networks) {
                val connecting = nm.connecting == n.ssid
                val open = popup.expanded == n.ssid
                TileRow(
                    Glyph.WIFI, n.ssid, colors, text,
                    subtitle = when {
                        connecting -> "Connecting…"
                        n.active -> "Connected"
                        n.known -> "Saved"
                        n.secure -> null
                        else -> "Open network"
                    },
                    active = n.active, level = n.strength / 100f, expanded = open, busy = connecting,
                    onClick = {
                        when {
                            n.active || n.known -> popup.expanded = if (open) null else n.ssid
                            nm.needsPassword(n) -> model.askPassword(n)
                            else -> nm.connect(n)
                        }
                    },
                    trailing = { if (n.secure) GlyphIcon(Glyph.LOCK, colors.dim, 13.dp) },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (n.active) PillButton("Disconnect", colors, text) { nm.disconnect(); popup.expanded = null }
                        else PillButton("Connect", colors, text, PillKind.PRIMARY) { nm.connect(n); popup.expanded = null }
                        PillButton("Forget", colors, text, PillKind.DANGER, glyph = Glyph.TRASH) { nm.forget(n); popup.expanded = null }
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(words: String, colors: BarColors, text: TextStyle) {
    BasicText(tr(words), style = text.copy(color = colors.dim), modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
}

// --- Bluetooth -----------------------------------------------------------------------------------

/** Bluetooth from the bar: on and off, your devices, finding and pairing new ones. */
@Composable
internal fun BluetoothPopup(model: BarModel, popup: Popup, colors: BarColors, text: TextStyle) {
    val bt = model.bluetooth
    LaunchedEffect(Unit) { bt.refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Header("Bluetooth", colors, text) {
            if (bt.powered) IconButton(Glyph.REFRESH, colors, spinning = bt.scanning) { bt.scan() }
            Spacer(Modifier.width(10.dp))
            Switch(bt.powered, colors) { bt.switchPower(it) }
        }
        bt.problem?.let { BasicText(tr(it), style = text.copy(color = colors.danger, fontSize = 12.sp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        if (!bt.powered) return@Column Note("Bluetooth is off.", colors, text)
        val paired = bt.devices.filter { it.paired }
        val found = bt.devices.filter { !it.paired }
        ScrollingRows(colors, bt.devices.size + (if (found.isNotEmpty() || bt.scanning) 1 else 0) + (if (popup.expanded != null) 1 else 0)) {
            if (paired.isNotEmpty()) Section("Your devices", colors, text)
            for (d in paired) DeviceRow(model, popup, d, colors, text)
            if (bt.scanning || found.isNotEmpty()) Section(if (bt.scanning) "Nearby (looking…)" else "Nearby", colors, text)
            for (d in found) DeviceRow(model, popup, d, colors, text)
        }
        if (paired.isEmpty() && found.isEmpty() && !bt.scanning) Note("No devices yet: “Find devices” looks for them.", colors, text)
    }
}

@Composable
private fun DeviceRow(model: BarModel, popup: Popup, d: BluetoothDevice, colors: BarColors, text: TextStyle) {
    val bt = model.bluetooth
    val busy = bt.busy == d.path
    val open = popup.expanded == d.path
    TileRow(
        glyphOfDevice(d.icon), d.name, colors, text,
        subtitle = when {
            busy && d.connected -> "Disconnecting…"
            busy -> if (d.paired) "Connecting…" else "Pairing…"
            d.connected -> d.battery?.let { "Connected · battery $it%" } ?: "Connected"
            d.paired -> null
            else -> "Click to pair"
        },
        active = d.connected, expanded = open, busy = busy,
        onClick = { if (d.paired) popup.expanded = if (open) null else d.path else bt.connect(d) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.connected) PillButton("Disconnect", colors, text) { bt.disconnect(d); popup.expanded = null }
            else PillButton("Connect", colors, text, PillKind.PRIMARY) { bt.connect(d); popup.expanded = null }
            PillButton("Forget", colors, text, PillKind.DANGER, glyph = Glyph.TRASH) { bt.forget(d); popup.expanded = null }
        }
    }
}

// --- sound ---------------------------------------------------------------------------------------

/** Sound from the bar: the volume and the microphone, and which device each uses. */
@Composable
internal fun SoundPopup(model: BarModel, colors: BarColors, text: TextStyle) {
    val sound = model.sound
    LaunchedEffect(Unit) { sound.refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val out = sound.outputs.firstOrNull { it.isDefault } ?: sound.outputs.firstOrNull()
        Header("Sound", colors, text)
        if (out == null) return@Column Note("No sound device", colors, text)
        LevelRow(Glyph.SPEAKER, out.volume, out.muted, colors, text, onMute = { sound.toggleMute(out, true) }) { sound.setVolume(out, it, true) }
        if (sound.outputs.size > 1) {
            Section("Output", colors, text)
            for (d in sound.outputs) DeviceChoice(d.description, outputGlyph(d.description), d.isDefault, colors, text) { sound.use(d, true) }
        }
        val mic = sound.inputs.firstOrNull { it.isDefault } ?: sound.inputs.firstOrNull()
        if (mic != null) {
            Section("Microphone", colors, text)
            LevelRow(Glyph.MIC, mic.volume, mic.muted, colors, text, onMute = { sound.toggleMute(mic, false) }) { sound.setVolume(mic, it, false) }
            if (sound.inputs.size > 1) for (d in sound.inputs) DeviceChoice(d.description, Glyph.MIC, d.isDefault, colors, text) { sound.use(d, false) }
        }
    }
}

// What kind of output a device's name says it is.
private fun outputGlyph(name: String): Glyph {
    val n = name.lowercase()
    return when {
        "headphone" in n || "headset" in n || "buds" in n || "bluez" in n || "airpods" in n -> Glyph.HEADPHONES
        "hdmi" in n || "displayport" in n -> Glyph.MONITOR
        else -> Glyph.SPEAKER_DEVICE
    }
}

// A device to choose: its picture, its name, a check on the one in use.
@Composable
private fun DeviceChoice(name: String, glyph: Glyph, chosen: Boolean, colors: BarColors, text: TextStyle, onClick: () -> Unit) {
    ListRow(name, colors, text, selected = chosen, onClick = onClick,
        leading = { GlyphIcon(glyph, if (chosen) colors.accent else colors.dim, 16.dp) },
        trailing = { if (chosen) GlyphIcon(Glyph.CHECK, colors.accent, 14.dp) })
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
