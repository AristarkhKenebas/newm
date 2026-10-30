package newm.bar

import androidx.compose.foundation.interaction.collectIsHoveredAsState

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
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
    // (Not while drawn ahead at the start: no scan at every login.)
    val warming = newm.LocalWarmingUp.current
    LaunchedEffect(Unit) { if (!warming) { nm.refresh(); if (nm.wifiEnabled && confScanOnOpen()) nm.scan() } }
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
                    Glyph.WIFI, verbatim(n.ssid), colors, text,
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
    val warming = newm.LocalWarmingUp.current
    LaunchedEffect(Unit) { if (!warming) bt.refresh() }
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
        if (paired.isEmpty() && found.isEmpty() && !bt.scanning) Note("No devices yet: the refresh button above looks for them.", colors, text)
    }
}

@Composable
private fun DeviceRow(model: BarModel, popup: Popup, d: BluetoothDevice, colors: BarColors, text: TextStyle) {
    val bt = model.bluetooth
    val busy = bt.busy == d.path
    val open = popup.expanded == d.path
    TileRow(
        glyphOfDevice(d.icon), verbatim(d.name), colors, text,
        subtitle = when {
            busy && d.connected -> "Disconnecting…"
            busy -> if (d.paired) "Connecting…" else "Pairing…"
            d.connected -> d.battery?.let { "Connected · battery ${verbatim("$it%")}" } ?: "Connected"
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

// sound { maxVolume = 150 }: how loud the volume's slider goes (100..150; past 100 the sound is amplified).
private val confMaxVolume = newm.configuredValue("sound.max_volume", 150)

/** How loud the output may be set (percent, 100..150): the slider's end, and the wheel's and keys' too. */
internal fun maxVolume(): Int = confMaxVolume().coerceIn(100, 150)

// --- sound ---------------------------------------------------------------------------------------

/** Sound from the bar: the volume and the device it plays on; the microphone and the device it hears with. */
@Composable
internal fun SoundPopup(model: BarModel, colors: BarColors, text: TextStyle) {
    val sound = model.sound
    val warming = newm.LocalWarmingUp.current
    LaunchedEffect(Unit) { if (!warming) sound.refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val out = sound.outputs.firstOrNull { it.isDefault } ?: sound.outputs.firstOrNull()
        Header("Sound", colors, text)
        if (out == null) return@Column Note("No sound device", colors, text)
        LevelRow(Glyph.SPEAKER, out.volume, out.muted, colors, text, onMute = { sound.toggleMute(out, true) }, max = maxVolume()) { sound.setVolume(out, it, true) }
        DevicePicker(out, sound.outputs, { it.description }, { outputGlyph(it.description) }, { it.isDefault }, colors, text) { sound.use(it, true) }
        val mic = sound.inputs.firstOrNull { it.isDefault } ?: sound.inputs.firstOrNull()
        if (mic != null) {
            Section("Microphone", colors, text)
            LevelRow(Glyph.MIC, mic.volume, mic.muted, colors, text, onMute = { sound.toggleMute(mic, false) }) { sound.setVolume(mic, it, false) }
            DevicePicker(mic, sound.inputs, { it.description }, { inputGlyph(it.description) }, { it.isDefault }, colors, text) { sound.use(it, false) }
        }
    }
}

// What kind of input a device's name says it is.
private fun inputGlyph(name: String): Glyph {
    val n = name.lowercase()
    return when {
        "headset" in n || "buds" in n || "bluez" in n || "headphone" in n -> Glyph.HEADPHONES
        "camera" in n || "webcam" in n -> Glyph.CAMERA
        else -> Glyph.MIC
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
    ListRow(verbatim(name), colors, text, selected = chosen, onClick = onClick,
        leading = { GlyphIcon(glyph, if (chosen) colors.accent else colors.dim, 16.dp) },
        trailing = { if (chosen) GlyphIcon(Glyph.CHECK, colors.accent, 14.dp) })
}

// A level: a Pixel's wide slider, its icon inside (a click on it mutes), the percent at the right.
@Composable
private fun LevelRow(glyph: Glyph, percent: Int, muted: Boolean, colors: BarColors, text: TextStyle, onMute: (() -> Unit)?, max: Int = 100, onSet: (Int) -> Unit) {
    var live by remember { mutableStateOf<Int?>(null) }
    LevelSlider(glyph, percent / 100f, colors, text, "${live ?: percent}%", Modifier.padding(vertical = 2.dp), dim = muted, max = max / 100f, onIcon = onMute) { level ->
        val p = Math.round(level * 100)
        live = p
        onSet(p)
    }
}

// --- battery and power profiles ----------------------------------------------------------------------

@Composable
internal fun BatteryAndPowerPopup(model: BarModel, popup: Popup, colors: BarColors, text: TextStyle) {
    val b = model.status.battery
    val profiles = model.powerProfiles
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (b != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
            GlyphIcon(Glyph.BATTERY, colors.text, 38.dp, level = b.percent / 100f, bolt = b.plugged,
                accent = if (b.charging || b.full) colors.accent else if (b.percent <= confBatteryLow()) colors.warning else colors.text)
            Spacer(Modifier.width(12.dp))
            Column {
                BasicText(tr("${b.percent}%"), style = text.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold))
                BasicText(batteryWords(b), style = text.copy(color = colors.dim, fontSize = 12.sp))
            }
        }
        if (b != null) Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(tr("Percent at the bar"), style = text, modifier = Modifier.weight(1f))
            Switch(model.batteryPercent ?: popup.bar.showPercent, colors) { model.showBatteryPercent(it) }
        }
        if (profiles.profiles.isNotEmpty()) {
            Section("Power mode", colors, text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (p in profiles.profiles) {
                    val (glyph, name) = when (p) {
                        "power-saver" -> Glyph.LEAF to "Power saver"
                        "balanced" -> Glyph.GAUGE to "Balanced"
                        "performance" -> Glyph.BOLT to "Performance"
                        else -> Glyph.POWER to verbatim(p)
                    }
                    ChoiceTile(glyph, name, p == profiles.active, colors, text, Modifier.weight(1f)) { profiles.use(p) }
                }
            }
        }
    }
}

// --- quick actions --------------------------------------------------------------------------

/**
 * Quick settings, as Android 16 has them: the brightness, tall, on top; wide tiles (Wi-Fi,
 * Bluetooth, caffeine: the icon switches it, the words open more) and small round ones (dark
 * look, a screenshot, the clipboard, colours, keys, do not disturb, power saving). On: filled with the accent.
 * Which tiles, in which order, and tiles of one's own: quickSettings { tiles; tile(...) { } }.
 * Two wide ones make a row; a wide one alone shares its row with up to two small ones; other
 * small ones go five to a row.
 */
@Composable
internal fun QuickPopup(model: BarModel, popup: Popup, colors: BarColors, text: TextStyle) {
    val wm = model.wm
    val until = wm.caffeineUntil
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (until != null) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(5000)
        }
    }
    val warming = newm.LocalWarmingUp.current
    LaunchedEffect(Unit) { if (!warming) { model.network.refresh(); model.bluetooth.refresh(); model.powerProfiles.refresh() } }
    val caffeine = until != null && until > now
    // (The same test as the bar's: what the tile shows is what everything shows.)
    val dark = wm.caulis.colorSchemeNow == dev.caulis.core.appearance.ColorScheme.DARK
    val nm = model.network
    val bt = model.bluetooth
    val profiles = model.powerProfiles
    fun closeThen(action: () -> Unit) {
        model.close()
        // (A moment later: not the menu in a screenshot, nor its keys.)
        wm.caulis.after(220) { action() }
    }
    // Tiles of one's own: whether each is on (a command's answer comes later), asked again after a click.
    val custom = confQuickCustom()
    var asked by remember { mutableStateOf(0) }
    val commandSays = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(asked) {
        if (warming) return@LaunchedEffect // (no commands run while drawn ahead)
        for ((name, spec) in custom) {
            val check = (spec as? Map<*, *>)?.get("is_on") as? String ?: continue
            inBackground { val on = commandSucceeds(check); wm.caulis.post { commandSays[name] = on } }
        }
    }
    fun customOn(name: String): Boolean {
        @Suppress("UNUSED_VARIABLE") val again = asked
        return when (val check = (custom[name] as? Map<*, *>)?.get("is_on")) {
            null -> false
            is String -> commandSays[name] == true
            else -> runHook(check, null, { null }, "quick_settings.$name.is_on") == true
        }
    }
    fun customClick(name: String) {
        runAction((custom[name] as? Map<*, *>)?.get("on_click"), wm, "quick_settings.$name.on_click")
        asked++
        // (A command takes a moment: asked again a little later too.)
        wm.caulis.after(600) { asked++ }
    }
    val tiles = confQuickTiles().filter { name ->
        when (name) {
            "power_saver" -> "power-saver" in profiles.profiles
            in QUICK_TILES -> true
            else -> name in custom
        }
    }
    fun wide(name: String) = name in setOf("wifi", "bluetooth", "caffeine") || (name !in QUICK_TILES && (custom[name] as? Map<*, *>)?.get("wide") == true)
    // The rows: two wide tiles; a wide one and up to two small ones; up to five small ones.
    val rows = ArrayList<List<String>>()
    var i = 0
    while (i < tiles.size) {
        val row = ArrayList<String>()
        if (wide(tiles[i])) {
            row += tiles[i++]
            if (i < tiles.size && wide(tiles[i])) row += tiles[i++]
            else while (i < tiles.size && !wide(tiles[i]) && row.size < 3) row += tiles[i++]
        } else while (i < tiles.size && !wide(tiles[i]) && row.size < 5) row += tiles[i++]
        rows += row
    }
    Column(Modifier.animateContentSize(newm.NewmAnimations.spec("popupsResize", 260, newm.NewmAnimations.FAST_OUT_SLOW_IN)),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The screen's brightness (here, not with the battery: a computer without one has it too).
        model.status.brightness?.let { level ->
            // (The same slider as the volume's: one look for all of them.)
            LevelSlider(Glyph.BRIGHTNESS, level / 100f, colors, text, "$level%", Modifier.padding(vertical = 2.dp)) { setBrightness(model, it) }
            Spacer(Modifier.height(2.dp))
        }
        // What does not work now is said here (the bar's sound shows it too, dimmed).
        model.status.volumeUnavailable?.let { why ->
            BasicText(tr("Sound is unavailable") + ": " + why, style = text.copy(color = colors.dim, fontSize = (text.fontSize.value - 1).sp), maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        for (row in rows) {
            val withWide = row.any(::wide)
            // (Beside a wide tile small ones are half as wide and as tall as it.)
            val smallWeight = if (withWide) 0.5f else 1f
            val smallHeight = if (withWide) 56.dp else 48.dp
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (name in row) when (name) {
                    "wifi" -> {
                        val ssid = nm.networks.firstOrNull { it.active }?.ssid
                        WideTile(model, if (nm.wifiEnabled) "wifi" else "wifi_off", "Wi-Fi", when {
                            nm.wired -> tr("Wired connection")
                            !nm.wifiEnabled -> tr("Off")
                            ssid != null -> ssid
                            else -> tr("Not connected")
                        }, nm.wifiEnabled, colors, text, Modifier.weight(1f), onOpen = { model.toggle(PopupKind.NETWORK, popup.bar, popup.anchorX) }) { nm.setWifi(!nm.wifiEnabled) }
                    }
                    "bluetooth" -> {
                        val device = bt.devices.firstOrNull { it.connected }?.name
                        WideTile(model, if (bt.powered) "bluetooth" else "bluetooth_disabled", "Bluetooth", when {
                            !bt.powered -> tr("Off")
                            device != null -> device
                            else -> tr("On")
                        }, bt.powered, colors, text, Modifier.weight(1f), onOpen = { model.toggle(PopupKind.BLUETOOTH, popup.bar, popup.anchorX) }) { bt.switchPower(!bt.powered) }
                    }
                    "caffeine" -> WideTile(model, "coffee", "Caffeine", when {
                        // (Off, but a program keeps the screen on: which one.)
                        !caffeine && wm.idleInhibit.reasons.isNotEmpty() -> newm.idleReasonsText(wm.idleInhibit.reasons)
                        !caffeine -> tr("Off")
                        until == Long.MAX_VALUE -> tr("On")
                        else -> tr("%s more").replace("%s", hoursOf(((until - now) / 60_000L).toInt() + 1))
                    }, caffeine, colors, text, Modifier.weight(1f), onOpen = { popup.expanded = if (popup.expanded == "caffeine") null else "caffeine" }) { wm.caffeine(if (caffeine) null else 0) }
                    "dark" -> SmallTile(model, if (dark) "dark_mode" else "light_mode", dark, colors, Modifier.weight(smallWeight), height = smallHeight) {
                        newm.ColorChoice.choose(wm.caulis, if (dark) dev.caulis.core.appearance.ColorScheme.LIGHT else dev.caulis.core.appearance.ColorScheme.DARK)
                        wm.caulis.applySettings()
                    }
                    "screenshot" -> SmallTile(model, "screenshot_region", false, colors, Modifier.weight(smallWeight), height = smallHeight) { closeThen { wm.screenshot() } }
                    "clipboard" -> SmallTile(model, "content_paste", false, colors, Modifier.weight(smallWeight), height = smallHeight) { closeThen { wm.toggleClipboard() } }
                    "colors" -> SmallTile(model, "palette", false, colors, Modifier.weight(smallWeight), height = smallHeight) { model.toggle(PopupKind.COLORS, popup.bar, popup.anchorX) }
                    "keys" -> SmallTile(model, "keyboard", false, colors, Modifier.weight(smallWeight), height = smallHeight) { closeThen { wm.toggleKeyHelp() } }
                    // Do not disturb: no notifications' cards (the history behind the bell keeps them).
                    "dnd" -> SmallTile(model, "do_not_disturb_on", model.notifications.doNotDisturb, colors, Modifier.weight(smallWeight), height = smallHeight) {
                        model.notifications.setDoNotDisturb(!model.notifications.doNotDisturb)
                    }
                    // Power saving, and back to the profile it came from.
                    "power_saver" -> {
                        val saving = profiles.active == "power-saver"
                        SmallTile(model, "energy_savings_leaf", saving, colors, Modifier.weight(smallWeight), height = smallHeight) { profiles.toggleSaver() }
                    }
                    else -> (custom[name] as? Map<*, *>)?.let { spec ->
                        val icon = spec["icon"] as? String ?: "settings"
                        val on = customOn(name)
                        if (wide(name)) WideTile(model, icon, (spec["label"] as? String) ?: name, tr(if (on) "On" else "Off"), on, colors, text, Modifier.weight(1f)) { customClick(name) }
                        else SmallTile(model, icon, on, colors, Modifier.weight(smallWeight), height = smallHeight) { customClick(name) }
                    }
                }
            }
            // The caffeine's times, below its row.
            if ("caffeine" in row && popup.expanded == "caffeine") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val left = if (caffeine && until != Long.MAX_VALUE) ((until - now) / 60_000L).toInt() + 1 else -1
                // caffeine { choices }: minutes, 0 for until turned off; the one chosen is the shortest that still covers what is left.
                val choices = confCaffeineChoices().filter { it >= 0 }.distinct()
                val timed = choices.filter { it > 0 }.sorted()
                for (minutes in choices) {
                    val shorter = timed.lastOrNull { it < minutes } ?: 0
                    val chosen = caffeine && if (minutes == 0) until == Long.MAX_VALUE else left in (shorter + 1)..minutes
                    ChoiceChip(caffeineLabel(minutes), chosen, colors, text, Modifier.weight(1f)) { wm.caffeine(if (chosen) null else minutes) }
                }
            }
        }
    }
}

