package newm.bar

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.caulis.log.Logger
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val log = Logger("bar")

/**
 * Icons by name from the installed icon themes (freedesktop's icon theme folders: the theme
 * in use, Adwaita, breeze, hicolor, /usr/share/pixmaps), as pictures for Compose. Looking up
 * reads folders and files: [load] does it on a thread of its own and hands the picture over.
 */
object IconTheme {

    private val ICON_THREADS = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)

    // Threads for the reading and drawing of icons (never the compositor's): as many as half the
    // processors (at most 4: a start with many windows draws their icons side by side), gone
    // again after a few seconds without work.
    private val worker = java.util.concurrent.ThreadPoolExecutor(
        ICON_THREADS, ICON_THREADS, 5, java.util.concurrent.TimeUnit.SECONDS, java.util.concurrent.LinkedBlockingQueue(),
    ) { r -> Thread(r, "newm-icons").apply { isDaemon = true } }.apply { allowCoreThreadTimeOut(true) }

    // Every icon file of a theme folder by its name ("firefox" -> its files).
    private val indexes = ConcurrentHashMap<Path, Map<String, List<Path>>>()
    private val pictures = ConcurrentHashMap<String, ImageBitmap>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    private val home = System.getenv("HOME") ?: System.getProperty("user.home")

    // The folders icons are in, the user's first.
    private val baseDirs: List<Path> by lazy {
        val dataHome = System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
        val dataDirs = (System.getenv("XDG_DATA_DIRS") ?: "/usr/local/share:/usr/share").split(':').filter { it.isNotBlank() }
        (listOf("$home/.icons", "$dataHome/icons") + dataDirs.map { "$it/icons" }).map { Path.of(it) }.filter(Files::isDirectory).distinct()
    }

    // The theme in use (GNOME's setting, else Adwaita), then the usual ones.
    private val themes: List<String> by lazy {
        val chosen = try {
            val p = dev.caulis.ffi.Processes.start(ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "icon-theme").redirectErrorStream(true))
            p.inputStream.bufferedReader().readText().trim().trim('\'').takeIf { p.waitFor() == 0 && it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
        val order = LinkedHashSet<String>()
        fun add(theme: String, depth: Int = 0) {
            if (!order.add(theme) || depth > 4) return
            inherits(theme).forEach { add(it, depth + 1) }
        }
        chosen?.let { add(it) }
        listOf("Adwaita", "breeze", "Papirus", "elementary", "hicolor").forEach { add(it) }
        log.info { "Icon themes: ${order.joinToString()}" }
        order.toList()
    }

    // The themes [theme] builds on (its index.theme's Inherits).
    private fun inherits(theme: String): List<String> = baseDirs.asSequence().map { it.resolve(theme).resolve("index.theme") }.firstOrNull(Files::isRegularFile)?.let { file ->
        try {
            Files.readAllLines(file).firstOrNull { it.startsWith("Inherits=") }?.substringAfter('=')?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }.orEmpty()

    // The icon files under [root] by name (a theme folder, or a program's own folder of icons).
    private fun index(root: Path): Map<String, List<Path>> = indexes.getOrPut(root) {
        val map = HashMap<String, MutableList<Path>>()
        if (Files.isDirectory(root)) try {
            Files.walk(root, 4).use { files ->
                for (file in files) {
                    val name = file.fileName?.toString() ?: continue
                    if (!name.endsWith(".png") && !name.endsWith(".svg")) continue
                    map.getOrPut(name.substringBeforeLast('.')) { ArrayList() }.add(file)
                }
            }
        } catch (e: Exception) {
            log.trace { "Icons in $root cannot be read: $e" }
        }
        map
    }

    /** The file of the icon [name] at about [size] pixels ([extraDir]: a program's own folder first), or null. */
    fun find(name: String, size: Int, extraDir: String? = null): Path? {
        if (name.startsWith("/")) return Path.of(name).takeIf(Files::isRegularFile)
        val roots = buildList {
            extraDir?.takeIf { it.isNotBlank() }?.let { add(Path.of(it)) }
            for (theme in themes) for (base in baseDirs) base.resolve(theme).takeIf(Files::isDirectory)?.let(::add)
            add(Path.of("/usr/share/pixmaps"))
        }
        for (root in roots) {
            val files = index(root)[name] ?: continue
            return best(files, size)
        }
        return null
    }

    // The file nearest to [size]: a scalable one, else the smallest not smaller, else the biggest.
    private fun best(files: List<Path>, size: Int): Path {
        files.firstOrNull { it.toString().endsWith(".svg") && ("scalable" in it.toString() || "symbolic" !in it.toString()) }?.let { return it }
        val sized = files.map { file -> file to (Regex("/(\\d+)x\\d+(@\\d+)?/").find(file.toString())?.groupValues?.get(1)?.toIntOrNull() ?: 48) }
        return (sized.filter { it.second >= size }.minByOrNull { it.second } ?: sized.maxBy { it.second }).first
    }

    /**
     * The icon [name] at [size] pixels, as a picture: [done] gets it on the compositor's thread
     * ([post]) — at once when it was loaded before; null when there is none.
     */
    /** The biggest picture file read (bytes) and picture drawn (pixels): what programs send is not trusted. */
    const val MAX_FILE_BYTES = 16_000_000L
    const val MAX_PIXELS = 4096L * 4096

    // The most pictures (and names without one) kept in memory; more, and they are read again.
    private const val MAX_KEPT = 512

    fun load(name: String, size: Int, extraDir: String? = null, post: (() -> Unit) -> Unit, done: (ImageBitmap?) -> Unit) {
        val key = "$name@$size@${extraDir.orEmpty()}"
        pictures[key]?.let { return done(it) }
        if (key in missing) return done(null)
        worker.execute {
            val picture = try {
                find(name, size, extraDir)?.let { render(it, size) }
            } catch (e: Exception) {
                log.trace { "The icon $name cannot be drawn: $e" }
                null
            }
            // (Kept for the next time — not without end: each notification's image-path is a new name.)
            if (pictures.size >= MAX_KEPT) pictures.clear()
            if (missing.size >= MAX_KEPT) missing.clear()
            if (picture != null) pictures[key] = picture else missing += key
            post { done(picture) }
        }
    }

    /**
     * A picture [make] makes (from what a program sent: it may take a while), made on the icons'
     * thread; [done] gets it on the compositor's thread ([post]), null when none was made.
     */
    fun make(post: (() -> Unit) -> Unit, make: () -> ImageBitmap?, done: (ImageBitmap?) -> Unit) {
        worker.execute {
            val picture = try {
                make()
            } catch (e: Exception) {
                log.trace { "A picture cannot be made: $e" }
                null
            }
            post { done(picture) }
        }
    }

    /**
     * The icon of the program with [appId] (its .desktop file's Icon=) at [size] pixels: the
     * name is looked up on the icons' thread too (the first time all .desktop files are read),
     * [done] gets the picture on the compositor's thread ([post]).
     */
    fun loadApp(appId: String, size: Int, post: (() -> Unit) -> Unit, done: (ImageBitmap?) -> Unit) {
        worker.execute {
            val name = AppIcons.iconName(appId)
            // (On the icons' thread here: a picture loaded before must still reach [done] on the compositor's.)
            load(name, size, null, post) { picture -> post { done(picture) } }
        }
    }

    /** [file] (PNG or SVG) drawn as a square of [size] pixels (null: not a picture newm takes). */
    fun render(file: Path, size: Int): ImageBitmap? {
        // (A program may name any file — a device, a pipe, a huge one: only ordinary files of a sensible size.)
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) return null.also { log.trace { "Not drawn (not a picture file of at most ${MAX_FILE_BYTES / 1_000_000} MB): $file" } }
        val bytes = Files.readAllBytes(file)
        val surface = Surface.makeRasterN32Premul(size, size)
        val canvas = surface.canvas
        if (file.toString().endsWith(".svg")) {
            // At its own size (its viewBox, else width and height), scaled to the square, in the middle
            // (given the square alone it drew at its own size: of a big one only its top left showed).
            val svg = SVGDOM(Data.makeFromBytes(bytes))
            val root = svg.root
            val box = root?.viewBox
            val w = root?.width?.takeIf { it.unit != org.jetbrains.skia.svg.SVGLengthUnit.PERCENTAGE && it.value > 0f }?.value ?: box?.width?.takeIf { it > 0f } ?: size.toFloat()
            val h = root?.height?.takeIf { it.unit != org.jetbrains.skia.svg.SVGLengthUnit.PERCENTAGE && it.value > 0f }?.value ?: box?.height?.takeIf { it > 0f } ?: size.toFloat()
            svg.setContainerSize(w, h)
            val scale = size / maxOf(w, h)
            canvas.translate((size - w * scale) / 2f, (size - h * scale) / 2f)
            canvas.scale(scale, scale)
            svg.render(canvas)
        } else {
            // Made smaller smoothly (through smaller copies of it: a 256 px icon at 40 px came out jagged).
            val image = Image.makeFromEncoded(bytes)
            // (Its size is read from the header: a picture that unpacks into gigabytes is not drawn.)
            if (image.width.toLong() * image.height > MAX_PIXELS) return null.also { log.trace { "Not drawn (${image.width}x${image.height}): $file" } }
            canvas.drawImageRect(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()), Rect.makeWH(size.toFloat(), size.toFloat()),
                org.jetbrains.skia.FilterMipmap(org.jetbrains.skia.FilterMode.LINEAR, org.jetbrains.skia.MipmapMode.LINEAR), Paint(), true)
        }
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }

    /**
     * A picture from ARGB32 pixels in network byte order (a tray icon's IconPixmap), drawn as
     * a square of [size] pixels.
     */
    fun fromArgb(width: Int, height: Int, argb: ByteArray, size: Int): ImageBitmap? {
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS || argb.size < width * height * 4) return null
        // Skia's N32 here is BGRA (little-endian), premultiplied.
        val pixels = ByteArray(width * height * 4)
        for (p in 0 until width * height) {
            val a = argb[4 * p].toInt() and 0xff
            val r = argb[4 * p + 1].toInt() and 0xff
            val g = argb[4 * p + 2].toInt() and 0xff
            val b = argb[4 * p + 3].toInt() and 0xff
            pixels[4 * p] = (b * a / 255).toByte()
            pixels[4 * p + 1] = (g * a / 255).toByte()
            pixels[4 * p + 2] = (r * a / 255).toByte()
            pixels[4 * p + 3] = a.toByte()
        }
        val info = org.jetbrains.skia.ImageInfo.makeN32Premul(width, height)
        val image = Image.makeRaster(info, pixels, width * 4)
        val surface = Surface.makeRasterN32Premul(size, size)
        surface.canvas.drawImageRect(image, Rect.makeWH(width.toFloat(), height.toFloat()), Rect.makeWH(size.toFloat(), size.toFloat()), SamplingMode.LINEAR, Paint(), true)
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }
}

