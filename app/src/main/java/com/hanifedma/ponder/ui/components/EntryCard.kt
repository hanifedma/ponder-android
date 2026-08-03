package com.hanifedma.ponder.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hanifedma.ponder.core.DateFmt
import com.hanifedma.ponder.core.Embeds
import com.hanifedma.ponder.data.Entry
import com.hanifedma.ponder.ui.theme.Dimens
import com.hanifedma.ponder.ui.theme.PonderTheme

/**
 * One saved entry: its text with live links, any media it mentions, and a
 * footer of tag / source / date. Delete sits in the corner and is always
 * visible — on a touch screen there is no hover to reveal it with.
 */
@Composable
fun EntryCard(
    entry: Entry,
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr

    PonderCard(
        modifier = modifier.fillMaxWidth(),
        padding = PaddingValues(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 15.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = linkifiedText(entry.text, colors.accent, onOpenUrl),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.text,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 3.dp),
                )
                PonderIconButton(
                    icon = PonderIcons.Close,
                    contentDescription = tr("aria.delete"),
                    onClick = onDelete,
                    tint = colors.faint,
                )
            }

            // Parsed once per entry rather than on every recomposition, so
            // scrolling a long list stays smooth.
            val media = remember(entry.text) { Embeds.detect(entry.text) }
            if (media.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 4.dp, end = 10.dp)
                        .widthIn(max = Dimens.mediaMaxWidth),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    media.forEach { MediaPreview(it, onOpenUrl) }
                }
            }

            EntryFooter(
                entry = entry,
                onOpenUrl = onOpenUrl,
                modifier = Modifier.padding(top = 11.dp, end = 10.dp),
            )
        }
    }
}

/**
 * Tag, source and date under an entry.
 *
 * Laid out as a flow — the same `flex-wrap: wrap` the web app's `.quote-foot`
 * uses — so each part keeps its natural width and moves to the next line when
 * the row is full. A plain Row with a weighted source would instead hand the
 * source whatever sliver the badge and date left over, and a long author name
 * would wrap one letter per line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryFooter(
    entry: Entry,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TagBadge(
            tag = entry.tag,
            label = tr.tag(entry.tag),
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        if (entry.source.isNotEmpty()) {
            SourceText(
                source = entry.source,
                onOpenUrl = onOpenUrl,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        Box(Modifier.align(Alignment.CenterVertically)) { MetaDot() }
        Text(
            text = DateFmt.format(tr.lang, entry.createdAt),
            color = colors.faint,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

/**
 * A tappable preview of linked media.
 *
 * Pressing play swaps the poster for an in-app player rather than launching
 * YouTube or Instagram, so a card can be watched without leaving the entry it
 * belongs to. Anything the app can't play itself still opens in the app that
 * owns the link.
 */
@Composable
fun MediaPreview(
    media: Embeds.Media,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr
    val shape = RoundedCornerShape(Dimens.radiusSm)

    // Reset when the card is reused for a different entry.
    var playing by remember(media.id) { mutableStateOf(false) }

    if (media.kind == Embeds.Kind.IMAGE) {
        AsyncImage(
            model = media.openUrl,
            contentDescription = media.label,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(shape)
                .background(colors.surface2)
                .border(1.dp, colors.border, shape)
                .clickable { onOpenUrl(media.openUrl) },
        )
        return
    }

    // Instagram embeds are a tall card rather than a video frame.
    val ratio = if (media.kind == Embeds.Kind.INSTAGRAM) 0.8f else 16f / 9f

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) {
            InlineWebPlayer(
                media = media,
                modifier = Modifier.fillMaxSize(),
                onOpenExternally = { onOpenUrl(media.openUrl) },
            )
            // Always leave a way out to the original site.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { onOpenUrl(media.openUrl) }
                    .padding(7.dp),
            ) {
                Icon(
                    PonderIcons.OpenInNew,
                    contentDescription = tr.f("media.open", "label" to media.label),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            PosterFacade(
                media = media,
                onPlay = {
                    if (media.isPlayable) playing = true else onOpenUrl(media.openUrl)
                },
            )
        }
    }
}

/** The still shown before playback starts: a thumbnail when the service has one. */
@Composable
private fun PosterFacade(media: Embeds.Media, onPlay: () -> Unit) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr

    Box(
        Modifier
            .fillMaxSize()
            .background(if (media.thumbUrl != null) Color.Black else colors.surface2)
            .clickable(onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        if (media.thumbUrl != null) {
            AsyncImage(
                model = media.thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PonderIcons.PlayArrow,
                    contentDescription = tr.f("media.play", "label" to media.label),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            // The thumbnail already says what a YouTube video is; the others don't.
            if (media.thumbUrl == null) {
                Text(
                    text = tr.f("media.play", "label" to media.label),
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Placeholder cards shown while the first cloud snapshot is on its way. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val colors = PonderTheme.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shimmer by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )

    PonderCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonLine(1f, shimmer, colors.surface2)
            SkeletonLine(0.75f, shimmer, colors.surface2)
            SkeletonLine(0.32f, shimmer, colors.surface2)
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, alpha: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .alpha(alpha)
            .background(color)
    )
}

/** The illustrated "nothing here" / "no matches" panel. */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val colors = PonderTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, fontSize = 34.sp)
        Text(
            title,
            color = colors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(subtitle, color = colors.muted, fontSize = 14.sp)
    }
}