// A caffeine time's words: "30 min", "1 h", "∞" (until turned off).
private fun caffeineLabel(minutes: Int): String = when {
    minutes <= 0 -> "∞"
    minutes % 60 == 0 -> "${minutes / 60} h"
    minutes > 60 -> "${minutes / 60} h ${minutes % 60} min"
    else -> "$minutes min"
}

// A wide tile: its icon in a rounded square (the switch), its name and state (more, on a click).
@Composable
private fun WideTile(model: BarModel, symbol: String, title: String, state: String, on: Boolean, colors: BarColors, text: TextStyle, modifier: Modifier,
                     onOpen: (() -> Unit)? = null, onToggle: () -> Unit) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by androidx.compose.animation.animateColorAsState(
        when {
            on -> colors.accent.copy(alpha = if (hovered) 0.26f else 0.18f)
            hovered -> colors.text.copy(alpha = 0.11f)
            else -> colors.text.copy(alpha = 0.07f)
        }, newm.NewmAnimations.spec("toggles", 160),
    )
    val square by androidx.compose.animation.animateColorAsState(if (on) colors.accent else colors.text.copy(alpha = 0.10f), newm.NewmAnimations.spec("toggles", 160))
    // (Square-ish with soft corners, as a phone's quick settings.)
    Row(modifier.height(56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(bg).hoverable(source)
        .onClick(onClick = onOpen ?: onToggle).padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(square)
            .onClick(onClick = onToggle), contentAlignment = Alignment.Center) {
            NamedIcon(symbol, if (on) onAccent(colors) else colors.text, 20.dp, model)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            BasicText(tr(title), style = text.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            BasicText(state, style = text.copy(color = colors.dim, fontSize = 11.sp), maxLines = 1, softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

// A small tile: a square with soft corners and its icon alone, filled with the accent when on.
@Composable
private fun SmallTile(model: BarModel, symbol: String, on: Boolean, colors: BarColors, modifier: Modifier, height: androidx.compose.ui.unit.Dp = 48.dp, onClick: () -> Unit) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by androidx.compose.animation.animateColorAsState(
        when {
            on -> if (hovered) colors.accent.copy(alpha = 0.88f) else colors.accent
            hovered -> colors.text.copy(alpha = 0.11f)
            else -> colors.text.copy(alpha = 0.07f)
        }, newm.NewmAnimations.spec("toggles", 160),
    )
    androidx.compose.foundation.layout.Box(modifier.height(height).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(bg)
        .hoverable(source).onClick(onClick = onClick), contentAlignment = Alignment.Center) {
        NamedIcon(symbol, if (on) onAccent(colors) else colors.text, 22.dp, model)
    }
}

/** What the battery does, in words (translated): charging and how long, held, how long it lasts. */
internal fun batteryWords(b: Battery): String {
    val minutes = b.minutesLeft
    return when {
        b.full -> tr("Fully charged")
        b.charging && minutes != null -> tr("Charging · full in %s").replace("%s", hoursOf(minutes))
        b.charging -> tr("Charging")
        b.plugged -> tr("Plugged in · held at its charge limit")
        minutes != null -> tr("%s left").replace("%s", hoursOf(minutes))
        else -> tr("On battery")
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
            model.palette?.let { p -> SmallButton("Copy code", colors, text) { model.wm.caulis.desktop.copyText?.invoke(p.code); model.toast("Palette copied: ${verbatim(p.code)}") } }
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
