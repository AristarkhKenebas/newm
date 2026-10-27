package newm

import androidx.compose.animation.core.FiniteAnimationSpec
import dev.caulis.compose.spec
import dev.caulis.core.AnimationEntry
import dev.caulis.core.AnimationNode
import dev.caulis.core.AnimationSettings
import dev.caulis.core.AnimationTree
import dev.caulis.core.Curve
import dev.caulis.core.ResolvedAnimation
import dev.caulis.log.Logger
import kotlin.math.ln
import kotlin.math.sqrt

private val log = Logger("animations")

// animations { } of the configuration (newm's names: preset, enabled, millis, curve, curves and the animations by name).
private val confAnimations = configuredValue<Map<String, Any?>>("animations", emptyMap())

// config.py's anim_time (seconds): the global time when animations { millis } is not given.
private val confAnimTime = configuredValue("anim_time", .3)

/**
 * newm's animations, Hyprland-like, with Caulis's model ([AnimationTree], [AnimationSettings]):
 * off unless turned on — `animations { enabled = true }` or `preset = "newm"` (newm's look as it
 * always was) — and each one by name with its time, curve and (for some) look. A setting for
 * a name is for those under it too ("windows" for windowsIn, windowsOut, ...). What no one says
 * is the animation's usual: what newm always did (the times of config.py's anim_time,
 * blend_time, focus.anim_time, ... and the bar's own), so `preset = "newm"` is today's newm.
 *
 * Off, a change is there at once (no frame in between); following fingers is not an animation —
 * only what comes after they lift (gestureSnap) and their smoothing (gestureSmoothing) are.
 */
object NewmAnimations {

    /** Compose's usual curve (FastOutSlowInEasing): what the bar's tweens moved along. */
    @JvmField val FAST_OUT_SLOW_IN: Curve = Curve.bezier(0.4, 0.0, 0.2, 1.0)

    /** Compose's spring of [stiffness] and [dampingRatio] (1: no bounce), as a [Curve]. */
    fun composeSpring(stiffness: Double, dampingRatio: Double): Curve = Curve.spring(stiffness, 2 * dampingRatio * sqrt(stiffness), 1.0)

    // The bar's springs as they were (made once: a spring is worked out step by step).
    internal val POPUP_SPRING: Curve by lazy { composeSpring(700.0, 0.9) }
    internal val CONTROL_SPRING: Curve by lazy { composeSpring(800.0, 0.9) }

    /** The looks of what comes and goes as a picture (menus, cards, panels): see [newm.bar.Fade.appearance]. */
    val FADE_STYLES = listOf("fade", "popin [96%]", "slide [up|down] [8]")

