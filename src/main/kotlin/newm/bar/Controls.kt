package newm.bar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
 * [spread] (Compose's own shadow came out as a dark, hard smear on some screens).
 */
fun Modifier.softShadow(radius: androidx.compose.ui.unit.Dp, spread: androidx.compose.ui.unit.Dp = 10.dp, strength: Float = 0.16f, shown: () -> Float = { 1f }): Modifier = drawBehind {
    val strength = strength * shown().coerceIn(0f, 1f)
    if (strength <= 0f) return@drawBehind
    val steps = 6
    val s = spread.toPx()
    for (k in steps downTo 1) {
        val grow = s * k / steps
        val alpha = strength * (1f - (k - 1f) / steps) / steps * 1.6f
        drawRoundRect(
            Color.Black.copy(alpha = alpha.coerceIn(0f, 1f)),
            topLeft = Offset(-grow, -grow + s * 0.25f),
            size = androidx.compose.ui.geometry.Size(size.width + 2 * grow, size.height + 2 * grow),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx() + grow),
        )
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
    val knob by animateFloatAsState(if (on) 1f else 0f, tween(160))
    val track by animateColorAsState(if (on) colors.accent else colors.text.copy(alpha = 0.22f), tween(160))
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
        }, tween(100),
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
 * A level as a Pixel's: a wide rounded track in the accent's faint tone, the level filled in
 * the accent with [glyph] inside it at the left, the percent at the right. Dragging or a
 * click sets it (now and then while dragging); a click on the icon does [onIcon] (mutes).
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
    onIcon: (() -> Unit)? = null,
    onChange: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val animated by animateFloatAsState(value.coerceIn(0f, 1f), tween(180))
    val shown = dragging ?: animated
    var lastSent by remember { mutableFloatStateOf(-1f) }
    val fill = if (dim) colors.text.copy(alpha = 0.35f) else colors.accent
    val onFill = if (fill.luminance() > 0.55f) Color(0xFF1B1B1F) else Color.White
    val height = 44.dp
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)).background(colors.accent.copy(alpha = 0.16f))
            .pointerInput(onIcon) {
                awaitPointerEventScope {
                    var held = false
                    var startX = 0f
                    var moved = false
                    while (true) {
                        val e = awaitPointerEvent()
                        val x = e.changes.firstOrNull()?.position?.x ?: continue
                        val level = (x / size.width).coerceIn(0f, 1f)
                        when (e.type) {
                            PointerEventType.Press -> { held = true; moved = false; startX = x }
                            PointerEventType.Move -> if (held) {
                                if (Math.abs(x - startX) > 4) moved = true
                                if (moved) dragging = level
                            }
                            PointerEventType.Release -> if (held) {
                                held = false
                                // A click on the icon (no drag): mute.
                                if (!moved && onIcon != null && x < size.height) onIcon() else { onChange(level); lastSent = level }
                                dragging = null
                            }
                            else -> {}
                        }
                        val d = dragging
                        if (held && d != null && Math.abs(d - lastSent) > 0.03f) { onChange(d); lastSent = d }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // The filled part never narrower than the icon's room.
            val w = maxOf(size.height, size.width * shown)
            drawRoundRect(fill, Offset.Zero, androidx.compose.ui.geometry.Size(w, size.height), androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
        }
        Box(Modifier.size(height).align(Alignment.CenterStart), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, onFill, 18.dp, level = shown, slash = dim)
        }
        BasicText(label, style = text.copy(color = if (shown > 0.86f) onFill else colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
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
        }, tween(140),
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
