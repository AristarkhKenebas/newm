package newm.bar

import androidx.compose.ui.unit.times
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A soft shadow below a rounded shape of [radius]: a few faint rounded layers growing out by
 * [spread] (Compose's own shadow came out as a dark, hard smear on some screens). Drawn once
 * into a picture for each size and only copied after (drawn anew each frame it cost most of a
 * menu's frame on the processor).
 */
fun Modifier.softShadow(radius: androidx.compose.ui.unit.Dp, spread: androidx.compose.ui.unit.Dp = 10.dp, strength: Float = 0.16f, shown: () -> Float = { 1f }): Modifier = drawWithCache {
    val s = spread.toPx()
    val r = radius.toPx()
    val w = (size.width + 2 * s).toInt().coerceAtLeast(1)
    val h = (size.height + 2 * s).toInt().coerceAtLeast(1)
    val picture = androidx.compose.ui.graphics.ImageBitmap(w, h)
    val canvas = androidx.compose.ui.graphics.Canvas(picture)
    val paint = androidx.compose.ui.graphics.Paint()
    val steps = 6
    for (k in steps downTo 1) {
        val grow = s * k / steps
        val alpha = strength * (1f - (k - 1f) / steps) / steps * 1.6f
        paint.color = Color.Black.copy(alpha = alpha.coerceIn(0f, 1f))
        canvas.drawRoundRect(s - grow, s - grow + s * 0.25f, s + size.width + grow, s + size.height + grow + s * 0.25f, r + grow, r + grow, paint)
    }
    onDrawBehind {
        val shownNow = shown().coerceIn(0f, 1f)
        if (shownNow > 0f) drawImage(picture, topLeft = Offset(-s, -s), alpha = shownNow)
    }
}

/** Does [onClick] when clicked (released over it). */
internal fun Modifier.onClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = pointerInput(enabled, onClick) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent()
            if (e.type == PointerEventType.Release && enabled) {
                val p = e.changes.firstOrNull()?.position
                if (p == null || (p.x >= 0 && p.y >= 0 && p.x <= size.width && p.y <= size.height)) onClick()
            }
        }
    }
}

/** An on/off switch. */
@Composable
internal fun Switch(on: Boolean, colors: BarColors, onChange: (Boolean) -> Unit) {
    val knob by animateFloatAsState(if (on) 1f else 0f, newm.NewmAnimations.spec("toggles", 160))
    val track by animateColorAsState(if (on) colors.accent else colors.text.copy(alpha = 0.22f), newm.NewmAnimations.spec("toggles", 160))
    Box(Modifier.width(38.dp).height(22.dp).background(track, CircleShape).onClick { onChange(!on) }) {
        Canvas(Modifier.size(38.dp, 22.dp)) {
            val r = size.height / 2 - 3.dp.toPx()
            drawCircle(Color.White, r, Offset(size.height / 2 + knob * (size.width - size.height), size.height / 2))
        }
    }
}

