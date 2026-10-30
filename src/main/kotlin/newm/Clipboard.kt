package newm

import newm.bar.ListRow
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.draw.drawBehind
import newm.bar.IconButton
import newm.bar.ChoiceChip
import newm.bar.PillKind
import newm.bar.PillButton
import newm.bar.GlyphIcon
import newm.bar.Glyph
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
private val confPictures = configuredValue("clipboard.pictures", true)
private val confFiles = configuredValue("clipboard.files", true)
private val confKeepPins = configuredValue("clipboard.keep_pins", true)
private val confIgnore = configuredValue("clipboard.ignore", listOf("keepassxc", "bitwarden", "1password"))
private val confMaxPictureMb = configuredValue("clipboard.max_picture_mb", 16)
// clipboard { picturesTotalMb = 64 }: all pictures kept, in all (their PNGs, in newm's memory: a
// photo's PNG may be many times its JPEG); the oldest unpinned ones go first.
private val confPicturesTotalMb = configuredValue("clipboard.pictures_total_mb", 64)
private val confMaxText = configuredValue("clipboard.max_text", 200_000)
// clipboard { clearAtOnce = true; onChoose = "copy" | "paste"; pasteKey = "C-v"; terminalPasteKey = "C-S-v"; width; height; top; pinKey = "C-p" }
private val confClearAtOnce = configuredValue("clipboard.clear_at_once", true)
private val confOnChoose = configuredValue("clipboard.on_choose", "copy")
private val confPasteKey = configuredValue("clipboard.paste_key", "C-v")
private val confTerminalPasteKey = configuredValue("clipboard.terminal_paste_key", "C-S-v")
private val confWidth = configuredValue("clipboard.width", 980)
private val confHeight = configuredValue("clipboard.height", 620)
private val confTop = configuredValue("clipboard.top", 0.13)
private val confPinKey = configuredValue("clipboard.pin_key", "C-p")
// clipboard { pasteOnLatinLayout = true }: the paste keys in the first Latin layout (see sendKeys).
private val confPasteOnLatinLayout = configuredValue("clipboard.paste_on_latin_layout", true)

// Keys by their xkb names (as in keys { }) as evdev codes, for pasting: the modifiers, letters, Insert.
private val EVDEV_MODIFIERS = mapOf("C" to 29, "S" to 42, "A" to 56, "1" to 56, "L" to 125)
private const val EVDEV_LETTERS = "qwertyuiop" // 16..25
private const val EVDEV_LETTERS2 = "asdfghjkl" // 30..38
private const val EVDEV_LETTERS3 = "zxcvbnm" // 44..50

/** The evdev codes of a combination in newm's notation ("C-S-v": Ctrl, Shift, V), modifiers first; null if a key is not known. */
internal fun evdevCodes(combo: String): List<Int>? {
    val parts = combo.trim().split('-')
    val mods = parts.dropLast(1).map { EVDEV_MODIFIERS[it] ?: return null }
    var name = parts.last()
    val shift = name.length == 1 && name[0].isUpperCase()
    name = name.lowercase()
    val key = when {
        name.length == 1 && name[0] in EVDEV_LETTERS -> 16 + EVDEV_LETTERS.indexOf(name[0])
        name.length == 1 && name[0] in EVDEV_LETTERS2 -> 30 + EVDEV_LETTERS2.indexOf(name[0])
        name.length == 1 && name[0] in EVDEV_LETTERS3 -> 44 + EVDEV_LETTERS3.indexOf(name[0])
        name == "insert" -> 110
        name == "return" -> 28
        else -> return null
    }
    return (mods + (if (shift && 42 !in mods) listOf(42) else emptyList()) + key)
}

/**
 * Presses and lets go [combo] (newm's notation: "C-S-v") as a keyboard would, by the keys' codes.
 * With [onLatinLayout], while the layout in use is of another script (Russian, Greek) the keys go
 * in the first Latin layout ("us" of "us,ru"), and the layout in use comes back right after:
 * programs that match their shortcuts in the layout in use only (Alacritty) paste then too. The
 * letter is the key that types it in that layout (on Dvorak V is not where a US keyboard has it).
 * False if [combo] cannot be sent.
 */
internal fun sendKeys(caulis: dev.caulis.wm.Caulis, combo: String, onLatinLayout: Boolean): Boolean {
    val codes = evdevCodes(combo)?.toMutableList() ?: return false
    val now = caulis.keyboardLayoutNow
    val layout = if (onLatinLayout) latinLayout(caulis, now) else now
    val letter = combo.trim().substringAfterLast('-').lowercase().singleOrNull()?.takeIf { it in 'a'..'z' }
    if (letter != null) keyTyping(caulis, letter, layout)?.let { codes[codes.size - 1] = it }
    if (layout != now) {
        log.trace { "The paste keys go in layout $layout (layout $now is not Latin)" }
        caulis.switchKeyboardLayout(layout)
    }
    try {
        codes.forEach { caulis.simulateKey(it, true) }
        codes.reversed().forEach { caulis.simulateKey(it, false) }
    } finally {
        if (layout != now) caulis.switchKeyboardLayout(now)
    }
    return true
}

// The layout in use if its letter keys type Latin letters, else the first one whose do (us of
// "us,ru"); none: the one in use.
private fun latinLayout(caulis: dev.caulis.wm.Caulis, now: Int): Int {
    fun latin(layout: Int) = caulis.keyText(KEY_A, false, layout)?.singleOrNull()?.let { it in 'a'..'z' } == true
    if (latin(now)) return now
    return caulis.keyboardLayoutNames.indices.firstOrNull(::latin) ?: now
}