/**
 * A picture from RGB or RGBA rows ([channels] 3 or 4, [stride] bytes a row: a notification's
 * image-data), drawn as a square of [size] pixels.
 */
fun IconTheme.fromRgba(width: Int, height: Int, stride: Int, channels: Int, rgba: ByteArray, size: Int): ImageBitmap? {
    // (Counted in Long: a program's numbers may overflow an Int and pass a check they should fail.)
    if (width <= 0 || height <= 0 || width.toLong() * height > IconTheme.MAX_PIXELS || channels !in 3..4 || stride.toLong() < width.toLong() * channels ||
        rgba.size.toLong() < stride.toLong() * (height - 1) + width.toLong() * channels) return null
    // Skia's N32 here is BGRA (little-endian), premultiplied.
    val pixels = ByteArray(width * height * 4)
    for (y in 0 until height) for (x in 0 until width) {
        val at = y * stride + x * channels
        val r = rgba[at].toInt() and 0xff
        val g = rgba[at + 1].toInt() and 0xff
        val b = rgba[at + 2].toInt() and 0xff
        val a = if (channels == 4) rgba[at + 3].toInt() and 0xff else 255
        val p = 4 * (y * width + x)
        pixels[p] = (b * a / 255).toByte()
        pixels[p + 1] = (g * a / 255).toByte()
        pixels[p + 2] = (r * a / 255).toByte()
        pixels[p + 3] = a.toByte()
    }
    val image = Image.makeRaster(org.jetbrains.skia.ImageInfo.makeN32Premul(width, height), pixels, width * 4)
    val surface = Surface.makeRasterN32Premul(size, size)
    // (Cut to a square from its middle: an avatar is round in its card anyway.)
    val side = minOf(width, height).toFloat()
    val src = Rect.makeXYWH((width - side) / 2f, (height - side) / 2f, side, side)
    surface.canvas.drawImageRect(image, src, Rect.makeWH(size.toFloat(), size.toFloat()), SamplingMode.LINEAR, Paint(), true)
    return surface.makeImageSnapshot().toComposeImageBitmap()
}

