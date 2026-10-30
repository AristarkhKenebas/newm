package newm

import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.core.appearance.ColorScheme
import dev.caulis.log.Logger
import newm.bar.BarColors
import newm.bar.softShadow
import newm.bar.IconTheme
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("launcher")

// launcher { frequent = 6; remember = true; hide = listOf(...); runTyped = false; entries { }; keys { } }
private val confFrequent = configuredValue("panels.launcher.frequent", 6)
private val confRemember = configuredValue("panels.launcher.remember", true)
private val confHide = configuredValue("panels.launcher.hide", emptyList<String>())
private val confRunTyped = configuredValue("panels.launcher.run_typed", false)
private val confEntries = configuredValue("panels.launcher.entries", emptyList<Map<String, Any?>>())
private val confKeys = configuredValue("panels.launcher.keys", emptyMap<String, Any?>())
// launcher { typos = 1 }: how many letters a word typed may have wrong and still find (0: none).
private val confTypos = configuredValue("panels.launcher.typos", 1)
// launcher { search = listOf(...) }: what the search finds besides programs.
internal val LAUNCHER_SOURCES = listOf("programs", "actions", "files", "calculator", "command")
private val confSearch = configuredValue("panels.launcher.search", LAUNCHER_SOURCES)
private val confCommandPrefix = configuredValue("panels.launcher.command_prefix", ">")
private val confRecentFiles = configuredValue("panels.launcher.recent_files", 200)
private val confOpenWith = configuredValue("panels.launcher.open_with", "xdg-open")
private val confPerKind = configuredValue("panels.launcher.per_kind", 6)
// launcher { pinned = listOf("firefox", "foot"); recent = 0 }: programs always first, and those started last.
private val confPinned = configuredValue("panels.launcher.pinned", emptyList<String>())
private val confRecent = configuredValue("panels.launcher.recent", 0)

/**
 * What a key of `launcher { keys { "C-n" { next() } } }` can do: choose the next or the
 * previous entry, the first or the last, start the chosen one, run what was typed as a
 * command, close the launcher.
 */
class LauncherControl internal constructor(
    private val step: (Int) -> Unit,
    private val jump: (Boolean) -> Unit,
    private val startChosen: () -> Unit,
    private val runText: () -> Unit,
    private val closeIt: () -> Unit,
) {
    fun next() = step(1)
    fun previous() = step(-1)
    fun first() = jump(true)
    fun last() = jump(false)
    fun start() = startChosen()
    fun runTyped() = runText()
    fun close() = closeIt()

    companion object {
        /** A launcher action by its name in config.json / config.lua ("next", "previous", "run_typed", ...), or null. */
        fun byName(name: String): ((LauncherControl) -> Unit)? = when (name.trim().replace('-', '_').lowercase()) {
            "next", "down" -> { c -> c.next() }
            "previous", "prev", "up" -> { c -> c.previous() }
            "first" -> { c -> c.first() }
            "last" -> { c -> c.last() }
            "start", "choose" -> { c -> c.start() }
            "run_typed", "runtyped", "run" -> { c -> c.runTyped() }
            "close", "exit" -> { c -> c.close() }
            else -> null
        }
    }
}

/** Whether the key event [e] is [combo] in newm's notation ("C-n", "C-S-Tab", "A-j"; "C-N" is "C-S-n"). */
internal fun keyEventIs(combo: String, e: androidx.compose.ui.input.key.KeyEvent): Boolean {
    val parts = combo.trim().split('-')
    if (parts.isEmpty() || parts.last().isEmpty()) return false
    val mods = parts.dropLast(1).toMutableSet()
    var name = parts.last()
    if (name.length == 1 && name[0].isLetter() && name != name.lowercase()) {
        mods += "S"
        name = name.lowercase()
    }
    val key = composeKeyOf(name) ?: return false
    return e.key == key && e.isCtrlPressed == ("C" in mods) && e.isShiftPressed == ("S" in mods) &&
        e.isAltPressed == ("A" in mods || "1" in mods) && e.isMetaPressed == ("L" in mods)
}

private val LETTER_KEYS = listOf(Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z)
private val DIGIT_KEYS = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

