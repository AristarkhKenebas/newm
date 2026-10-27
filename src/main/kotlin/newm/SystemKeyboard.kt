package newm

import dev.caulis.log.Logger
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("keyboard")

/**
 * The keyboard layouts of the system, for a configuration that names none: XKB_DEFAULT_LAYOUT,
 * else the X11 keyboard setting (localectl set-x11-keymap writes
 * /etc/X11/xorg.conf.d/00-keyboard.conf), else the
 * language (LANG=ru_RU: "us,ru"), with Alt+Shift switching when there are several.
 */
data class SystemKeyboard(val layouts: String, val options: String) {
    companion object {
        // Languages whose keyboard is not their xkb name (most are: de, fr, ru, ...).
        private val layoutOfLanguage = mapOf(
            "uk" to "ua", "be" to "by", "kk" to "kz", "cs" to "cz", "sv" to "se", "da" to "dk", "et" to "ee", "el" to "gr",
            "he" to "il", "ja" to "jp", "ko" to "kr", "zh" to "cn", "ar" to "ara", "fa" to "ir", "hy" to "am", "ka" to "ge",
            "sr" to "rs", "sl" to "si", "nb" to "no", "nn" to "no", "vi" to "vn", "uz" to "uz", "ky" to "kg", "tg" to "tj",
        )

        private const val SWITCH = "grp:alt_shift_toggle"

        fun detect(): SystemKeyboard {
            // XKB_DEFAULT_LAYOUT and _OPTIONS (as wlroots compositors read them; newm's NixOS
            // module sets them from services.xserver.xkb).
            System.getenv("XKB_DEFAULT_LAYOUT")?.takeIf { it.isNotBlank() }?.let { layouts ->
                val options = System.getenv("XKB_DEFAULT_OPTIONS")?.takeIf { it.isNotBlank() } ?: if (',' in layouts) SWITCH else ""
                log.info { "Keyboard layouts from XKB_DEFAULT_LAYOUT: $layouts" }
                return SystemKeyboard(layouts, options)
            }
            fromX11Conf()?.let { return it.also { k -> log.info { "Keyboard layouts of the system: ${k.layouts}" } } }
            val lang = (System.getenv("LC_ALL") ?: System.getenv("LANG") ?: "").substringBefore('_').substringBefore('.').lowercase()
            if (lang.isEmpty() || lang == "en" || lang == "c" || lang == "posix") return SystemKeyboard("us", "")
            val layout = layoutOfLanguage[lang] ?: lang
            log.info { "Keyboard layouts for the language $lang: us,$layout (Alt+Shift switches)" }
            return SystemKeyboard("us,$layout", SWITCH)
        }

        // Option "XkbLayout" "us,ru" and Option "XkbOptions" "..." of the X11 keyboard setting.
        private fun fromX11Conf(): SystemKeyboard? {
            val dir = Path.of("/etc/X11/xorg.conf.d")
            val text = try {
                Files.list(dir).use { files -> files.filter { it.toString().endsWith(".conf") }.sorted().toList() }.joinToString("\n") { Files.readString(it) }
            } catch (_: Exception) {
                return null
            }
            fun option(name: String) = Regex("Option\\s+\"$name\"\\s+\"([^\"]*)\"").find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            val layouts = option("XkbLayout") ?: return null
            val options = option("XkbOptions") ?: if (',' in layouts) SWITCH else ""
            return SystemKeyboard(layouts, options)
        }
    }
}