/** Programs' icons by their app id, from their .desktop files (Icon=). */
object AppIcons {
    private val byId: Map<String, String> by lazy {
        val started = System.nanoTime()
        readDesktopFiles().also { log.trace { "Programs' icons: ${it.size} names from .desktop files in ${(System.nanoTime() - started) / 1_000_000} ms" } }
    }

    /** The icon name of the program with [appId] ("firefox", "org.gnome.Nautilus"), or [appId] itself. */
    fun iconName(appId: String): String {
        if (appId.isEmpty()) return "application-x-executable"
        return byId[appId.lowercase()] ?: byId[appId.lowercase().substringAfterLast('.')] ?: appId.lowercase()
    }

    private fun readDesktopFiles(): Map<String, String> {
        val home = System.getenv("HOME") ?: System.getProperty("user.home")
        val dataHome = System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
        val dataDirs = (System.getenv("XDG_DATA_DIRS") ?: "/usr/local/share:/usr/share").split(':')
        val map = HashMap<String, String>()
        for (dir in (listOf(dataHome) + dataDirs + "/var/lib/flatpak/exports/share" + "$home/.local/share/flatpak/exports/share").reversed()) {
            val folder = Path.of(dir, "applications")
            if (!Files.isDirectory(folder)) continue
            try {
                Files.walk(folder).use { files ->
                    for (file in files.filter { it.toString().endsWith(".desktop") }.toList()) {
                        val lines = try { Files.readAllLines(file) } catch (_: Exception) { continue }
                        val icon = lines.firstOrNull { it.startsWith("Icon=") }?.substringAfter('=')?.trim() ?: continue
                        val id = file.fileName.toString().removeSuffix(".desktop").lowercase()
                        map[id] = icon
                        lines.firstOrNull { it.startsWith("StartupWMClass=") }?.substringAfter('=')?.trim()?.lowercase()?.let { map[it] = icon }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return map
    }
}