// A key's name (xkb's, as in keys { }) as Compose knows it.
private fun composeKeyOf(name: String): Key? = when {
    name.length == 1 && name[0] in 'a'..'z' -> LETTER_KEYS[name[0] - 'a']
    name.length == 1 && name[0] in '0'..'9' -> DIGIT_KEYS[name[0] - '0']
    else -> when (name.lowercase()) {
        "tab" -> Key.Tab
        "return", "enter" -> Key.Enter
        "escape", "esc" -> Key.Escape
        "up" -> Key.DirectionUp
        "down" -> Key.DirectionDown
        "left" -> Key.DirectionLeft
        "right" -> Key.DirectionRight
        "home" -> Key.MoveHome
        "end" -> Key.MoveEnd
        "prior", "page_up", "pageup" -> Key.PageUp
        "next", "page_down", "pagedown" -> Key.PageDown
        "delete" -> Key.Delete
        "backspace" -> Key.Backspace
        "space" -> Key.Spacebar
        else -> null
    }
}

/** How often each program was started from the launcher (~/.local/state/newm/launcher.json): the frequent ones come first. */
internal object LaunchCounts {
    /** How many of the programs started last are kept (launcher { recent } shows up to so many). */
    const val RECENT_KEPT = 24

    private val file: Path by lazy {
        val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.startsWith("/") } ?: "${System.getenv("HOME") ?: System.getProperty("user.home")}/.local/state"
        Path.of(state, "newm", "launcher.json")
    }
    private val counts: MutableMap<String, Int> by lazy {
        try {
            Json.parseObject(Files.readString(file)).mapValuesTo(HashMap()) { (it.value as? Number)?.toInt() ?: 0 }
        } catch (_: Exception) {
            HashMap()
        }
    }

    fun of(app: DesktopApp): Int = if (!confRemember()) 0 else counts[app.id.ifEmpty { app.name }] ?: 0

    // The programs started last, newest first (~/.local/state/newm/launcher-recent.json; kept only with remember).
    private val recentFile: Path by lazy { file.resolveSibling("launcher-recent.json") }
    private val recentIds: MutableList<String> by lazy {
        try {
            Json.parseArray(Files.readString(recentFile)).mapNotNullTo(ArrayList()) { it as? String }
        } catch (_: Exception) {
            ArrayList()
        }
    }

    /** The programs started last (their ids, newest first), at most [most]. */
    fun recent(most: Int): List<String> = if (!confRemember() || most <= 0) emptyList() else recentIds.take(most)

    fun started(app: DesktopApp) {
        // launcher { remember = false }: nothing written, nothing counted.
        if (!confRemember()) return
        val key = app.id.ifEmpty { app.name }
        counts[key] = (counts[key] ?: 0) + 1
        // (The newest first; only as many kept as could ever be shown.)
        recentIds.remove(key)
        recentIds.add(0, key)
        while (recentIds.size > RECENT_KEPT) recentIds.removeAt(recentIds.size - 1)
        try {
            privateFolder(file.parent)
            Files.writeString(recentFile, Json.write(recentIds))
            Files.writeString(file, Json.write(counts))
        } catch (e: Exception) {
            log.warn("The launcher's counts cannot be kept: $e")
        }
    }
}

// What the search finds now (launcher { search }): see [universalResults].
private fun searchEverything(all: List<DesktopApp>, query: String, sources: Set<String>, files: List<RecentFile>): List<DesktopApp> =
    universalResults(all, if ("actions" in sources) launcherActions() else emptyList(), files, query, sources,
        confTypos(), confPerKind(), confCommandPrefix(), confOpenWith(), copy = { l, text ->
            log.info { "The sum's result copied" }
            l.caulis.clipboard.setClipboard(dev.caulis.core.TextOnClipboard(text))
        })

/**
 * newm's launcher (Logo+D, five fingers): a search in the upper middle of the screen. Type a
 * few letters, Enter starts the first; the arrows or the pointer choose another; Escape or
 * Logo+D again closes it. With nothing typed: the programs you start most, then all.
 */