// The key that types [letter] in [layout]: its place on a US keyboard if it does there.
private fun keyTyping(caulis: dev.caulis.wm.Caulis, letter: Char, layout: Int): Int? {
    val usual = evdevCodes(letter.toString())?.singleOrNull()
    if (usual != null && caulis.keyText(usual, false, layout) == letter.toString()) return usual
    return (1..0xff).firstOrNull { caulis.keyText(it, false, layout) == letter.toString() } ?: usual
}

private const val KEY_A = 30

/**
 * One thing copied: text, or a picture (its PNG and a picture of it to show), the program it
 * came from ([source], its app id [sourceApp] for its icon), when; [pinned] ones stay.
 */
class ClipEntry(val text: String?, val png: ByteArray?, val picture: ImageBitmap?, val width: Int, val height: Int,
                val source: String? = null, val sourceApp: String? = null, val at: Long = System.currentTimeMillis(),
                /** Copied files (their file:// addresses), or null. */
                val files: List<String>? = null) {
    val kind: ClipKind get() = when {
        files != null -> ClipKind.FILES
        picture != null -> ClipKind.PICTURES
        else -> ClipKind.TEXT
    }

    /** The files' names ("report.pdf"). */
    val fileNames: List<String> get() = files.orEmpty().map { java.net.URLDecoder.decode(it.substringAfterLast('/'), Charsets.UTF_8) }

    val time: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    var pinned by mutableStateOf(false)

    fun same(other: ClipEntry) = files != null && files == other.files || files == null && other.files == null && text != null && text == other.text ||
        png != null && other.png != null && png.contentEquals(other.png)

    /** How long ago, in words ("just now", "5 min ago", "14:03"). */
    fun ago(now: Long = System.currentTimeMillis()): String {
        val minutes = (now - at) / 60_000
        return when {
            minutes < 1 -> newm.bar.tr("just now")
            minutes < 60 -> newm.bar.tr("%s min ago").replace("%s", minutes.toString())
            else -> time
        }
    }
}

/** What a copied thing is. */
enum class ClipKind { TEXT, PICTURES, FILES }

/** Which ones the list shows. */
private enum class ClipFilter(val title: String) {
    ALL("All"), TEXT("Text"), PICTURES("Pictures"), FILES("Files"), PINNED("Pinned");

    fun has(e: ClipEntry) = when (this) {
        ALL -> true
        TEXT -> e.kind == ClipKind.TEXT
        PICTURES -> e.kind == ClipKind.PICTURES
        FILES -> e.kind == ClipKind.FILES
        PINNED -> e.pinned
    }
}

/**
 * The clipboard's history (Logo+V, as Windows has it): what was copied lately — texts and
 * pictures — kept in memory only (never on disk), the newest first. Choosing one copies it
 * again; Delete takes it away. Things password managers mark as secret are not kept.
 * clipboard { enabled = false } turns it off; clipboard { size = 100 } keeps more.
 */
class ClipboardHistory(private val wm: Layout) {
    private companion object {
        // A picture bigger than this is not decoded at all (20000×20000 would be gigabytes).
        const val MAX_PIXELS = 40_000_000L

        // What is shown of a picture: at most this wide and high.
        const val THUMBNAIL = 640

        // Every picture kept, in all, at most (clipboard { picturesTotalMb } is less).
        const val MAX_PICTURE_BYTES = 256L shl 20
    }

    val entries = mutableStateListOf<ClipEntry>()
    var shown by mutableStateOf(false)
        private set
    private var opened by mutableIntStateOf(0)
    private var widget: ComposeWidget? = null

    // Its coming and going (done by the compositor).
    private val fade = newm.bar.Fade("clipboard", inMillis = 260, outMillis = 180)

    // What newm itself put back on the clipboard (not kept again as new).
    private var ours: ClipboardData? = null

    // The programs' names (read once, apart: for "copied from").
    @Volatile private var apps: List<DesktopApp> = emptyList()

