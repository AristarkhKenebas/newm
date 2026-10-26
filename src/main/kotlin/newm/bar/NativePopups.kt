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
        if (!nm.available) return@Column BasicText("NetworkManager is not running.", style = text.copy(color = colors.dim), modifier = Modifier.padding(6.dp))
        nm.problem?.let { BasicText(it, style = text.copy(color = colors.danger, fontSize = 12.sp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        if (nm.wired) ListRow("Wired connection", colors, text, subtitle = "Connected", selected = true, leading = { GlyphIcon(Glyph.WIRED, colors.accent, 16.dp) })
        if (!nm.wifiEnabled) return@Column BasicText("Wi-Fi is off.", style = text.copy(color = colors.dim), modifier = Modifier.padding(6.dp))
        if (nm.networks.isEmpty()) BasicText("Looking for networks…", style = text.copy(color = colors.dim), modifier = Modifier.padding(6.dp))
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
                            BasicText(if (popup.password.isNotEmpty() && popup.password.length < 8) "At least 8 characters" else "", style = text.copy(color = colors.dim, fontSize = 11.sp))
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
        bt.problem?.let { BasicText(it, style = text.copy(color = colors.danger, fontSize = 12.sp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        if (!bt.powered) return@Column BasicText("Bluetooth is off.", style = text.copy(color = colors.dim), modifier = Modifier.padding(6.dp))
        val paired = bt.devices.filter { it.paired }
        val found = bt.devices.filter { !it.paired }
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            if (paired.isNotEmpty()) Section("Your devices", colors, text)
            for (d in paired) DeviceRow(model, popup, d, colors, text)
            if (bt.scanning || found.isNotEmpty()) Section(if (bt.scanning) "Nearby (looking…)" else "Nearby", colors, text)
            for (d in found) DeviceRow(model, popup, d, colors, text)
            if (paired.isEmpty() && found.isEmpty() && !bt.scanning) BasicText("No devices yet: “Find devices” looks for them.", style = text.copy(color = colors.dim, fontSize = 12.sp), modifier = Modifier.padding(6.dp))
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

/** Sound from the bar: the volume and the microphone, which device each uses, the screen's brightness. */
@Composable
internal fun SoundPopup(model: BarModel, colors: BarColors, text: TextStyle) {
    val sound = model.sound
    LaunchedEffect(Unit) { sound.refresh() }
    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        val out = sound.outputs.firstOrNull { it.isDefault } ?: sound.outputs.firstOrNull()
        Header("Sound", colors, text)
        if (out == null) {
            BasicText("No sound device", style = text.copy(color = colors.dim), modifier = Modifier.padding(6.dp))
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
        model.status.brightness?.let { b ->
            Section("Screen", colors, text)
            LevelRow(Glyph.BRIGHTNESS, b, false, colors, text, onMute = null) { setBrightness(model, it / 100f) }
        }
    }
}

// A level: its icon (a click mutes), a slider, the percent.
@Composable
private fun LevelRow(glyph: Glyph, percent: Int, muted: Boolean, colors: BarColors, text: TextStyle, onMute: (() -> Unit)?, onSet: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(28.dp).then(if (onMute != null) Modifier.onClick(onClick = onMute) else Modifier), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, if (muted) colors.dim else colors.text, 17.dp, level = percent / 100f, slash = muted)
        }
        Spacer(Modifier.width(8.dp))
        Slider(colors, percent / 100f, Modifier.weight(1f)) { level -> onSet((level * 100).toInt()) }
        Spacer(Modifier.width(8.dp))
        BasicText(if (muted) "off" else "$percent%", style = text.copy(color = colors.dim, fontSize = 12.sp), modifier = Modifier.width(38.dp))
    }
}

// --- battery and power profiles ----------------------------------------------------------------------

@Composable
internal fun BatteryAndPowerPopup(model: BarModel, colors: BarColors, text: TextStyle) {
    val b = model.status.battery
    val profiles = model.powerProfiles
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (b != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
            GlyphIcon(Glyph.BATTERY, colors.text, 38.dp, level = b.percent / 100f, bolt = b.charging,
                accent = if (b.charging || b.full) colors.accent else if (b.percent <= 20) colors.warning else colors.text)
            Spacer(Modifier.width(12.dp))
            Column {
                BasicText("${b.percent}%", style = text.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold))
                val minutes = b.minutesLeft
                BasicText(when {
                    b.full -> "Fully charged"
                    b.charging && minutes != null -> "Charging · full in ${hoursOf(minutes)}"
                    b.charging -> "Charging"
                    minutes != null -> "${hoursOf(minutes)} left"
                    else -> "On battery"
                }, style = text.copy(color = colors.dim, fontSize = 12.sp))
            }
        }
        if (profiles.profiles.isNotEmpty()) {
            Section("Power mode", colors, text)
            for (p in profiles.profiles) {
                val name = when (p) {
                    "power-saver" -> "Power saver"
                    "balanced" -> "Balanced"
                    "performance" -> "Performance"
                    else -> p
                }
                ListRow(name, colors, text, selected = p == profiles.active, onClick = { profiles.use(p) },
                    leading = { if (p == profiles.active) GlyphIcon(Glyph.CHECK, colors.accent, 12.dp) })
            }
        }
    }
}

internal fun hoursOf(minutes: Int): String = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
