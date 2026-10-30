package newm.bar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.compose.compose
import dev.caulis.core.Monitor
import dev.caulis.geometry.Rect
import newm.lockShown
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// The cards: room around them for their shadow; their height as a guess before it is measured, and their tallest.
private const val CARD_SHADOW = 16
private const val CARD_GUESS = 110
private const val CARD_TALLEST = 230

// notifications { corner = "top_right"; width = 380; gap = 10; monitor = "focused" }: where the cards are (the compositor's notices too).
private val confCorner = newm.configuredValue("notifications.corner", "top_right")
private val confWidth = newm.configuredValue("notifications.width", 380)
private val confGap = newm.configuredValue("notifications.gap", 10)
private val confMonitor = newm.configuredValue("notifications.monitor", "focused")

/** A corner of the screen (or the middle of its top or bottom edge) for the cards. */
internal enum class CardsCorner(val top: Boolean, val left: Boolean, val right: Boolean, val notices: dev.caulis.core.NoticePlacement.Toast.Corner) {
    TOP_LEFT(true, true, false, dev.caulis.core.NoticePlacement.Toast.Corner.TOP_LEFT),
    TOP(true, false, false, dev.caulis.core.NoticePlacement.Toast.Corner.TOP),
    TOP_RIGHT(true, false, true, dev.caulis.core.NoticePlacement.Toast.Corner.TOP_RIGHT),
    BOTTOM_LEFT(false, true, false, dev.caulis.core.NoticePlacement.Toast.Corner.BOTTOM_LEFT),
    BOTTOM(false, false, false, dev.caulis.core.NoticePlacement.Toast.Corner.BOTTOM),
    BOTTOM_RIGHT(false, false, true, dev.caulis.core.NoticePlacement.Toast.Corner.BOTTOM_RIGHT),
}

/** Where the cards are: notifications.corner ("top_right", "top_left", "top", "bottom_right", "bottom_left", "bottom"). */
internal fun cardsCorner(): CardsCorner = confCorner().trim().uppercase().replace('-', '_').replace(' ', '_').let { name ->
    CardsCorner.entries.firstOrNull { it.name == name } ?: CardsCorner.TOP_RIGHT
}

/** How wide the cards are (and the compositor's notices), and the gap between them and to the screen's edge. */
internal fun cardsWidth(): Int = confWidth().coerceIn(200, 800)
internal fun cardsGap(): Int = confGap().coerceIn(0, 100)

/** The notifications' cards: at the top right, below the bar, of the monitor with the focus (as notifications { } says). */
fun BarModel.showNotificationCards(): AutoCloseable {
    val center = notifications
    val caulis = wm.caulis
    // notifications.monitor: "focused" (the default), "pointer", "first", or a monitor's name ("eDP-1").
    fun target(m: Monitor): Boolean {
        val d = caulis.desktop
        val focused = runCatching { d.focusedMonitor }.getOrNull() ?: d.monitors.firstOrNull()
        val chosen = when (val which = confMonitor().trim()) {
            "focused", "" -> focused
            "pointer", "mouse" -> d.monitors.firstOrNull { it.area.contains(Rect(d.pointerX.toInt(), d.pointerY.toInt(), 1, 1)) } ?: focused
            "first", "primary" -> d.monitors.firstOrNull()
            else -> d.monitors.firstOrNull { it.name == which } ?: focused
        }
        return m === chosen
    }
    // (Not drawn while the screen is locked or a window fills it; their time waits meanwhile.)
    fun hidden() = wm.lockShown() || (caulis.desktop.focusedWindow?.fullscreen == true && !confShowInFullscreen())
    return caulis.compose(place = { m ->
        val cards = center.cards
        if (cards.isEmpty() || !target(m) || hidden()) null
        else {
            val corner = cardsCorner()
            val width = cardsWidth()
            val gap = cardsGap()
            val h = cards.sumOf { it.height.takeIf { h -> h > 0 } ?: CARD_GUESS } + gap * (cards.size - 1)
            // Below the bar at the top, above a bottom bar at the bottom.
            val y = if (corner.top) m.area.y + maxOf(wm.topBarReach(), 6) + 8 else {
                val reach = wm.barsNow().filter { !it.top && it.monitor === m }.mapNotNull { b -> b.place()?.let { m.area.bottom - it.y } }.maxOrNull() ?: 0
                m.area.bottom - maxOf(reach, 6) - 8 - h
            }
            val x = when {
                corner.left -> m.area.x + gap
                corner.right -> m.area.right - gap - width
                else -> m.area.x + (m.area.width - width) / 2
            }
            Rect(x - CARD_SHADOW, y - CARD_SHADOW, width + 2 * CARD_SHADOW, h + 2 * CARD_SHADOW)
        }
    }, sceneSize = { _ -> dev.caulis.compose.SceneSize(cardsWidth() + 2 * CARD_SHADOW, (mostCards() + 1) * (CARD_TALLEST + cardsGap()) + 2 * CARD_SHADOW) },
        appearance = { _ -> center.cardsFade.appearance(rise = if (cardsCorner().top) -10f else 10f, smallest = 0.97f) }, moving = { center.cardsFade.moving }) { _ ->
        CardStack(this, center)
    }
}