    /** newm's animations by name. */
    @JvmField val TREE = AnimationTree(
        listOf(
            AnimationNode("windows", null, "every animation of windows below"),
            AnimationNode("windowsIn", "windows", "a window appearing: it takes its place in the layout, the others make room"),
            AnimationNode("windowsOut", "windows", "a window closing: the others take its place"),
            AnimationNode("windowsMove", "windows", "a window moved, resized, made floating or tiled, put side by side, onto another workspace, swallowed by the program it started and back; the layout cleaned up (`clean`)"),
            AnimationNode("fullscreen", "windows", "a window going fullscreen and back"),
            AnimationNode("moveResize", "windows", "a window moved or resized with the mouse or fingers settling on the grid when let go"),
            AnimationNode("camera", null, "the view moving over the windows: to the next window, to the one focused, zooming in and out, making room for a bar that came or went, keeping a dragged window in sight"),
            AnimationNode("overview", null, "the overview opening and closing, also choosing a window in it"),
            AnimationNode("gestures", null, "what fingers start, below"),
            AnimationNode("gestureSnap", "gestures", "after a swipe or pinch, the view or the window gliding on to where it settles; its time comes from how fast the fingers went (the grid's time_scale) unless one is set"),
            AnimationNode("gestureSmoothing", "gestures", "the fingers' movement smoothed: the view follows a little behind them, softly (gestures.lp_inertia; the time is how soon it catches up, 75 ms); off: exactly under the fingers"),
            AnimationNode("border", null, "the focus border moving to the focused window and changing with it (focus.anim_time, 300 ms)"),
            AnimationNode("lock", null, "the lock screen sliding in (the screen is locked before it moves) and away after unlocking"),
            AnimationNode("launcher", null, "newm's launcher coming and going", curve = Curve.EASE_OUT),
            AnimationNode("launcherList", "launcher", "the launcher's first rows coming in one after another", millis = 220, curve = FAST_OUT_SLOW_IN),
            AnimationNode("background", null, "the wallpaper, below"),
            AnimationNode("backgroundMove", "background", "the wallpaper gliding behind the view, catching up softly (background.time_scale: how soon, 150 ms)", millis = 150),
            AnimationNode("backgroundIn", "background", "the wallpaper fading in the first time it shows", millis = 600, curve = Curve.EASE_OUT),
            AnimationNode("session", null, "newm starting and quitting, below"),
            AnimationNode("startup", "session", "newm fading in after it started (blend_time, 1 s)"),
            AnimationNode("quit", "session", "newm fading out when it quits (blend_time, 1 s)"),
            AnimationNode("sessionRestore", "session", "windows kept from the last session going back to their places (250 ms, the views 300 ms)"),
            AnimationNode("popups", null, "the bar's menus (calendar, quick settings, tray menus, ...)", styles = FADE_STYLES),
            AnimationNode("popupsIn", "popups", "a menu opening", millis = 280, curve = Curve.EASE_OUT, styles = FADE_STYLES),
            AnimationNode("popupsOut", "popups", "a menu closing", millis = 180, curve = Curve.SMOOTH, styles = FADE_STYLES),
            AnimationNode("popupsResize", "popups", "a menu growing and shrinking as what it shows changes, its parts unfolding"),
            AnimationNode("notifications", null, "notification cards", styles = FADE_STYLES),
            AnimationNode("notificationsIn", "notifications", "a card coming", millis = 260, curve = FAST_OUT_SLOW_IN, styles = FADE_STYLES),
            AnimationNode("notificationsOut", "notifications", "a card going", millis = 220, curve = FAST_OUT_SLOW_IN, styles = FADE_STYLES),
            AnimationNode("osd", null, "the volume and brightness slider, toasts and the cards in the middle (a network came or went)", styles = FADE_STYLES),
            AnimationNode("osdIn", "osd", "it appearing", millis = 140, curve = FAST_OUT_SLOW_IN, styles = FADE_STYLES),
            AnimationNode("osdOut", "osd", "it going", millis = 220, curve = FAST_OUT_SLOW_IN, styles = FADE_STYLES),
            AnimationNode("clipboard", null, "the clipboard's history (Logo+V)", styles = FADE_STYLES),
            AnimationNode("clipboardIn", "clipboard", "it opening", millis = 260, curve = Curve.EASE_OUT, styles = FADE_STYLES),
            AnimationNode("clipboardOut", "clipboard", "it closing", millis = 180, curve = Curve.SMOOTH, styles = FADE_STYLES),
            AnimationNode("keyHelp", null, "the keys' help (Logo+/)", styles = FADE_STYLES),
            AnimationNode("keyHelpIn", "keyHelp", "it opening", millis = 260, curve = Curve.EASE_OUT, styles = FADE_STYLES),
            AnimationNode("keyHelpOut", "keyHelp", "it closing", millis = 180, curve = Curve.SMOOTH, styles = FADE_STYLES),
            AnimationNode("widgets", null, "small motions in newm's bar, menus and panels, below"),
            AnimationNode("hover", "widgets", "colours changing under the pointer", millis = 120, curve = FAST_OUT_SLOW_IN),
            AnimationNode("toggles", "widgets", "switches, sliders and buttons moving to their new state", millis = 160, curve = FAST_OUT_SLOW_IN),
            AnimationNode("scroll", "widgets", "a list scrolling to the chosen entry (launcher, clipboard; Compose's own time)", millis = 300),
            AnimationNode("spinner", "widgets", "the refresh icon turning while it looks, arrows turning when a part unfolds", millis = 900, curve = Curve.LINEAR),
            AnimationNode("brightness", null, "the screen's brightness gliding to a new level (config.py's BacklightManager)"),
        ),
    )

    // The settings made from the configuration, and what they were made of.
    private var settings: AnimationSettings? = null
    private var madeFrom: Pair<Map<String, Any?>, Double>? = null

    /** The settings now (made again when the configuration changed). */
    fun settings(): AnimationSettings {
        val section = confAnimations()
        val animTime = confAnimTime()
        val from = section to animTime
        settings?.let { made -> if (madeFrom?.first === section && madeFrom?.second == animTime) return made }
        val problems = ArrayList<String>()
        val made = read(section, animTime, problems)
        problems.forEach { log.warn("animations: $it") }
        settings = made
        madeFrom = from
        log.info { "Animations ${if (made.enabled) "on" else "off"}${if (made.entries.isEmpty()) "" else ", ${made.entries.size} set by name"}" }
        log.trace { "Animations by name:\n${made.describe()}" }
        return made
    }

    /** True when animations { } says on or off (or a preset): Caulis's own follow it then. */
    fun explicitlySet(): Boolean = confAnimations().let { it["enabled"] != null || it["preset"] != null }