    fun start() {
        val caulis = wm.caulis
        loadPins()
        Thread({ apps = DesktopApp.installed() }, "newm-clipboard-apps").apply { isDaemon = true }.start()
        caulis.clipboard.onClipboardChanged { data -> if (data != null && data !== ours && confEnabled()) take(data) }
        val w = ComposeWidget(caulis.desktop, { task -> caulis.post { task.run() } }, place = { m ->
            // (Never over the lock screen.)
            if (!fade.visible || wm.lockShown()) null else {
                val d = caulis.desktop
                val target = d.monitors.firstOrNull { it.area.contains(Rect(d.pointerX.toInt(), d.pointerY.toInt(), 1, 1)) } ?: d.monitors.firstOrNull()
                if (m !== target) null else {
                    // clipboard { width; height; top } (pixels at most; a part of the screen's height).
                    val width = minOf(confWidth().coerceAtLeast(400), m.area.width - 80)
                    val height = minOf(confHeight().coerceAtLeast(240), m.area.height - 120)
                    val y = minOf(m.area.y + (m.area.height * confTop().coerceIn(0.0, 1.0)).toInt(), m.area.y + m.area.height - height).coerceAtLeast(m.area.y)
                    Rect(m.area.x + (m.area.width - width) / 2, y, width, height)
                }
            }
        }, level = DrawingLevel.ON_TOP, takesKeyboard = true,
            appearance = { _ -> fade.appearance(rise = -12f, smallest = 0.95f) }, moving = { fade.moving }) { ClipboardPanel(this) }
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
        val (source, sourceApp) = sourceOf(data.pid)
        // Programs never kept (password managers).
        if (confIgnore().any { ignored -> ignored.equals(source, true) || ignored.equals(sourceApp, true) }) return log.trace { "Not kept: from $source" }
        // Copied files: their addresses.
        if (confFiles() && "text/uri-list" in types) {
            readClipboard(data, "text/uri-list", 1 shl 20) { bytes ->
                val uris = bytes?.let { String(it, Charsets.UTF_8) }?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }.orEmpty()
                if (uris.isNotEmpty()) wm.caulis.post { add(ClipEntry(uris.joinToString("\n"), null, null, 0, 0, source, sourceApp, files = uris)) }
            }
            return
        }
        val picture = if (!confPictures()) null else types.firstOrNull { it == "image/png" } ?: types.firstOrNull { it.startsWith("image/") && it != "image/svg+xml" }
        val text = types.firstOrNull { it == "text/plain;charset=utf-8" } ?: types.firstOrNull { it == "UTF8_STRING" } ?: types.firstOrNull { it == "text/plain" } ?: types.firstOrNull { it.startsWith("text/") }
        val mime = picture ?: text ?: return
        val limit = if (mime == picture) confMaxPictureMb().coerceIn(1, 256) shl 20 else confMaxText().coerceAtLeast(1) * 4
        readClipboard(data, mime, limit) { bytes ->
            if (bytes == null || bytes.isEmpty()) return@readClipboard
            val entry = if (mime == picture) {
                pictureEntry(bytes, mime, source, sourceApp) ?: return@readClipboard
            } else {
                val s = String(bytes, Charsets.UTF_8).trimEnd('\u0000')
                if (s.isBlank()) return@readClipboard
                ClipEntry(s, null, null, 0, 0, source, sourceApp)
            }
            wm.caulis.post { add(entry) }
        }
    }

    // A copied picture: its size read first (one too big is not decoded at all), kept as PNG
    // (what is copied back) with a small picture of it to show — never the whole one decoded.
    private fun pictureEntry(bytes: ByteArray, mime: String, source: String?, sourceApp: String?): ClipEntry? = runCatching {
        val (width, height) = pictureSize(bytes) ?: return null.also { log.trace { "Not kept: an unreadable picture ($mime)" } }
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS) return null.also { log.info { "Not kept: a picture of ${width}x$height is too big" } }
        val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        try {
            // (Kept as PNG: what is copied back is a PNG.)
            val png = if (mime == "image/png") bytes else image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes ?: return null
            ClipEntry(null, png, thumbnail(image).toComposeImageBitmap(), width, height, source, sourceApp)
        } finally {
            image.close()
        }
    }.getOrElse { e ->
        log.info { "Not kept: the picture could not be read ($e)" }
        null
    }

    // The picture's width and height from its header (PNG, JPEG, ...: skia's codec reads only that).
    private fun pictureSize(bytes: ByteArray): Pair<Int, Int>? {
        val data = org.jetbrains.skia.Data.makeFromBytes(bytes)
        return try {
            val codec = runCatching { org.jetbrains.skia.Codec.makeFromData(data) }.getOrNull() ?: return null
            try {
                codec.width to codec.height
            } finally {
                codec.close()
            }
        } finally {
            data.close()
        }
    }

    // [image] at most THUMBNAIL px wide and high (as it is, when smaller).
    private fun thumbnail(image: org.jetbrains.skia.Image): org.jetbrains.skia.Image {
        val scale = minOf(1.0, THUMBNAIL.toDouble() / maxOf(image.width, image.height))
        val w = (image.width * scale).toInt().coerceAtLeast(1)
        val h = (image.height * scale).toInt().coerceAtLeast(1)
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
        try {
            surface.canvas.drawImageRect(
                image, org.jetbrains.skia.Rect.makeWH(image.width.toFloat(), image.height.toFloat()), org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()),
                org.jetbrains.skia.SamplingMode.LINEAR, null, true,
            )
            return surface.makeImageSnapshot()
        } finally {
            surface.close()
        }
    }

    // The program of [pid]: a window's (its title's program, its app id for the icon), else the process' name.
    private fun sourceOf(pid: Int): Pair<String?, String?> {
        if (pid <= 0) return null to null
        val window = wm.caulis.desktop.allWindows.firstOrNull { it.pid == pid }
        if (window != null) {
            val app = window.appId.ifEmpty { null }
            val name = app?.let { id -> apps.firstOrNull { it.id.equals(id, true) || it.id.substringAfterLast('.').equals(id, true) }?.name } ?: app
            return name to app
        }
        val comm = runCatching { java.nio.file.Files.readString(java.nio.file.Path.of("/proc/$pid/comm")).trim() }.getOrNull()
        return comm to null
    }

    internal fun add(entry: ClipEntry) {
        val before = entries.firstOrNull { it.same(entry) }
        if (before?.pinned == true) entry.pinned = true
        entries.removeAll { it.same(entry) }
        entries.add(0, entry)
        // Pinned ones are never pushed out.
        while (entries.size > confSize().coerceIn(1, 500)) {
            val last = entries.indexOfLast { !it.pinned }
            if (last < 0) break
            entries.removeAt(last)
        }
        // Pictures in all: at most clipboard { picturesTotalMb } (the oldest unpinned ones go).
        val most = (confPicturesTotalMb().toLong().coerceIn(1, MAX_PICTURE_BYTES shr 20)) shl 20
        while (entries.sumOf { it.png?.size?.toLong() ?: 0L } > most) {
            val last = entries.indexOfLast { !it.pinned && it.png != null }
            if (last < 0) break
            entries.removeAt(last)
        }
        // (Never what was copied: only what kind of thing.)
        log.trace { "Kept: ${when (entry.kind) { ClipKind.TEXT -> "a text"; ClipKind.FILES -> "${entry.files?.size} files"; ClipKind.PICTURES -> "a picture ${entry.width}x${entry.height}" }}" }
    }

    /** Copies [entry] again (and closes). */
    fun use(entry: ClipEntry) {
        val files = entry.files
        val data: ClipboardData = when {
            files != null -> BytesOnClipboard(linkedMapOf(
                "text/uri-list" to (files.joinToString("\r\n") + "\r\n").toByteArray(),
                "x-special/gnome-copied-files" to ("copy\n" + files.joinToString("\n")).toByteArray(),
                "text/plain;charset=utf-8" to files.joinToString("\n").toByteArray(),
            ))
            entry.png != null -> BytesOnClipboard("image/png", entry.png)
            else -> TextOnClipboard(entry.text ?: "")
        }
        ours = data
        wm.caulis.clipboard.setClipboard(data)
        entries.remove(entry)
        entries.add(0, entry)
        close()
        // clipboard { onChoose = "paste" }: then pasted into the window that has the keyboard.
        if (confOnChoose().trim().lowercase() == "paste") paste()
    }

    // Sends the paste keys (Ctrl+V; Ctrl+Shift+V to a terminal) to the focused window, a moment
    // after the panel gave the keyboard back (through Caulis' input, as a keyboard would).
    private fun paste() {
        wm.caulis.after(120) {
            val window = desktop.focusedWindow ?: return@after log.info { "Not pasted: no window has the keyboard" }
            if (wm.lockShown()) return@after
            val combo = if (isTerminalApp(window.appId)) confTerminalPasteKey() else confPasteKey()
            log.info { "Pasting into ${window.appId} with $combo" }
            if (!sendKeys(this, combo, confPasteOnLatinLayout())) log.warn("clipboard.paste_key: \"$combo\" cannot be sent (modifiers C, S, A, L and a letter, or Insert)")
        }
    }

    fun remove(entry: ClipEntry) {
        entries.remove(entry)
        if (entry.pinned) savePins()
    }

    /** Everything (of [kind], or all) but the pinned ones goes. */
    fun clear(kind: ClipKind? = null) {
        entries.removeAll { !it.pinned && (kind == null || it.kind == kind) }
    }

    /** Something of [kind] (or anything) could be cleared. */
    fun canClear(kind: ClipKind? = null) = entries.any { !it.pinned && (kind == null || it.kind == kind) }

    /** Pins [entry] (it stays, also after a restart: pinned texts are kept on disk) or lets go of it. */
    fun pin(entry: ClipEntry) {
        entry.pinned = !entry.pinned
        savePins()
    }

    // Pinned texts, kept in ~/.local/state/newm/clipboard-pins.json (what you pinned only).
    private val pinsFile by lazy {
        val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.startsWith("/") } ?: "${System.getenv("HOME") ?: System.getProperty("user.home")}/.local/state"
        java.nio.file.Path.of(state, "newm", "clipboard-pins.json")
    }

    private fun savePins() {
        if (!confKeepPins()) return
        try {
            // (Only its user may read it: a pinned text may be a secret.)
            privateFolder(pinsFile.parent)
            writePrivately(pinsFile, Json.write(entries.filter { it.pinned && it.text != null }.map { it.text }))
        } catch (e: Exception) {
            log.warn("The pinned clipboard entries cannot be kept: $e")
        }
    }

    private fun loadPins() {
        if (!confKeepPins()) return
        val texts = try {
            (Json.parse(java.nio.file.Files.readString(pinsFile)) as? List<*>).orEmpty().filterIsInstance<String>()
        } catch (_: Exception) {
            return
        }
        for (t in texts) entries += ClipEntry(t, null, null, 0, 0, at = 0L).also { it.pinned = true }
    }

    fun toggle() = if (shown) close() else open()

    fun open() {
        // (Not over the lock screen: the keyboard stays its own.)
        if (wm.lockShown()) return log.trace { "Not opened: locked" }
        if (!confEnabled()) return run { wm.caulis.notify(newm.bar.tr("The clipboard's history is off (clipboard { enabled = true } turns it on)."), title = "newm") }
        opened++
        if (!hintsLoaded) {
            hintsLoaded = true
            hintsSeen = java.nio.file.Files.exists(hintsFile)
        }
        shown = true
        fade.show()
        widget?.let { wm.caulis.desktop.focusWidget(it) }
    }

    // The hints: never again once "not again" was ticked (~/.local/state/newm/clipboard-hints-seen);
    // else once a session.
    private val hintsFile by lazy {
        val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.startsWith("/") } ?: "${System.getenv("HOME") ?: System.getProperty("user.home")}/.local/state"
        java.nio.file.Path.of(state, "newm", "clipboard-hints-seen")
    }
    private var hintsSeen by mutableStateOf(false)
    private var hintsLoaded = false

    private fun hintsDone(never: Boolean) {
        hintsSeen = true
        if (never) runCatching {
            privateFolder(hintsFile.parent)
            java.nio.file.Files.writeString(hintsFile, "")
        }
    }

    fun close() {
        if (!shown) return
        shown = false
        fade.hide()
        if (wm.caulis.desktop.keyboardWidget === widget) wm.caulis.desktop.focusWidget(null)
    }

    // Where the keys are in the window: the search, the kinds, clearing, the list, the actions.
    private enum class Zone { SEARCH, KINDS, CLEAR, LIST, ACTIONS }

    @Composable
    private fun ClipboardPanel(history: ClipboardHistory, warm: Boolean = false) {
        if (!warm && !history.shown && !fade.visible) return
        val colors = wm.barModel.colorsNow()
        val text = TextStyle(color = colors.text, fontSize = 14.sp)
        var query by remember(opened) { mutableStateOf("") }
        var filter by remember(opened) { mutableStateOf(ClipFilter.ALL) }
        var selected by remember(opened) { mutableIntStateOf(0) }
        var zone by remember(opened) { mutableStateOf(Zone.SEARCH) }
        var action by remember(opened) { mutableIntStateOf(0) }
        // The clearing menu: open, and its row in focus (0: everything, then text, pictures, files).
        var clearMenu by remember(opened) { mutableStateOf(false) }
        var clearRow by remember(opened) { mutableIntStateOf(0) }
        // clipboard { clearAtOnce = false }: "Clear" asked once (of which kind: [askedKind]), the next one clears.
        var clearAsked by remember(opened) { mutableStateOf(false) }
        var askedKind by remember(opened) { mutableStateOf<ClipKind?>(null) }
        val focus = remember { FocusRequester() }
        val list = rememberLazyListState()
        LaunchedEffect(opened) { runCatching { focus.requestFocus() } }
        // The pinned ones first, then the newest.
        val shown = history.entries.filter { e ->
            filter.has(e) && (query.isBlank() || e.text?.contains(query, ignoreCase = true) == true || e.source?.contains(query, ignoreCase = true) == true ||
                e.fileNames.any { it.contains(query, ignoreCase = true) } || (e.png != null && "picture image картинка изображение".contains(query.lowercase())))
        }.sortedByDescending { it.pinned }
        LaunchedEffect(shown.size) { if (selected >= shown.size) selected = (shown.size - 1).coerceAtLeast(0) }
        var byKeys by remember(opened) { mutableIntStateOf(0) }
        LaunchedEffect(byKeys) { if (byKeys > 0 && shown.isNotEmpty()) selected.coerceAtMost(shown.size - 1).let { if (NewmAnimations["scroll"].on) list.animateScrollToItem(it) else list.scrollToItem(it) } }
        // (Read when needed, not while composing: choosing another then redraws only the
        // preview and two rows — reading it here composed the whole window anew on each row
        // the pointer passed, and the pointer lagged.)
        fun current() = shown.getOrNull(selected)
        val clearKinds = listOf<ClipKind?>(null, ClipKind.TEXT, ClipKind.PICTURES, ClipKind.FILES)
        val actions = listOf<Pair<String, () -> Unit>>(
            "Copy" to { current()?.let(history::use) },
            "Pin" to { current()?.let(history::pin) },
            "Delete" to { current()?.let(history::remove) },
        )
        fun clearNow(kind: ClipKind?) {
            if (!history.canClear(kind)) return
            if (!confClearAtOnce() && !(clearAsked && askedKind == kind)) {
                clearAsked = true
                askedKind = kind
                return
            }
            history.clear(kind)
            clearAsked = false
            clearMenu = false
            selected = 0
        }
        fun ring(on: Boolean) = if (on) Modifier.border(2.dp, colors.accent, RoundedCornerShape(16.dp)) else Modifier

        // All the keys of the window: Tab and Shift+Tab go from part to part; the arrows or
        // h j k l move within one; Enter does it; Escape closes the menu, then the window.
        fun onKey(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
            if (e.type != KeyEventType.KeyDown) return false
            // (Asked whether to clear: any other key than Enter is "no".)
            if (clearAsked && e.key != Key.Enter && e.key != Key.NumPadEnter) clearAsked = false
            // The hints first: Enter or Escape is "Got it".
            if (!hintsSeen && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.Escape)) {
                hintsDone(false)
                return true
            }
            val left = e.key == Key.DirectionLeft || zone != Zone.SEARCH && e.key == Key.H
            val right = e.key == Key.DirectionRight || zone != Zone.SEARCH && e.key == Key.L
            val down = e.key == Key.DirectionDown || zone != Zone.SEARCH && e.key == Key.J
            val up = e.key == Key.DirectionUp || zone != Zone.SEARCH && e.key == Key.K
            if (e.key == Key.Escape) {
                if (clearMenu) clearMenu = false else history.close()
                return true
            }
            if (e.key == Key.Tab) {
                clearMenu = false
                val zones = Zone.entries
                zone = zones[Math.floorMod(zone.ordinal + if (e.isShiftPressed) -1 else 1, zones.size)]
                return true
            }
            if (clearMenu) {
                when {
                    down -> clearRow = (clearRow + 1).coerceAtMost(clearKinds.size - 1)
                    up -> clearRow = (clearRow - 1).coerceAtLeast(0)
                    e.key == Key.Enter || e.key == Key.NumPadEnter -> clearNow(clearKinds[clearRow])
                    else -> return zone != Zone.SEARCH
                }
                return true
            }
            if (keyEventIs(confPinKey(), e)) { current()?.let(history::pin); return true }
            when (zone) {
                Zone.SEARCH, Zone.LIST -> when {
                    down -> { selected = (selected + 1).coerceAtMost(shown.size - 1); byKeys++ }
                    up -> { selected = (selected - 1).coerceAtLeast(0); byKeys++ }
                    e.key == Key.Enter || e.key == Key.NumPadEnter -> current()?.let(history::use)
                    e.key == Key.Delete -> current()?.let(history::remove)
                    zone == Zone.LIST && (left || right) -> zone = if (right) Zone.ACTIONS else Zone.KINDS
                    else -> return zone != Zone.SEARCH
                }
                Zone.KINDS -> when {
                    left -> filter = ClipFilter.entries[(filter.ordinal - 1).coerceAtLeast(0)].also { selected = 0 }
                    right -> filter = ClipFilter.entries[(filter.ordinal + 1).coerceAtMost(ClipFilter.entries.size - 1)].also { selected = 0 }
                    down -> zone = Zone.LIST
                    else -> {}
                }
                Zone.CLEAR -> when {
                    e.key == Key.Enter || e.key == Key.NumPadEnter -> clearNow(null)
                    down || e.key == Key.Spacebar -> { clearMenu = true; clearRow = 0 }
                    else -> {}
                }
                Zone.ACTIONS -> when {
                    left -> action = (action - 1).coerceAtLeast(0)
                    right -> action = (action + 1).coerceAtMost(actions.size - 1)
                    up || down -> { selected = (selected + if (down) 1 else -1).coerceIn(0, (shown.size - 1).coerceAtLeast(0)); byKeys++ }
                    e.key == Key.Enter || e.key == Key.NumPadEnter -> actions[action].second()
                    else -> {}
                }
            }
            return true
        }

        val shape = RoundedCornerShape(22.dp)
        Box(
            Modifier.fillMaxSize().padding(16.dp)
                .softShadow(22.dp, 14.dp)
                .background(colors.island.copy(alpha = 0.97f), shape)
                .border(1.dp, colors.edge, shape)
                .padding(16.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                // The search, and clearing (everything, or one kind from the menu).
                Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).fillMaxHeight().then(ring(zone == Zone.SEARCH)).background(colors.text.copy(alpha = 0.06f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.SEARCH, colors.dim, 17.dp)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) BasicText(newm.bar.tr("Search what was copied"), style = text.copy(color = colors.dim, fontSize = 16.sp))
                            BasicTextField(
                                query, { if (zone == Zone.SEARCH) { query = it; selected = 0 } }, singleLine = true, textStyle = text.copy(fontSize = 16.sp), cursorBrush = SolidColor(colors.accent),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent(::onKey),
                            )
                        }
                        // Which kind: everything, texts, pictures, files, the pinned ones (a click: the next).
                        KindSwitch(filter, zone == Zone.KINDS, colors, text) { filter = ClipFilter.entries[(filter.ordinal + 1) % ClipFilter.entries.size]; selected = 0 }
                    }
                    Spacer(Modifier.width(10.dp))
                    // Clearing, one button: the bin and the word, a line, the arrow (one kind only).
                    ClearButton(history.canClear(), zone == Zone.CLEAR, colors, text, if (clearAsked && askedKind == null) "Really clear?" else "Clear",
                        onClear = { clearNow(null) }, onMenu = { clearMenu = !clearMenu; clearRow = 0; clearAsked = false })
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (shown.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                GlyphIcon(Glyph.COPY, colors.dim, 40.dp)
                                Spacer(Modifier.height(12.dp))
                                BasicText(newm.bar.tr(if (history.entries.isEmpty()) "Nothing copied yet — what you copy shows up here." else "Nothing here"), style = text.copy(color = colors.dim))
                            }
                        }
                    } else Row(Modifier.fillMaxSize()) {
                        // The list.
                        LazyColumn(state = list, modifier = Modifier.width(380.dp).fillMaxHeight().then(ring(zone == Zone.LIST)).padding(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            itemsIndexed(shown, key = { _, e -> System.identityHashCode(e) }) { i, entry ->
                                EntryRow(entry, { selected == i }, colors, text, onHover = { selected = i }, onClick = { history.use(entry) },
                                    onPin = { history.pin(entry) }, onRemove = { history.remove(entry) })
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        // What the chosen one is, whole, and what can be done with it.
                        Box(Modifier.weight(1f).fillMaxHeight()) { PreviewOf(::current) { current ->
                        Column(Modifier.fillMaxSize().background(colors.text.copy(alpha = 0.04f), RoundedCornerShape(16.dp)).padding(16.dp)) {
                            if (current != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SourceIcon(current, colors, 22.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        BasicText(current.source ?: newm.bar.tr(when (current.kind) { ClipKind.PICTURES -> "Picture"; ClipKind.FILES -> "Files"; else -> "Text" }),
                                            style = text.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                                        BasicText(listOfNotNull(
                                            current.ago(),
                                            current.files?.let { newm.bar.tr("%s files").replace("%s", it.size.toString()) }
                                                ?: current.text?.let { newm.bar.tr("%s characters").replace("%s", it.length.toString()) },
                                            current.picture?.let { "${current.width} × ${current.height}" },
                                        ).joinToString(" · "), style = text.copy(color = colors.dim, fontSize = 12.sp))
                                    }
                                    // What can be done: copy, pin, delete (pictures alone; Tab reaches them too).
                                    Row(Modifier.then(ring(zone == Zone.ACTIONS)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        @Composable
                                        fun lit(i: Int) = zone == Zone.ACTIONS && action == i
                                        ActionIcon(Glyph.COPY, lit(0), true, colors) { history.use(current) }
                                        ActionIcon(if (current.pinned) Glyph.UNPIN else Glyph.PIN, lit(1), current.pinned, colors) { history.pin(current) }
                                        ActionIcon(Glyph.TRASH, lit(2), false, colors, danger = true) { history.remove(current) }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    when {
                                        current.picture != null -> Image(current.picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.Medium)
                                        current.files != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            for ((name, uri) in current.fileNames.zip(current.files)) Row(verticalAlignment = Alignment.CenterVertically) {
                                                GlyphIcon(Glyph.FILE, colors.dim, 16.dp)
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    BasicText(name, style = text.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    BasicText(java.net.URLDecoder.decode(uri.removePrefix("file://").substringBeforeLast('/'), Charsets.UTF_8), style = text.copy(color = colors.dim, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                            BasicText(current.text ?: "", style = text.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                                        }
                                    }
                                }
                            }
                        }
                        } }
                    }
                    // The clearing menu, under its button.
                    if (clearMenu) Column(
                        Modifier.align(Alignment.TopEnd).width(230.dp).softShadow(14.dp, 8.dp)
                            .background(colors.island, RoundedCornerShape(14.dp)).border(1.dp, colors.edge, RoundedCornerShape(14.dp)).padding(6.dp),
                    ) {
                        val labels = listOf("Clear everything", "Clear text", "Clear pictures", "Clear files")
                        val glyphs = listOf(Glyph.TRASH, Glyph.COPY, Glyph.PICTURE, Glyph.FILE)
                        for ((i, kind) in clearKinds.withIndex()) {
                            val can = history.canClear(kind)
                            ListRow(if (clearAsked && askedKind == kind) "Really clear?" else labels[i], colors, text, selected = clearRow == i, enabled = can, onClick = { clearNow(kind) },
                                leading = { GlyphIcon(glyphs[i], if (can) colors.text else colors.dim.copy(alpha = 0.5f), 15.dp) })
                        }
                    }
                }

            }
            // How to use it: once, over the window, until "not again" is ticked (else again next session).
            if (!hintsSeen) HintsCard(colors, text)
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.BoxScope.HintsCard(colors: newm.bar.BarColors, text: TextStyle) {
        var never by remember { mutableStateOf(false) }
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(430.dp).softShadow(20.dp, 12.dp).background(colors.island, RoundedCornerShape(20.dp)).border(1.dp, colors.edge, RoundedCornerShape(20.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(newm.bar.tr("The clipboard's history"), style = text.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                for ((keys, what) in listOf(
                    "Enter" to if (confOnChoose().trim().lowercase() == "paste") "Paste the chosen one" else "Copy the chosen one again",
                    "↑ ↓  ·  j k" to "Choose",
                    "Tab" to "Go to the next part (kinds, Clear, the list, its buttons)",
                    keyNames(confPinKey()).joinToString(" ") { it.joinToString("+") } to "Pin, so it stays",
                    "Delete" to "Remove it",
                    "Esc" to "Close",
                )) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(110.dp)) {
                        BasicText(keys, style = text.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.background(colors.text.copy(alpha = 0.08f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp))
                    }
                    BasicText(newm.bar.tr(what), style = text.copy(fontSize = 12.5.sp))
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).pointerInput(Unit) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) never = !never } },
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).background(if (never) colors.accent else Color.Transparent, RoundedCornerShape(5.dp))
                            .border(1.5.dp, if (never) colors.accent else colors.dim, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                            if (never) GlyphIcon(Glyph.CHECK, newm.bar.onAccent(colors), 12.dp)
                        }
                        Spacer(Modifier.width(8.dp))
                        BasicText(newm.bar.tr("Don't show again"), style = text.copy(fontSize = 12.5.sp))
                    }
                    PillButton("Got it", colors, text, PillKind.PRIMARY) { hintsDone(never) }
                }
            }
        }
    }

    // "🗑 Clear | ▾": one rounded button, a thin line between its two parts.
    @Composable
    private fun ClearButton(can: Boolean, ringed: Boolean, colors: newm.bar.BarColors, text: TextStyle, label: String, onClear: () -> Unit, onMenu: () -> Unit) {
        val fg = if (can) colors.danger else colors.dim
        val bg = if (can) colors.danger.copy(alpha = 0.12f) else colors.text.copy(alpha = 0.06f)
        Row(
            Modifier.height(38.dp).then(if (ringed) Modifier.border(2.dp, colors.accent, RoundedCornerShape(19.dp)) else Modifier)
                .background(bg, RoundedCornerShape(19.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.fillMaxHeight().pointerInput(onClear) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onClear() } }
                .padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.TRASH, fg, 15.dp)
                Spacer(Modifier.width(7.dp))
                BasicText(newm.bar.tr(label), style = text.copy(color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            }
            Box(Modifier.width(1.dp).height(20.dp).background(fg.copy(alpha = 0.35f)))
            Box(Modifier.fillMaxHeight().pointerInput(onMenu) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onMenu() } }
                .padding(start = 8.dp, end = 12.dp), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.CHEVRON_DOWN, fg, 15.dp)
            }
        }
    }

    // The kind shown, a small pill at the search's end ("All ▾").
    @Composable
    private fun KindSwitch(filter: ClipFilter, ringed: Boolean, colors: newm.bar.BarColors, text: TextStyle, onClick: () -> Unit) {
        Row(
            Modifier.height(30.dp).then(if (ringed) Modifier.border(2.dp, colors.accent, RoundedCornerShape(15.dp)) else Modifier)
                .background(colors.text.copy(alpha = 0.07f), RoundedCornerShape(15.dp))
                .pointerInput(onClick) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onClick() } }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(newm.bar.tr(filter.title), style = text.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(Modifier.width(6.dp))
            GlyphIcon(Glyph.CHEVRON_DOWN, colors.dim, 13.dp)
        }
    }

    // An action as a picture alone, round (lit when [on]; ringed when the keys are on it).
    @Composable
    private fun ActionIcon(glyph: Glyph, ringed: Boolean, on: Boolean, colors: newm.bar.BarColors, danger: Boolean = false, onClick: () -> Unit) {
        val source = remember { MutableInteractionSource() }
        val hovered by source.collectIsHoveredAsState()
        val fg = when {
            danger -> colors.danger
            on -> colors.accent
            else -> colors.text
        }
        Box(
            Modifier.size(34.dp).then(if (ringed) Modifier.border(2.dp, colors.accent, RoundedCornerShape(17.dp)) else Modifier)
                .background(if (hovered) fg.copy(alpha = 0.16f) else colors.text.copy(alpha = 0.06f), RoundedCornerShape(17.dp)).hoverable(source)
                .pointerInput(onClick) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onClick() } },
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(glyph, fg, 16.dp) }
    }

    /** The window as it looks, for drawing it once ahead (see Warmup). */
    @Composable
    internal fun WarmPanel() = ClipboardPanel(this, warm = true)

    // The chosen one, read here alone: only this part recomposes when another is chosen.
    @Composable
    private fun PreviewOf(chosen: () -> ClipEntry?, content: @Composable (ClipEntry?) -> Unit) = content(chosen())

    // The program it came from (its icon), or what it is.
    @Composable
    private fun SourceIcon(entry: ClipEntry, colors: newm.bar.BarColors, size: androidx.compose.ui.unit.Dp) {
        var icon by remember(entry.sourceApp) { mutableStateOf<ImageBitmap?>(null) }
        val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
        LaunchedEffect(entry.sourceApp) { entry.sourceApp?.let { app -> newm.bar.IconTheme.load(app, px, null, post = { wm.caulis.post { it() } }) { icon = it } } }
        val picture = icon
        if (picture != null) Image(picture, null, Modifier.size(size))
        else GlyphIcon(when (entry.kind) { ClipKind.PICTURES -> Glyph.PICTURE; ClipKind.FILES -> Glyph.FILE; else -> Glyph.COPY }, colors.dim, size)
    }

    @Composable
    private fun EntryRow(entry: ClipEntry, selected: () -> Boolean, colors: newm.bar.BarColors, text: TextStyle, onHover: () -> Unit, onClick: () -> Unit, onPin: () -> Unit, onRemove: () -> Unit) {
        val source = remember { MutableInteractionSource() }
        val hovered by source.collectIsHoveredAsState()
        LaunchedEffect(hovered) { if (hovered) onHover() }
        val lit = colors.accent.copy(alpha = 0.18f)
        Row(
            Modifier.fillMaxWidth().height(if (entry.picture != null) 72.dp else 58.dp)
                .drawBehind { if (selected()) drawRoundRect(lit, cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())) }
                .hoverable(source)
                .pointerInput(onClick) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onClick() } }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.picture != null) {
                Image(entry.picture, null, Modifier.size(56.dp).background(colors.text.copy(alpha = 0.06f), RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit, filterQuality = FilterQuality.Medium)
            } else {
                Box(Modifier.size(34.dp).background(colors.text.copy(alpha = 0.07f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { SourceIcon(entry, colors, 18.dp) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                BasicText(if (entry.files != null) entry.fileNames.joinToString(", ") else entry.text?.lines()?.firstOrNull { it.isNotBlank() }?.trim() ?: newm.bar.tr("Picture"), style = text.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val lines = if (entry.files != null) 0 else entry.text?.trimEnd()?.lines()?.size ?: 0
                BasicText(listOfNotNull(entry.source, entry.ago(), entry.files?.let { newm.bar.tr("%s files").replace("%s", it.size.toString()) }, if (lines > 1) newm.bar.tr("%s lines").replace("%s", lines.toString()) else null).joinToString(" · "),
                    style = text.copy(color = colors.dim, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (entry.pinned) GlyphIcon(Glyph.PIN, colors.accent, 14.dp)
            if (hovered) {
                Spacer(Modifier.width(4.dp))
                IconButton(if (entry.pinned) Glyph.UNPIN else Glyph.PIN, colors, 28.dp, onClick = onPin)
                Spacer(Modifier.width(4.dp))
                IconButton(Glyph.TRASH, colors, 28.dp, onClick = onRemove)
            }
        }
    }
}