@Composable
private fun CardStack(model: BarModel, center: NotificationCenter) {
    val colors = model.colorsNow()
    // (At the bottom the newest is nearest the edge: the last.)
    val cards = if (cardsCorner().top) center.cards else center.cards.reversed()
    Column(Modifier.width((cardsWidth() + 2 * CARD_SHADOW).dp).padding(CARD_SHADOW.dp), verticalArrangement = Arrangement.spacedBy(cardsGap().dp)) {
        for (card in cards) key(card) {
            val n = center.history.find(card.id)
            if (n != null) NotificationCardView(model, center, card, n, colors)
        }
    }
}

/**
 * A notification's card drawn ahead (Warmup, on its own thread): the first real card is not
 * slow — it held newm ~0.4 s. A made-up notification, nothing kept, nothing sent.
 */
@Composable
internal fun WarmNotificationCard(model: BarModel) {
    val card = remember { NotificationCard(-1, 0L) }
    val n = remember { Notification(-1, "newm", summary = "Notification", body = "A notification drawn ahead", actions = listOf("default" to "", "open" to "Open"), at = System.currentTimeMillis()) }
    Column(Modifier.width((cardsWidth() + 2 * CARD_SHADOW).dp).padding(CARD_SHADOW.dp)) {
        NotificationCardView(model, model.notifications, card, n, model.colorsNow())
    }
}