    /**
     * Whether Caulis's own animations (a monitor's scale changing, its notices, ...) are to be on
     * (null: as Caulis's settings say, off unless set there): as `enabled` or the preset "on" /
     * "none" say. Not with preset = "newm" alone: that is newm's look as it always was, when
     * Caulis's were off.
     */
    fun caulisEnabled(): Boolean? = caulisEnabled(confAnimations())

    /** Caulis's animations [now] turned [on] or off; on, its windows' stay off (newm's are). */
    fun caulisAnimations(now: dev.caulis.core.Animations, on: Boolean): dev.caulis.core.Animations {
        if (!on) return now.copy(enabled = false)
        return now.copy(enabled = true, entries = now.entries + ("windows" to (now.entries["windows"]?.copy(enabled = false) ?: AnimationEntry(enabled = false))))
    }

    internal fun caulisEnabled(section: Map<String, Any?>): Boolean? = (section["enabled"] as? Boolean) ?: when (section["preset"]) {
        "on" -> true
        "none", "off" -> false
        else -> null
    }

    /** What the animation [name] comes to now. */
    operator fun get(name: String): ResolvedAnimation = settings().resolve(name)

    /** What [name] comes to, [usualMillis] and [usualCurve] its usual (what newm always did there). */
    fun resolve(name: String, usualMillis: Long? = null, usualCurve: Curve? = null): ResolvedAnimation = settings().resolve(name, usualMillis, usualCurve)

    /** The same, its usual time in seconds (config.py's way of saying it). */
    fun resolveSeconds(name: String, usualSeconds: Double, usualCurve: Curve? = null): ResolvedAnimation =
        resolve(name, Math.round(usualSeconds.coerceIn(0.0, 10.0) * 1000), usualCurve)

    /** A Compose animation for [name]: a tween of its time along its curve, or at once when off. */
    fun <T> spec(name: String, usualMillis: Long? = null, usualCurve: Curve? = null): FiniteAnimationSpec<T> = resolve(name, usualMillis, usualCurve).spec()

    /**
     * The settings in [section] (as config.kts, config.json and config.lua give them), the
     * global time [animTime] seconds unless it says; what is wrong goes to [problems].
     */
    fun read(section: Map<String, Any?>, animTime: Double, problems: MutableList<String>): AnimationSettings {
        // Curves by name: the ready ones and those of "curves".
        val curves = HashMap<String, Curve>()
        (section["curves"] as? Map<*, *>)?.forEach { (name, value) ->
            curveOf(value, "curves.$name", curves, problems)?.let { curves[name.toString()] = it }
        }
        fun named(value: Any?, path: String): Curve? = if (value == null) null else curveOf(value, path, curves, problems)

        var enabled = when (val preset = section["preset"]) {
            null -> false
            "newm", "on" -> true
            "none", "off" -> false
            else -> false.also { problems += "preset: \"$preset\" is none of newm, none (or on, off)" }
        }
        (section["enabled"] as? Boolean)?.let { enabled = it }
        val millis = (section["millis"] as? Number)?.toLong()?.takeIf { it in 0..10_000 }
            ?: Math.round(animTime.coerceIn(0.0, 10.0) * 1000).also { if (section["millis"] != null) problems += "millis is 0 to 10000, not ${section["millis"]}" }
        val curve = named(section["curve"], "curve") ?: Curve.LINEAR

        val entries = LinkedHashMap<String, AnimationEntry>()
        for ((key, value) in section) {
            if (key in GLOBAL_KEYS) continue
            val node = TREE[key]
            if (node == null) {
                problems += TREE.unknown(key)
                continue
            }
            entries[key] = entryOf(node, value, ::named, problems) ?: continue
        }
        return AnimationSettings(TREE, enabled, millis, curve, entries)
    }

    // One animation's settings: true/false, its look, or { enabled, millis or speed, curve, style }.
    private fun entryOf(node: AnimationNode, value: Any?, named: (Any?, String) -> Curve?, problems: MutableList<String>): AnimationEntry? {
        val name = node.name
        fun style(given: Any?): String? {
            val style = given?.toString() ?: return null
            return if (styleProblem(node, style) == null) style else null.also { problems += styleProblem(node, style)!! }
        }
        return when (value) {
            null -> null
            is Boolean -> AnimationEntry(enabled = value)
            is String -> AnimationEntry(style = style(value))
            is AnimationEntry -> value
            is Map<*, *> -> {
                for (k in value.keys) if (k.toString() !in ENTRY_KEYS) problems += "$name.$k: there is no such setting (there are: ${ENTRY_KEYS.joinToString()})"
                val millis = when (val m = value["millis"]) {
                    null -> (value["speed"] as? Number)?.let { Math.round(it.toDouble() * 100) }
                    is Number -> m.toLong()
                    else -> null.also { problems += "$name.millis is a number, not $m" }
                }?.let { m -> if (m in 0..10_000) m else null.also { problems += "$name: an animation takes 0 to 10000 milliseconds, not $m" } }
                AnimationEntry(value["enabled"] as? Boolean, millis, named(value["curve"], "$name.curve"), style(value["style"]))
            }
            else -> null.also { problems += "$name is true, false, a look or a table { enabled, millis, speed, curve, style }, not $value" }
        }
    }