@Composable
internal fun LauncherPanel(wm: Layout, apps: List<DesktopApp>, opened: Int, closing: Boolean = false) {
    val colors = wm.barModel.colorsNow()
    var query by remember(opened) { mutableStateOf("") }
    var selected by remember(opened) { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val icons = remember { mutableStateMapOf<String, ImageBitmap?>() }
    // (It comes and goes as newm's launcher animation runs: the compositor shows it see-through
    // and smaller then, nothing here is drawn anew for it.)
    LaunchedEffect(opened) { runCatching { focus.requestFocus() } }

    // The wheel and the touchpad: what they scroll is added up and the list glides after it, a
    // frame at a time while there is something left (Compose scrolled by whole steps: jerky).
    var scrollWanted by remember(opened) { mutableStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var gliding by remember(opened) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun glide(by: Float) {
        scrollWanted += by
        if (gliding?.isActive == true) return
        gliding = scope.launch {
            while (Math.abs(scrollWanted) > 0.5f) {
                androidx.compose.runtime.withFrameNanos { }
                val step = scrollWanted * 0.3f
                list.scrollBy(step)
                scrollWanted -= step
            }
            scrollWanted = 0f
        }
    }

    // The programs and the entries of the configuration (launcher { entries { } }), less those
    // left out (launcher { hide }); while windows of the last session wait, an entry for them first.
    val all = remember(apps, opened) {
        val hide = confHide().map { it.lowercase() }.toSet()
        val own = confEntries().mapNotNull { e ->
            val name = e["name"]?.toString() ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val action = e["action"] as? (Layout) -> Unit ?: return@mapNotNull null
            DesktopApp(name, e["comment"]?.toString().orEmpty(), "", "", id = "entry:$name", run = action, kind = "entry")
        }
        val rest = wm.session.restLeft()
        val reopen = if (rest > 0) listOf(DesktopApp(newm.bar.tr("Reopen %s windows").replace("%s", rest.toString()), newm.bar.tr("Left out when the session came back"),
            "", "", id = "entry:reopen", run = { l -> l.session.restoreRest() })) else emptyList()
        reopen + own + apps.filter { it.name.lowercase() !in hide && it.id.lowercase() !in hide }
    }
    // Nothing typed: the frequent ones first (then by name); else the matches, the frequent first among equals.
    val frequentMost = confFrequent().coerceAtLeast(0)
    // The files used lately: read once the search has something typed (and only then), away
    // from the compositor's thread; dropped with the launcher's scene.
    val sources = confSearch().map { it.trim().lowercase() }.toSet()
    var recentFiles by remember(opened) { mutableStateOf<List<RecentFile>?>(null) }
    LaunchedEffect(opened, query.isNotBlank()) {
        if (query.isBlank() || recentFiles != null || "files" !in sources) return@LaunchedEffect
        recentFiles = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { RecentFiles.read(most = confRecentFiles()) }
        log.trace { "${recentFiles?.size} files used lately, for the search" }
    }
    val shown = remember(all, query, recentFiles) {
        if (query.isBlank()) {
            if (frequentMost > 0) all.sortedWith(compareByDescending<DesktopApp> { it.id == "entry:reopen" }.thenByDescending { LaunchCounts.of(it) }.thenBy { it.name.lowercase() })
            else all.sortedWith(compareByDescending<DesktopApp> { it.id == "entry:reopen" }.thenBy { it.name.lowercase() })
        } else searchEverything(all, query, sources, recentFiles.orEmpty())
    }
    val kindsShown = remember(shown) { shown.map { it.group }.distinct().size }
    val frequent = if (query.isBlank() && frequentMost > 0 && shown.firstOrNull()?.id != "entry:reopen") shown.takeWhile { LaunchCounts.of(it) > 0 }.take(frequentMost).size else 0
    // Nothing typed: the pinned programs first, then those started last (launcher { pinned; recent }).
    val (pinnedCount, head) = remember(all, query.isBlank()) {
        if (query.isNotBlank()) 0 to emptyList() else {
            val pinned = pinnedApps(all, confPinned())
            val recentIds = LaunchCounts.recent(confRecent())
            val byId = all.associateBy { it.id.ifEmpty { it.name } }
            val recent = recentIds.mapNotNull { byId[it] }.filter { it !in pinned }
            pinned.size to pinned + recent
        }
    }
    val listed = if (head.isEmpty()) shown else head + shown
    // (Kept in the list, not read here: reading it while composing composed the whole list anew at each row the pointer passed.)
    LaunchedEffect(listed) { if (selected >= listed.size) selected = (listed.size - 1).coerceAtLeast(0) }
    // Chosen with the keys: scrolled into sight if it is not (the pointer only chooses what it is on:
    // scrolling then moved the list under a still pointer, which chose the next one, on and on).
    var byKeys by remember(opened) { mutableIntStateOf(0) }
    LaunchedEffect(byKeys) {
        if (byKeys == 0 || listed.isEmpty()) return@LaunchedEffect
        val visible = list.layoutInfo.visibleItemsInfo.filter { it.offset >= 0 && it.offset + it.size <= list.layoutInfo.viewportEndOffset }.map { it.index }
        if (selected !in visible) {
            // Below: it becomes the last one in sight; above: the first.
            val target = if (selected > (visible.maxOrNull() ?: 0)) selected - visible.size + 1 else selected
            if (NewmAnimations["scroll"].on) list.animateScrollToItem(target.coerceAtLeast(0)) else list.scrollToItem(target.coerceAtLeast(0))
        }
    }

    fun start(app: DesktopApp) {
        if (app.kind == "app" || app.kind == "entry") LaunchCounts.started(app)
        query = ""
        val run = app.run
        if (run != null) {
            // (A file's name or a command typed is nobody's business in the log: only what kind it was.)
            log.info { if (app.kind == "entry") "Launcher entry: ${app.name}" else "Launcher: a result of kind ${app.kind}" }
            wm.exitOverlay()
            run(wm)
        } else {
            log.info { "Starting ${app.name}" }
            wm.launchApp(app.command)
        }
    }

    // launcher { runTyped = true }: what was typed, as a command.
    fun runTyped() {
        val command = query.trim()
        if (command.isEmpty()) return
        log.info { "Running what was typed: $command" }
        query = ""
        wm.launchApp(command)
    }

    val control = LauncherControl(
        step = { by -> if (listed.isNotEmpty()) { selected = (selected + by).coerceIn(0, listed.size - 1); byKeys++ } },
        jump = { toFirst -> if (listed.isNotEmpty()) { selected = if (toFirst) 0 else listed.size - 1; byKeys++ } },
        startChosen = { listed.getOrNull(selected)?.let(::start) },
        runText = ::runTyped,
        closeIt = { wm.exitOverlay() },
    )

    val text = TextStyle(color = colors.text, fontSize = 14.sp)
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.fillMaxSize().padding(16.dp)
            .softShadow(20.dp, 14.dp)
            .background(colors.island.copy(alpha = 0.97f), shape)
            .border(1.dp, colors.edge, shape),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            // The search.
            Row(Modifier.fillMaxWidth().height(50.dp).background(colors.text.copy(alpha = 0.06f), RoundedCornerShape(13.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(18.dp)) {
                    val r = size.minDimension * 0.33f
                    drawCircle(colors.dim, r, Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(2.dp.toPx()))
                    drawLine(colors.dim, Offset(size.width * 0.66f, size.height * 0.66f), Offset(size.width * 0.92f, size.height * 0.92f), 2.dp.toPx(), StrokeCap.Round)
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) BasicText(newm.bar.tr(if (sources.size > 1) "Search programs, files, actions" else "Search programs"), style = text.copy(color = colors.dim, fontSize = 18.sp))
                    BasicTextField(
                        query, { query = it; selected = 0; byKeys++ },
                        Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            // The configuration's keys first (launcher { keys { "C-n" { next() } } }).
                            for ((combo, action) in confKeys()) {
                                if (!keyEventIs(combo, e)) continue
                                @Suppress("UNCHECKED_CAST")
                                (action as? (LauncherControl) -> Unit)?.invoke(control)
                                return@onPreviewKeyEvent true
                            }
                            val ctrl = e.isCtrlPressed && !e.isAltPressed && !e.isMetaPressed
                            when {
                                e.key == Key.Tab && e.isShiftPressed -> { control.previous(); true }
                                e.key == Key.DirectionDown || e.key == Key.Tab || ctrl && (e.key == Key.N || e.key == Key.J) -> { control.next(); true }
                                e.key == Key.DirectionUp || ctrl && (e.key == Key.P || e.key == Key.K) -> { control.previous(); true }
                                e.key == Key.Enter || e.key == Key.NumPadEnter -> {
                                    if (shown.isEmpty() && confRunTyped()) runTyped() else control.start()
                                    true
                                }
                                e.key == Key.Escape -> { control.close(); true }
                                else -> false
                            }
                        },
                        singleLine = true,
                        textStyle = text.copy(fontSize = 18.sp),
                        cursorBrush = SolidColor(colors.accent),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (listed.isEmpty()) {
                BasicText(if (apps.isEmpty()) newm.bar.tr("Looking for programs…") else newm.bar.tr(if (confRunTyped()) "Nothing called “%s” — Enter runs it" else "Nothing called “%s”").replace("%s", query), style = text.copy(color = colors.dim), modifier = Modifier.padding(14.dp))
            }
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(opened) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (e.type == PointerEventType.Scroll) {
                            val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            glide(dy * 64.dp.toPx())
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
            }) {
                // (A pinned or recent program is in the list below too: its key says which place it is.)
                itemsIndexed(listed, key = { i, app -> (if (i < head.size) "head:" else "") + app.id.ifEmpty { app.name } }) { i, app ->
                    if (query.isBlank() && i == 0 && pinnedCount > 0) Label(newm.bar.tr("Pinned"), colors)
                    if (query.isBlank() && i == pinnedCount && head.size > pinnedCount) Label(newm.bar.tr("Recent"), colors)
                    if (query.isBlank() && i == head.size && frequent > 0) Label(newm.bar.tr("Frequent"), colors)
                    if (query.isBlank() && i == head.size + frequent) Label(newm.bar.tr("All programs"), colors)
                    // Something typed: the kind of what follows, where it changes (when more than programs were found).
                    if (query.isNotBlank() && kindsShown > 1 && (i == 0 || listed[i - 1].group != app.group)) Label(newm.bar.tr(app.group), colors)
                    AppRow(app, { selected == i }, colors, text, icons, wm, intro = -1, onHover = { selected = i }, onClick = { start(app) })
                }
            }
        }
    }
}

@Composable
private fun Label(title: String, colors: BarColors) {
    BasicText(title.uppercase(), style = TextStyle(color = colors.dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
        modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp))
}

@Composable
private fun AppRow(app: DesktopApp, selected: () -> Boolean, colors: BarColors, text: TextStyle, icons: MutableMap<String, ImageBitmap?>, wm: Layout, intro: Int, onHover: () -> Unit, onClick: () -> Unit) {
    // The first rows come in one after another ([intro]: its place; -1: shown at once).
    // (Without the animation "launcherList": all there at once.)
    val coming = intro >= 0 && NewmAnimations["launcherList"].on
    val come = remember { Animatable(if (coming) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (coming) {
            kotlinx.coroutines.delay(40L + intro * 22L)
            come.animateTo(1f, NewmAnimations.spec("launcherList"))
        }
    }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    // The chosen one lit: read only while drawing, so choosing another redraws two rows, not
    // the whole list composed anew (that cost a heavy frame at each row the pointer passed).
    val lit = colors.accent.copy(alpha = 0.18f)
    // (Results that are not programs draw a glyph: no icon looked up for them.)
    val key = if (app.kind == "app" || app.kind == "entry") app.icon.ifEmpty { app.id } else ""
    val iconPixels = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.roundToPx() }
    LaunchedEffect(key) {
        if (key.isNotEmpty() && key !in icons) {
            icons[key] = null
            // At the size it is drawn in pixels: drawn as it is, not shrunk anew each frame.
            IconTheme.load(key, iconPixels, null, post = { wm.caulis.post { it() } }) { icons[key] = it }
        }
    }
    Row(
        Modifier.fillMaxWidth().height(58.dp)
            // A layer of its own only while it comes in (each one costs a picture drawn apart).
            .then(if (come.value < 1f) Modifier.graphicsLayer {
                alpha = come.value
                translationY = (1 - come.value) * 10f * density
            } else Modifier)
            .drawBehind { if (selected()) drawRoundRect(lit, cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())) }.hoverable(source)
            .pointerInput(onClick) { awaitPointerEventScope { while (true) if (awaitPointerEvent().type == PointerEventType.Release) onClick() } }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val picture = icons[key]
        // What is not a program: a picture of its kind.
        val glyph = when (app.kind) {
            "calc" -> newm.bar.Glyph.CALCULATOR
            "command" -> newm.bar.Glyph.TERMINAL
            "action" -> newm.bar.Glyph.SETTINGS
            "file" -> newm.bar.Glyph.FILE
            else -> null
        }
        if (glyph != null) {
            Box(Modifier.size(40.dp).background(colors.accent.copy(alpha = 0.16f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                newm.bar.GlyphIcon(glyph, colors.text, 20.dp)
            }
        } else if (picture != null) {
            Image(picture, null, Modifier.size(40.dp), filterQuality = FilterQuality.Low)
        } else {
            Box(Modifier.size(40.dp).background(colors.accent.copy(alpha = 0.25f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                BasicText(app.name.take(1).uppercase(), style = text.copy(fontWeight = FontWeight.Bold))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(app.name, style = text.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (app.comment.isNotBlank()) BasicText(if (app.kind == "calc" || app.kind == "command") newm.bar.tr(app.comment) else app.comment, style = text.copy(color = colors.dim, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BasicText("↵", style = text.copy(color = colors.dim, fontSize = 14.sp), modifier = Modifier.graphicsLayer { alpha = if (selected()) 1f else 0f })
    }
}