@Composable
private fun NotificationCardView(model: BarModel, center: NotificationCenter, card: NotificationCard, n: Notification, colors: BarColors) {
    val density = LocalDensity.current
    val appear = remember(card) { Animatable(0f) }
    LaunchedEffect(card, card.leaving) {
        if (card.leaving) appear.animateTo(0f, newm.NewmAnimations.spec("notificationsOut", 220)) else appear.animateTo(1f, newm.NewmAnimations.spec("notificationsIn", 260))
    }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(hovered) { card.hovered = hovered }
    val critical = n.urgency == 2
    val shape = RoundedCornerShape(18.dp)
    val text = TextStyle(color = colors.text, fontSize = 13.sp)
    Box(
        Modifier.fillMaxWidth().wrapContentHeight()
            .onSizeChanged { size -> card.height = with(density) { size.height.toDp().value.toInt() } }
            .graphicsLayer { alpha = appear.value; translationX = (1f - appear.value) * (if (cardsCorner().left) -36f else 36f) * density.density }
            .softShadow(18.dp, 12.dp, shown = { appear.value })
            .clip(shape)
            .background(colors.island.copy(alpha = 0.97f))
            .border(1.dp, if (critical) colors.danger.copy(alpha = 0.8f) else colors.edge, shape)
            .hoverable(source)
            .tap { center.invoke(n.id) }
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 14.dp),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                AppPicture(model, n, 16.dp, appIconOnly = true)
                Spacer(Modifier.width(7.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(n.app.ifEmpty { tr("Notification") }, style = text.copy(color = colors.dim, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    BasicText(" · " + whenWords(n.at, model.now.toLocalDate()), style = text.copy(color = colors.dim, fontSize = 11.sp), maxLines = 1)
                }
                CloseButton(colors, 24.dp) { center.close(n.id, ClosedBecause.DISMISSED) }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Top) {
                val picture = center.pictures[n.id]
                if (picture != null) {
                    Image(picture, null, Modifier.padding(top = 2.dp, end = 12.dp).size(40.dp).clip(CircleShape), filterQuality = FilterQuality.High)
                }
                Column(Modifier.weight(1f).padding(end = 4.dp)) {
                    if (n.summary.isNotEmpty()) BasicText(n.summary, style = text.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (n.body.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        BasicText(n.body, style = text.copy(color = colors.text.copy(alpha = 0.82f)), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val buttons = n.buttons.take(3)
            if (buttons.isNotEmpty() && n.live) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((actionKey, label) in buttons) ActionButton(label.ifEmpty { actionKey }, colors, text, Modifier.weight(1f)) { center.invoke(n.id, actionKey) }
                }
            }
        }
    }
}

/**
 * Does [onTap] on a click let go over it, unless something inside took the click first (a
 * button on a card: not the card's own click as well).
 */
private fun Modifier.tap(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent()
            if (e.type != PointerEventType.Release) continue
            val change = e.changes.firstOrNull() ?: continue
            if (change.isConsumed) continue
            val p = change.position
            if (p.x >= 0 && p.y >= 0 && p.x <= size.width && p.y <= size.height) {
                change.consume()
                onTap()
            }
        }
    }
}

// The × of a card or a row.
@Composable
private fun CloseButton(colors: BarColors, size: Dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(Modifier.size(size).clip(CircleShape).background(if (hovered) colors.hover else Color.Transparent).hoverable(source).tap(onClick),
        contentAlignment = Alignment.Center) {
        GlyphIcon(Glyph.CLOSE, if (hovered) colors.text else colors.dim, size * 0.55f)
    }
}

// A notification's button: tinted, its words (the program's: not translated).
@Composable
private fun ActionButton(label: String, colors: BarColors, text: TextStyle, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by animateColorAsState(colors.accent.copy(alpha = if (hovered) 0.26f else 0.15f), newm.NewmAnimations.spec("hover", 120))
    Box(modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(bg).hoverable(source).tap(onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center) {
        BasicText(label, style = text.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The picture of [n]'s program: its icon (app_icon or image-path: a name of the icon theme or
 * a file), else its .desktop file's, else its name's; a bell when there is none.
 */
@Composable
private fun AppPicture(model: BarModel, n: Notification, size: Dp, appIconOnly: Boolean = false) {
    var picture by remember(n.id, n.icon, n.entry) { mutableStateOf<ImageBitmap?>(null) }
    var looked by remember(n.id, n.icon, n.entry) { mutableStateOf(false) }
    val pixels = (size.value * 2).toInt()
    val post: (() -> Unit) -> Unit = { task -> model.wm.caulis.post { task() } }
    LaunchedEffect(n.id, n.icon, n.entry) {
        // On the card's top line the program's own icon (an image-path is the notification's picture, not the program's).
        val icon = n.icon.takeIf { it.isNotBlank() && !(appIconOnly && it.startsWith("/")) }
        fun byApp() {
            val id = n.entry.ifBlank { n.app }
            if (id.isBlank()) looked = true
            else IconTheme.loadApp(id, pixels, post) { p -> picture = p; looked = true }
        }
        if (icon != null) IconTheme.load(icon, pixels, null, post) { p -> if (p != null) { picture = p; looked = true } else byApp() } else byApp()
    }
    val shown = picture
    if (shown != null) Image(shown, null, Modifier.size(size), filterQuality = FilterQuality.High)
    else Box(Modifier.size(size), contentAlignment = Alignment.Center) { if (looked) GlyphIcon(Glyph.BELL, model.colorsNow().dim, size * 0.85f) }
}

/** When [at] was, in short words: "now", "5 min", "14:32", "3 Nov 14:32". */
private fun whenWords(at: Long, today: LocalDate): String {
    val minutes = ((System.currentTimeMillis() - at) / 60_000L).toInt()
    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    return when {
        minutes < 1 -> tr("now")
        minutes < 60 -> tr("%s min").replace("%s", "$minutes")
        // (time_format: "HH:mm" by default.)
        time.toLocalDate() == today -> timeText(time)
        else -> time.format(DateTimeFormats.of("d MMM")) + " " + timeText(time)
    }
}

// --- the bell's popup ------------------------------------------------------------------------------

/**
 * The history behind the bell: newest first, by program; a click on one brings its program
 * forward (and is the end of it), its × only closes it; "Clear all" and do not disturb.
 */
@Composable
internal fun NotificationsPopup(model: BarModel, colors: BarColors, text: TextStyle) {
    val center = model.notifications
    // (The ones new when it opened keep their dot while it is open; the badge goes at once.)
    val unreadAtOpen = remember { center.history.items.filter { !it.read }.map { it.id }.toSet() }
    LaunchedEffect(Unit) { center.markAllRead() }
    val items = center.history.items
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Header("Notifications", colors, text) {
            if (items.isNotEmpty()) SmallButton("Clear all", colors, text) { center.clearAll() }
        }
        Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SymbolIcon("do_not_disturb_on", if (center.doNotDisturb) colors.accent else colors.dim, 18.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                BasicText(tr("Do not disturb"), style = text)
                BasicText(tr("No cards, the history only"), style = text.copy(color = colors.dim, fontSize = 11.sp))
            }
            Switch(center.doNotDisturb, colors) { center.setDoNotDisturb(it) }
        }
        val other = center.otherDaemon
        if (!center.serving && other != null) {
            BasicText(tr("Notifications go to %s — newm's own are off").replace("%s", other),
                style = text.copy(color = colors.warning, fontSize = 12.sp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlyphIcon(Glyph.BELL, colors.dim.copy(alpha = 0.6f), 28.dp)
                    Spacer(Modifier.height(8.dp))
                    BasicText(tr("No notifications"), style = text.copy(color = colors.dim))
                }
            }
            return@Column
        }
        // By program, the program with the newest one first.
        val groups = items.groupBy { it.entry.ifBlank { it.app }.lowercase() }
        ScrollingRows(colors, items.size + groups.size, rows = 7, rowHeight = 56.dp) {
            for ((_, list) in groups) {
                Section(verbatim(list.first().app.ifEmpty { "?" }), colors, text)
                for (n in list) key(n.id) { HistoryRow(model, center, n, n.id in unreadAtOpen, colors, text) }
            }
        }
    }
}

// One notification of the history: its picture, its words, when; a dot when new; its ×.
@Composable
private fun HistoryRow(model: BarModel, center: NotificationCenter, n: Notification, new: Boolean, colors: BarColors, text: TextStyle) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) colors.hover else Color.Transparent, newm.NewmAnimations.spec("hover", 100))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).hoverable(source)
            .tap { center.invoke(n.id, fromHistory = true) }
            .padding(start = 8.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            val picture = center.pictures[n.id]
            if (picture != null) Image(picture, null, Modifier.size(30.dp).clip(CircleShape), filterQuality = FilterQuality.High)
            else AppPicture(model, n, 26.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (new) {
                    Box(Modifier.size(7.dp).background(colors.accent, CircleShape))
                    Spacer(Modifier.width(6.dp))
                }
                BasicText(n.summary.ifEmpty { n.app }, style = text.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                BasicText(whenWords(n.at, model.now.toLocalDate()), style = text.copy(color = colors.dim, fontSize = 11.sp), maxLines = 1)
            }
            if (n.body.isNotEmpty()) BasicText(n.body, style = text.copy(color = colors.dim, fontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        CloseButton(colors, 24.dp) { center.close(n.id, ClosedBecause.DISMISSED) }
    }
}
