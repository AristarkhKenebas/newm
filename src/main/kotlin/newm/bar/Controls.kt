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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
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
fun Modifier.softShadow(radius: androidx.compose.ui.unit.Dp, spread: androidx.compose.ui.unit.Dp = 10.dp, strength: Float = 0.16f): Modifier = drawBehind {
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
        BasicText(title, style = text.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** A small label above a group of rows. */
@Composable
internal fun Section(title: String, colors: BarColors, text: TextStyle) {
    BasicText(title.uppercase(), style = text.copy(color = colors.dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
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
            BasicText(title, style = text.copy(color = if (enabled) colors.text else colors.dim, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) BasicText(subtitle, style = text.copy(color = colors.dim, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        BasicText(label, style = text.copy(color = if (accent) Color.White else colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium))
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
        if (value.isEmpty()) BasicText("Password", style = text.copy(color = colors.dim))
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