    /** Why [style] is not a look [node] can take, or null. */
    fun styleProblem(node: AnimationNode, style: String): String? =
        if (node.styles.isEmpty()) "${node.name} has one look only (no style), not \"$style\""
        else if (FadeStyle.parse(style) == null) "${node.name}: there is no look \"$style\" (there are: ${node.styles.joinToString()})"
        else null

    // A curve: a ready one or one of [curves] by name, a Curve of config.kts, { bezier = [...] } or { spring = { ... } }.
    private fun curveOf(value: Any?, path: String, curves: Map<String, Curve>, problems: MutableList<String>): Curve? = when (value) {
        is Curve -> value
        is String -> curves[value] ?: Curve.named(value) ?: null.also { problems += "$path: there is no curve \"$value\" (there are: linear, ease-out, ease-in-out, smooth, overshoot${curves.keys.joinToString("") { ", $it" }})" }
        is Map<*, *> -> try {
            val bezier = value["bezier"] as? List<*>
            val spring = value["spring"] as? Map<*, *>
            when {
                bezier != null && bezier.size == 4 && bezier.all { it is Number } ->
                    bezier.map { (it as Number).toDouble() }.let { (a, b, c, d) -> Curve.bezier(a, b, c, d) }
                spring != null -> Curve.spring(
                    (spring["stiffness"] as? Number)?.toDouble() ?: 200.0,
                    (spring["damping"] as? Number)?.toDouble() ?: 20.0,
                    (spring["mass"] as? Number)?.toDouble() ?: 1.0,
                )
                else -> null.also { problems += "$path: a curve is { bezier = [x1, y1, x2, y2] } or { spring = { stiffness, damping, mass } }" }
            }
        } catch (e: IllegalArgumentException) {
            null.also { problems += "$path: ${e.message}" }
        }
        else -> null.also { problems += "$path: a curve's name or { bezier = [...] }, not $value" }
    }

    /**
     * How strongly the fingers' movement is smoothed (newm's low-pass inertia, 0: not at all),
     * from gestureSmoothing: its time is how soon the view catches up with the fingers.
     */
    fun gestureInertia(usualInertia: Double, frequency: Double): Double {
        val f = frequency.coerceAtLeast(1.0)
        val inertia = usualInertia.coerceIn(0.0, 0.999)
        // (The time constant of the low-pass at this frequency, and back.)
        val usualMillis = if (inertia <= 0.0) 0L else Math.round(-1000.0 / (f * ln(inertia)))
        val smoothing = resolve("gestureSmoothing", usualMillis)
        if (!smoothing.on) return 0.0
        return Math.exp(-1000.0 / (f * smoothing.millis)).coerceIn(0.0, 0.999)
    }

    private val GLOBAL_KEYS = setOf("preset", "enabled", "millis", "curve", "curves")
    private val ENTRY_KEYS = listOf("enabled", "millis", "speed", "curve", "style")
}

/**
 * A look of what comes and goes as a picture (menus, cards): "fade" (only see-through),
 * "popin 90%" (growing from 90%), "slide up 20" (rising 20 units into place; down: falling).
 */
class FadeStyle(val rise: Float, val smallest: Float) {
    companion object {
        fun parse(style: String): FadeStyle? {
            val words = style.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when (words.firstOrNull()) {
                "fade" -> FadeStyle(0f, 1f).takeIf { words.size == 1 }
                "popin", "pop" -> {
                    val word = words.getOrNull(1) ?: return FadeStyle(0f, 0.96f)
                    val n = word.removeSuffix("%").toFloatOrNull() ?: return null
                    val scale = if (word.endsWith("%") || n > 1f) n / 100f else n
                    FadeStyle(0f, scale).takeIf { scale in 0f..1f && words.size == 2 }
                }
                "slide" -> {
                    var up = true
                    var distance = 8f
                    for (word in words.drop(1)) {
                        when {
                            word == "up" -> up = true
                            word == "down" -> up = false
                            word.toFloatOrNull()?.takeIf { it in 0f..2000f } != null -> distance = word.toFloat()
                            else -> return null
                        }
                    }
                    FadeStyle(if (up) distance else -distance, 1f)
                }
                else -> null
            }
        }
    }
}