/** A popup's title, with what goes at its right (a switch, a button). */
@Composable
internal fun Header(title: String, colors: BarColors, text: TextStyle, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(tr(title), style = text.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** A small label above a group of rows. */
@Composable
internal fun Section(title: String, colors: BarColors, text: TextStyle) {
    BasicText(tr(title).uppercase(), style = text.copy(color = colors.dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp))
}

/**
 * A row of a list: [leading] (an icon), a title and a line below it, [trailing] at the right;
 * highlighted when [selected], lit under the pointer, [onClick] on a click.
 */
@Composable
internal fun ListRow(
    title: String,
    colors: BarColors,
    text: TextStyle,
    subtitle: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            selected -> colors.accent.copy(alpha = 0.16f)
            hovered && onClick != null && enabled -> colors.hover
            else -> Color.Transparent
        }, newm.NewmAnimations.spec("hover", 100),
    )
    Row(
        Modifier.fillMaxWidth().height(if (subtitle != null) 42.dp else 34.dp)
            .background(bg, RoundedCornerShape(9.dp)).hoverable(source)
            .then(if (onClick != null) Modifier.onClick(enabled, onClick) else Modifier)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(tr(title), style = text.copy(color = if (enabled) colors.text else colors.dim, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) BasicText(tr(subtitle), style = text.copy(color = colors.dim, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** A small button with words. */
@Composable
internal fun SmallButton(label: String, colors: BarColors, text: TextStyle, accent: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg = when {
        accent -> if (hovered) colors.accent.copy(alpha = 0.85f) else colors.accent
        hovered -> colors.text.copy(alpha = 0.14f)
        else -> colors.text.copy(alpha = 0.08f)
    }
    Box(Modifier.height(26.dp).background(bg, RoundedCornerShape(7.dp)).hoverable(source).onClick(onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center) {
        BasicText(tr(label), style = text.copy(color = if (accent) Color.White else colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium))
    }
}

/** A password's field: hidden letters; Enter does [onSubmit], Escape [onCancel]. Takes the keys at once. */
@Composable
internal fun PasswordField(value: String, colors: BarColors, text: TextStyle, onChange: (String) -> Unit, onSubmit: () -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier.fillMaxWidth().height(30.dp).background(colors.text.copy(alpha = 0.07f), RoundedCornerShape(7.dp))
            .border(1.dp, colors.accent.copy(alpha = 0.7f), RoundedCornerShape(7.dp)).padding(horizontal = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) BasicText(tr("Password"), style = text.copy(color = colors.dim))
        BasicTextField(
            value, onChange, singleLine = true, textStyle = text, cursorBrush = SolidColor(colors.accent),
            visualTransformation = PasswordVisualTransformation(),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter -> { onSubmit(); true }
                    Key.Escape -> { onCancel(); true }
                    else -> false
                }
            },
        )
    }
}

/**
 * A level as Android 16's (Material 3 Expressive) sliders, lying down: the filled part in the
 * accent and the rest faint, a gap between them where a tall thin handle stands (taller than the
 * track, so it is easy to see and to grab), the outer ends rounded, the inner ones nearly
 * square; [glyph] at the start. [value] goes from 0 to [max] (1: 100%; the volume goes to 1.5,
 * a small mark showing where 100% is). Pressed anywhere it jumps there and follows the pointer
 * (also beyond its ends, so its ends are easy to reach); the percent shows while dragging. A
 * click on the icon does [onIcon] (mutes).
 */
@Composable
internal fun LevelSlider(
    glyph: Glyph,
    value: Float,
    colors: BarColors,
    text: TextStyle,
    label: String,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    max: Float = 1f,
    onIcon: (() -> Unit)? = null,
    /** How tall it is (the handle; the track a little less), and its icon at the end (Android's brightness). */
    height: Dp = 40.dp,
    iconAtEnd: Boolean = false,
    symbol: String? = null,
    onChange: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val animated by animateFloatAsState((value / max).coerceIn(0f, 1f), newm.NewmAnimations.spec("toggles", 160))
    val shown = dragging ?: animated
    var lastSent by remember { mutableFloatStateOf(-1f) }
    val fill = if (dim) colors.text.copy(alpha = 0.32f) else colors.accent
    val onFill = if (fill.luminance() > 0.55f) Color(0xFF1B1B1F) else Color.White
    val rest = colors.text.copy(alpha = 0.10f)
    val handleColor = if (dim) colors.dim else colors.accent
    Box(
        modifier.fillMaxWidth().height(height)
            .pointerInput(onIcon, max) {
                awaitPointerEventScope {
                    var held = false
                    var startX = 0f
                    var moved = false
                    // The handle's way: from its half width in to its half width from the end (both ends reached inside).
                    fun levelAt(x: Float): Float {
                        val inset = 10.dp.toPx()
                        return ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    }
                    while (true) {
                        val e = awaitPointerEvent()
                        val x = e.changes.firstOrNull()?.position?.x ?: continue
                        when (e.type) {
                            PointerEventType.Press -> {
                                held = true; moved = false; startX = x
                                // (On the icon: maybe a click to mute — not a jump to 0.)
                                if (onIcon == null || x > 36.dp.toPx()) dragging = levelAt(x)
                            }
                            PointerEventType.Move -> if (held) {
                                if (Math.abs(x - startX) > 3) moved = true
                                if (moved || dragging != null) dragging = levelAt(x)
                            }
                            PointerEventType.Release -> if (held) {
                                held = false
                                if (!moved && onIcon != null && x <= 36.dp.toPx()) onIcon()
                                else { val l = levelAt(x); onChange(l * max); lastSent = l }
                                dragging = null
                            }
                            else -> {}
                        }
                        val d = dragging
                        if (held && d != null && Math.abs(d - lastSent) > 0.02f) { onChange(d * max); lastSent = d }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackH = size.height - 10.dp.toPx()
            val top = (size.height - trackH) / 2
            val outer = minOf(trackH / 2.4f, 16.dp.toPx())
            val inner = 3.dp.toPx()
            val gap = 5.dp.toPx()
            val handleW = 4.dp.toPx()
            val inset = 10.dp.toPx()
            val hx = inset + shown * (size.width - 2 * inset)
            fun part(left: Float, right: Float, color: Color, leftR: Float, rightR: Float) {
                if (right - left < 1f) return
                val path = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(
                        left, top, right, top + trackH,
                        topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(leftR), bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(leftR),
                        topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(rightR), bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(rightR),
                    ))
                }
                drawPath(path, color)
            }
            part(0f, hx - gap - handleW / 2, fill, outer, inner)
            part(hx + gap + handleW / 2, size.width, rest, inner, outer)
            // Where 100% is, on a level that goes beyond it.
            if (max > 1f) {
                val mx = inset + (1f / max) * (size.width - 2 * inset)
                if (Math.abs(mx - hx) > gap * 2) drawCircle(if (mx < hx) onFill.copy(alpha = 0.6f) else colors.text.copy(alpha = 0.35f), 2.dp.toPx(), Offset(mx, size.height / 2))
            }
            // The handle: taller than the track, with clear room around it.
            drawRoundRect(handleColor, Offset(hx - handleW / 2, 0f), androidx.compose.ui.geometry.Size(handleW, size.height), androidx.compose.ui.geometry.CornerRadius(handleW / 2))
        }
        val fillWidth = shown
        if (iconAtEnd) Box(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(22.dp), contentAlignment = Alignment.Center) {
            val tint = if (fillWidth > 0.9f) onFill else colors.text
            if (symbol != null) SymbolIcon(symbol, tint, 22.dp) else GlyphIcon(glyph, tint, 18.dp, level = shown, slash = dim)
        } else Box(Modifier.padding(start = 10.dp).size(20.dp), contentAlignment = Alignment.Center) {
            if (symbol != null) SymbolIcon(symbol, if (fillWidth > 0.12f) onFill else colors.text, 18.dp)
            else GlyphIcon(glyph, if (fillWidth > 0.12f) onFill else colors.text, 16.dp, level = shown, slash = dim)
        }
        // The percent while dragging (else the level speaks for itself).
        if (dragging != null) BasicText(label, style = text.copy(color = if (iconAtEnd && fillWidth > 0.75f) onFill else colors.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = if (iconAtEnd) 46.dp else 12.dp))
    }
}

/** A tile of a row of choices (the power modes): an icon above its name, filled when chosen. */
@Composable
internal fun ChoiceTile(glyph: Glyph, label: String, chosen: Boolean, colors: BarColors, text: TextStyle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val onAccent = if (colors.accent.luminance() > 0.55f) Color(0xFF1B1B1F) else Color.White
    val bg by animateColorAsState(
        when {
            chosen -> colors.accent
            hovered -> colors.accent.copy(alpha = 0.22f)
            else -> colors.accent.copy(alpha = 0.10f)
        }, newm.NewmAnimations.spec("hover", 140),
    )
    Column(
        modifier.height(62.dp).clip(RoundedCornerShape(14.dp)).background(bg).hoverable(source).onClick(onClick = onClick).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        GlyphIcon(glyph, if (chosen) onAccent else colors.text, 18.dp)
        Spacer(Modifier.height(5.dp))
        BasicText(tr(label), style = text.copy(color = if (chosen) onAccent else colors.text, fontSize = 11.sp, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A small rounded choice ("30 min", "1 h"), filled when chosen. */
@Composable
internal fun ChoiceChip(label: String, chosen: Boolean, colors: BarColors, text: TextStyle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val onAccent = if (colors.accent.luminance() > 0.55f) Color(0xFF1B1B1F) else Color.White
    val bg = when {
        chosen -> colors.accent
        hovered -> colors.accent.copy(alpha = 0.22f)
        else -> colors.accent.copy(alpha = 0.10f)
    }
    Box(modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(bg).hoverable(source).onClick(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center) {
        BasicText(tr(label), style = text.copy(color = if (chosen) onAccent else colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

/** The colour to draw on [colors]' accent (white on a dark accent, dark on a light one). */
internal fun onAccent(colors: BarColors): Color = if (colors.accent.luminance() > 0.55f) Color(0xFF1B1B1F) else Color.White

/**
 * A row as quick settings have them: [glyph] in a round badge (the accent's when [active]), a
 * title and a line below it, [trailing] at the right. Opened ([expanded]) it becomes a card
 * with [details] below, growing smoothly.
 */
@Composable
internal fun TileRow(
    glyph: Glyph,
    title: String,
    colors: BarColors,
    text: TextStyle,
    subtitle: String? = null,
    active: Boolean = false,
    level: Float = 1f,
    expanded: Boolean = false,
    busy: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    details: @Composable () -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            expanded -> colors.text.copy(alpha = 0.07f)
            hovered && onClick != null -> colors.hover
            else -> Color.Transparent
        }, newm.NewmAnimations.spec("hover", 120),
    )
    val badge by animateColorAsState(if (active) colors.accent else colors.text.copy(alpha = 0.09f), newm.NewmAnimations.spec("toggles", 160))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg)
            .animateContentSize(newm.NewmAnimations.spec("popupsResize", null, newm.NewmAnimations.CONTROL_SPRING)),
    ) {
        Row(
            Modifier.fillMaxWidth().height(50.dp).hoverable(source)
                .then(if (onClick != null) Modifier.onClick(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
                GlyphIcon(glyph, if (active) onAccent(colors) else colors.text, 17.dp, level = level)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(tr(title), style = text.copy(fontSize = 13.5.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) BasicText(tr(subtitle), style = text.copy(color = if (busy) colors.accent else colors.dim, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
        if (expanded) Box(Modifier.fillMaxWidth().padding(start = 54.dp, end = 10.dp, bottom = 10.dp)) { details() }
    }
}

/** How a [PillButton] looks: the main one (filled), a quiet one (tinted), a warning one. */
internal enum class PillKind { PRIMARY, TONAL, DANGER }

/** A rounded button with words (and a picture before them). */
@Composable
internal fun PillButton(label: String, colors: BarColors, text: TextStyle, kind: PillKind = PillKind.TONAL, glyph: Glyph? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val (bg, fg) = when (kind) {
        PillKind.PRIMARY -> (if (hovered) colors.accent.copy(alpha = 0.88f) else colors.accent) to onAccent(colors)
        PillKind.TONAL -> colors.accent.copy(alpha = if (hovered) 0.24f else 0.15f) to colors.text
        PillKind.DANGER -> colors.danger.copy(alpha = if (hovered) 0.22f else 0.13f) to colors.danger
    }
    Row(
        modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(bg).hoverable(source).onClick(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, fg, 14.dp)
            Spacer(Modifier.width(6.dp))
        }
        BasicText(tr(label), style = text.copy(color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/** A round button with a picture alone (scan again, close). */
@Composable
internal fun IconButton(glyph: Glyph, colors: BarColors, size: Dp = 30.dp, spinning: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val turn = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(spinning) {
        // (Off: the icon stays still — never a loop of turns that take no time.)
        while (spinning && newm.NewmAnimations.resolve("spinner", 900, dev.caulis.core.Curve.LINEAR).on) {
            turn.animateTo(turn.value + 360f, newm.NewmAnimations.spec("spinner", 900, dev.caulis.core.Curve.LINEAR))
        }
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(if (hovered) colors.hover else colors.text.copy(alpha = 0.06f))
            .hoverable(source).onClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { rotationZ = turn.value }) { GlyphIcon(glyph, colors.text, size * 0.5f) }
    }
}

/**
 * Rows that scroll once there are more than [rows] of [rowHeight]: no longer than that, a thin
 * scroll bar at the right then.
 */
@Composable
internal fun ScrollingRows(colors: BarColors, count: Int, rows: Int = 5, rowHeight: Dp = 52.dp, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    val scrolls = count > rows
    Box(Modifier.fillMaxWidth().then(if (scrolls) Modifier.height(rowHeight * rows + rowHeight / 2) else Modifier)) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = if (scrolls) 12.dp else 0.dp)) { content() }
        if (scrolls) androidx.compose.foundation.VerticalScrollbar(
            androidx.compose.foundation.rememberScrollbarAdapter(scroll),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp),
            style = androidx.compose.foundation.ScrollbarStyle(
                minimalHeight = 28.dp, thickness = 5.dp, shape = RoundedCornerShape(3.dp), hoverDurationMillis = newm.NewmAnimations.resolve("hover", 150).effectiveMillis.toInt(),
                unhoverColor = colors.text.copy(alpha = 0.22f), hoverColor = colors.text.copy(alpha = 0.4f),
            ),
        )
    }
}


/**
 * The device in use, a row with an arrow ("🎧 Redmi Buds ▾"): a click unfolds the others below
 * it (smoothly), each with its picture, the one in use checked; choosing one folds it again.
 */
@Composable
internal fun <T> DevicePicker(
    current: T?,
    devices: List<T>,
    name: (T) -> String,
    glyph: (T) -> Glyph,
    chosen: (T) -> Boolean,
    colors: BarColors,
    text: TextStyle,
    onChoose: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val turn by animateFloatAsState(if (open) 180f else 0f, newm.NewmAnimations.spec("spinner", 260, newm.NewmAnimations.FAST_OUT_SLOW_IN))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (open) colors.text.copy(alpha = 0.06f) else Color.Transparent)
            .animateContentSize(newm.NewmAnimations.spec("popupsResize", 260, newm.NewmAnimations.FAST_OUT_SLOW_IN)),
    ) {
        Row(
            Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp)).background(if (hovered) colors.hover else Color.Transparent)
                .hoverable(source).onClick(enabled = devices.size > 1) { open = !open }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (current != null) GlyphIcon(glyph(current), colors.dim, 16.dp)
            Spacer(Modifier.width(10.dp))
            BasicText(current?.let(name) ?: "", style = text.copy(fontSize = 12.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (devices.size > 1) Box(Modifier.graphicsLayer { rotationZ = turn }) { GlyphIcon(Glyph.CHEVRON_DOWN, colors.dim, 15.dp) }
        }
        if (open) Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)) {
            for (d in devices) {
                val on = chosen(d)
                ListRow(verbatim(name(d)), colors, text, selected = on, onClick = { onChoose(d); open = false },
                    leading = { GlyphIcon(glyph(d), if (on) colors.accent else colors.dim, 16.dp) },
                    trailing = { if (on) GlyphIcon(Glyph.CHECK, colors.accent, 14.dp) })
            }
        }
    }
}
