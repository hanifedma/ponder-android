package com.hanifedma.ponder.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.ponder.core.LinkSpans
import com.hanifedma.ponder.ui.theme.Dimens
import com.hanifedma.ponder.ui.theme.PonderTheme
import com.hanifedma.ponder.ui.theme.TagColors

// ----------------------------------------------------------------- containers

/** `.card`: surface fill, hairline border, 14dp corners. */
@Composable
fun PonderCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Dimens.cardPadding),
    content: @Composable () -> Unit,
) {
    val colors = PonderTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(Dimens.radius))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(Dimens.radius))
            .padding(padding)
    ) { content() }
}

// ------------------------------------------------------------------- buttons

enum class ButtonVariant { PRIMARY, GHOST }

@Composable
fun PonderButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.GHOST,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    /** Leaves a multi-colour icon (the Google mark) untinted. */
    preserveIconColors: Boolean = false,
) {
    val colors = PonderTheme.colors
    val primary = variant == ButtonVariant.PRIMARY
    val background = if (primary) colors.accent else Color.Transparent
    val border = if (primary) colors.accent else colors.border
    val content = if (primary) colors.accentContrast else colors.text
    val alpha = if (enabled) 1f else 0.5f

    Row(
        modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(background.copy(alpha = background.alpha * alpha))
            .border(1.dp, border.copy(alpha = border.alpha * alpha), RoundedCornerShape(Dimens.radiusSm))
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = Dimens.controlHeight)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (preserveIconColors) Color.Unspecified else content.copy(alpha = alpha),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An icon-only button sized for comfortable tapping. */
@Composable
fun PonderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = PonderTheme.colors.muted,
    bordered: Boolean = false,
) {
    val colors = PonderTheme.colors
    Box(
        modifier
            .size(Dimens.controlHeight)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .then(
                if (bordered) {
                    Modifier
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(Dimens.radiusSm))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Google's own button styling — white, their mark, their wording. */
@Composable
fun GoogleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.6f))
            .border(1.dp, Color(0xFFDADCE0), RoundedCornerShape(Dimens.radiusSm))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            PonderIcons.Google,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 10.dp),
            color = Color(0xFF1F1F1F),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// --------------------------------------------------------------- text inputs

/**
 * `.input`: a filled field that keeps its border invisible until focus, then
 * outlines in the accent colour.
 */
@Composable
fun PonderTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    filled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    contentDescription: String? = null,
) {
    val colors = PonderTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val background = when {
        focused -> colors.surface
        filled -> colors.surface2
        else -> colors.surface
    }
    val borderColor = when {
        focused -> colors.accent
        filled -> Color.Transparent
        else -> colors.border
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(Dimens.radiusSm))
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        textStyle = TextStyle(color = colors.text, fontSize = 15.sp, lineHeight = 23.sp),
        cursorBrush = SolidColor(colors.accent),
        singleLine = singleLine,
        minLines = minLines,
        interactionSource = interactionSource,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Row(
                Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = colors.faint,
                        modifier = Modifier
                            .padding(end = 9.dp)
                            .size(18.dp),
                    )
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.faint,
                            fontSize = 15.sp,
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

// ---------------------------------------------------------------- selections

/** A `<select>`: a bordered field that drops a menu of choices. */
@Composable
fun <T> PonderSelect(
    selected: T,
    options: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    filled: Boolean = false,
) {
    val colors = PonderTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(if (filled) colors.surface2 else colors.surface)
                .border(
                    1.dp,
                    if (filled) Color.Transparent else colors.border,
                    RoundedCornerShape(Dimens.radiusSm),
                )
                .clickable { expanded = true }
                .defaultMinSize(minHeight = Dimens.controlHeight)
                .padding(start = 13.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = labelOf(selected),
                color = colors.text,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                PonderIcons.ArrowDropDown,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.elevated,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            labelOf(option),
                            color = if (option == selected) colors.accent else colors.text,
                            fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

// ------------------------------------------------------------------- badges

/** `.badge`: a pill tinted with the tag's own hue. */
@Composable
fun TagBadge(tag: String, label: String, modifier: Modifier = Modifier) {
    val colors = PonderTheme.colors
    val accent = TagColors.accentFor(tag)
    val fill = TagColors.fillFor(tag) ?: Color.Transparent
    val outline = TagColors.outlineFor(tag) ?: Color.Transparent

    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(fill)
            .border(1.dp, outline, RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 3.dp),
        color = accent ?: colors.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

/** The neutral "saved on this device" / "offline" pill. */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier) {
    val colors = PonderTheme.colors
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 3.dp),
        color = colors.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

// ------------------------------------------------------------ space switcher

/**
 * The segmented control from the top of the web app, pill and all. Tabs share
 * the width evenly, so the highlight can simply slide to the selected index.
 */
@Composable
fun SpaceSwitcher(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PonderTheme.colors
    if (labels.isEmpty()) return

    BoxWithConstraints(
        modifier
            .height(Dimens.controlHeight)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(colors.surface2)
            .border(1.dp, colors.border, RoundedCornerShape(Dimens.radiusSm))
            .padding(3.dp)
    ) {
        val tabWidth = maxWidth / labels.size
        val index = selectedIndex.coerceIn(0, labels.lastIndex)
        val offset by animateDpAsState(
            targetValue = tabWidth * index,
            animationSpec = tween(durationMillis = 260),
            label = "spacePill",
        )

        Box(
            Modifier
                .offset(x = offset)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface)
        )
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (i == index) colors.text else colors.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------- linkify text

/**
 * Renders entry text with real, tappable links. Nothing is ever parsed as
 * markup — plain and link runs are appended separately.
 */
@Composable
fun linkifiedText(
    text: String,
    linkColor: Color = PonderTheme.colors.accent,
    onOpenUrl: (String) -> Unit,
): AnnotatedString {
    val segments = remember(text) { LinkSpans.split(text) }
    return remember(segments, linkColor, onOpenUrl) {
        buildAnnotatedString {
            for (segment in segments) {
                when (segment) {
                    is LinkSpans.Segment.Text -> append(segment.value)
                    is LinkSpans.Segment.Link -> {
                        val link = LinkAnnotation.Url(
                            url = segment.url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                )
                            ),
                            linkInteractionListener = { onOpenUrl(segment.url) },
                        )
                        withLink(link) { append(segment.display) }
                    }
                }
            }
        }
    }
}

/** The italic "— Source" run under an entry. */
@Composable
fun SourceText(source: String, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = PonderTheme.colors
    CompositionLocalProvider(LocalContentColor provides colors.muted) {
        Text(
            text = buildAnnotatedString {
                append("— ")
                append(linkifiedText(source, colors.accent, onOpenUrl))
            },
            modifier = modifier,
            color = colors.muted,
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
        )
    }
}

/** A translucent divider dot, matching `.dot`. */
@Composable
fun MetaDot() {
    Text("·", color = PonderTheme.colors.faint, fontSize = 13.sp)
}

/** Thin wrapper so surfaces inherit the palette without repeating themselves. */
@Composable
fun PonderSurface(
    modifier: Modifier = Modifier,
    color: Color = PonderTheme.colors.surface,
    border: BorderStroke? = null,
    shape: RoundedCornerShape = RoundedCornerShape(Dimens.radius),
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, color = color, border = border, shape = shape, content = content)
}
