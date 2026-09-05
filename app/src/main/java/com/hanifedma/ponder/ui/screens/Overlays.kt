package com.hanifedma.ponder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.ponder.core.DateFmt
import com.hanifedma.ponder.core.Embeds
import com.hanifedma.ponder.core.Similarity
import com.hanifedma.ponder.core.SortOrder
import com.hanifedma.ponder.data.Entry
import com.hanifedma.ponder.data.Prefs
import com.hanifedma.ponder.data.Space
import com.hanifedma.ponder.data.Spaces
import com.hanifedma.ponder.data.ThoughtPool
import com.hanifedma.ponder.ui.ShuffleState
import com.hanifedma.ponder.ui.components.ButtonVariant
import com.hanifedma.ponder.ui.components.EntryFooter
import com.hanifedma.ponder.ui.components.MediaPreview
import com.hanifedma.ponder.ui.components.MetaDot
import com.hanifedma.ponder.ui.components.PonderButton
import com.hanifedma.ponder.ui.components.PonderCard
import com.hanifedma.ponder.ui.components.PonderIconButton
import com.hanifedma.ponder.ui.components.PonderIcons
import com.hanifedma.ponder.ui.components.PonderSelect
import com.hanifedma.ponder.ui.components.SourceText
import com.hanifedma.ponder.ui.components.TagBadge
import com.hanifedma.ponder.ui.components.linkifiedText
import com.hanifedma.ponder.ui.theme.Dimens
import com.hanifedma.ponder.ui.theme.PonderTheme
import kotlin.math.abs

/**
 * One random entry at a time. Tap the card, swipe it, or press the button for
 * another — the same three gestures the web app offers.
 */
@Composable
fun ShuffleOverlay(
    shuffle: ShuffleState,
    entry: Entry?,
    space: Space,
    onSetTag: (String?) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 45.dp.toPx() } }

    // "All tags" is modelled as an empty string so the select can hold Strings.
    val allValue = ""
    val options = remember(space.key) { listOf(allValue) + space.tags }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PonderSelect(
                    selected = shuffle.tagFilter ?: allValue,
                    options = options,
                    labelOf = { if (it == allValue) tr("shuffle.all") else tr.tag(it) },
                    onSelect = { onSetTag(if (it == allValue) null else it) },
                    contentDescription = tr("aria.shuffleTag"),
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 260.dp),
                )
                Box(Modifier.weight(0.001f))
                PonderIconButton(
                    icon = PonderIcons.Close,
                    contentDescription = tr("aria.shuffleClose"),
                    onClick = onClose,
                    tint = colors.muted,
                    bordered = true,
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (entry == null) {
                    Text(
                        text = tr("shuffle.empty"),
                        color = colors.muted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    PonderCard(
                        modifier = Modifier
                            .widthIn(max = 620.dp)
                            .pointerInput(entry.id) {
                                detectTapGestures(onTap = { onNext() })
                            }
                            .pointerInput(entry.id) {
                                // Lives in the gesture coroutine, so it survives
                                // recomposition without extra state.
                                var dragged = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { dragged = 0f },
                                    onDragEnd = { if (abs(dragged) > swipeThresholdPx) onNext() },
                                    onDragCancel = { dragged = 0f },
                                ) { _, amount -> dragged += amount }
                            },
                        padding = PaddingValues(22.dp),
                    ) {
                        Column(
                            Modifier
                                .heightIn(max = 520.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = linkifiedText(entry.text, colors.accent, onOpenUrl),
                                color = colors.text,
                                fontSize = 19.sp,
                                lineHeight = 30.sp,
                            )
                            Embeds.detect(entry.text).forEach { MediaPreview(it, onOpenUrl) }
                            EntryFooter(entry = entry, onOpenUrl = onOpenUrl)
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PonderButton(
                    text = tr("shuffle.next"),
                    onClick = onNext,
                    variant = ButtonVariant.PRIMARY,
                )
                Text(
                    text = tr("shuffle.hint"),
                    color = colors.faint,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Groups of near-identical entries, with a delete on each so one can be kept. */
@Composable
fun DuplicatesOverlay(
    groups: List<List<Entry>>,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr
    val total = groups.sumOf { it.size }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tr("dup.title.plural"),
                    color = colors.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                PonderIconButton(
                    icon = PonderIcons.Close,
                    contentDescription = tr("done"),
                    onClick = onClose,
                    tint = colors.muted,
                    bordered = true,
                )
            }
            Text(
                text = tr.f("dup.groups", "g" to groups.size, "n" to total),
                color = colors.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )

            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(groups, key = { group -> group.first().id }) { group ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.radius))
                            .background(colors.surface2)
                            .border(1.dp, colors.border, RoundedCornerShape(Dimens.radius))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        group.forEach { entry ->
                            DuplicateItem(entry, { onDelete(entry.id) }, onOpenUrl)
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                PonderButton(
                    text = tr("done"),
                    onClick = onClose,
                    variant = ButtonVariant.PRIMARY,
                )
            }
        }
    }
}

@Composable
private fun DuplicateItem(
    entry: Entry,
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(Dimens.radiusSm))
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = linkifiedText(entry.text, colors.accent, onOpenUrl),
                color = colors.text,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )
            EntryFooter(entry = entry, onOpenUrl = onOpenUrl)
        }
        PonderIconButton(
            icon = PonderIcons.Close,
            contentDescription = tr("aria.delete"),
            onClick = onDelete,
            tint = colors.faint,
            modifier = Modifier.size(40.dp),
        )
    }
}

/** Shown before adding something that looks like an entry you already have. */
@Composable
fun DuplicateConfirmDialog(
    matches: List<Similarity.Match>,
    onCancel: () -> Unit,
    onAddAnyway: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr
    val plural = matches.size > 1

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colors.elevated,
        titleContentColor = colors.text,
        textContentColor = colors.muted,
        title = {
            Text(
                text = if (plural) tr("dup.title.plural") else tr("dup.title"),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (plural) tr("dup.sub.plural") else tr("dup.sub"),
                    color = colors.muted,
                    fontSize = 14.sp,
                )
                matches.forEach { match ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.radiusSm))
                            .background(colors.surface2)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = linkifiedText(match.entry.text, colors.accent, onOpenUrl),
                            color = colors.text,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TagBadge(tag = match.entry.tag, label = tr.tag(match.entry.tag))
                            Text(
                                text = tr.f(
                                    "dup.match",
                                    "n" to Math.round(match.score * 100).toInt(),
                                ),
                                color = colors.faint,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddAnyway) {
                Text(tr("dup.addAnyway"), color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(tr("cancel"), color = colors.muted)
            }
        },
    )
}

/**
 * Everything worth remembering between launches: which space to open, the
 * ordering entries are listed in, and the thought Ponder keeps in the
 * notification shade. Every choice takes effect immediately — the sort re-orders
 * the list behind the dialog, the notification redraws — so it can be seen
 * rather than guessed.
 */
@Composable
fun SettingsDialog(
    startupSpaceKey: String,
    defaultSort: SortOrder,
    notifyEnabled: Boolean,
    notifySpaceKey: String,
    keepAlive: Boolean,
    notifyBlocked: Boolean,
    notifyPoolCount: Int,
    widgetPlaced: Boolean,
    widgetSpaceKey: String,
    batteryUnrestricted: Boolean,
    onSetStartupSpace: (String) -> Unit,
    onSetDefaultSort: (SortOrder) -> Unit,
    onSetNotifyEnabled: (Boolean) -> Unit,
    onSetNotifySpace: (String) -> Unit,
    onSetKeepAlive: (Boolean) -> Unit,
    onSetWidgetSpace: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onAllowBackground: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr

    // "Last one I used" sits at the top, then the spaces in nav order.
    val spaceOptions = remember { listOf(Prefs.STARTUP_LAST) + Spaces.order.map { it.key } }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.elevated,
        titleContentColor = colors.text,
        textContentColor = colors.muted,
        title = { Text(tr("settings.title"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SettingRow(
                    label = tr("settings.startSpace"),
                    hint = tr("settings.startSpace.hint"),
                ) {
                    PonderSelect(
                        selected = startupSpaceKey,
                        options = spaceOptions,
                        labelOf = { key ->
                            if (key == Prefs.STARTUP_LAST) {
                                tr("settings.startSpace.last")
                            } else {
                                tr("tab.$key", Spaces.byKey(key).fallbackName)
                            }
                        },
                        onSelect = onSetStartupSpace,
                        contentDescription = tr("settings.startSpace"),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingRow(
                    label = tr("settings.sort"),
                    hint = tr("settings.sort.hint"),
                ) {
                    PonderSelect(
                        selected = defaultSort,
                        options = listOf(SortOrder.NEWEST, SortOrder.OLDEST, SortOrder.BY_TAG),
                        labelOf = {
                            when (it) {
                                SortOrder.NEWEST -> tr("sort.newest")
                                SortOrder.OLDEST -> tr("sort.oldest")
                                SortOrder.BY_TAG -> tr("sort.tag")
                            }
                        },
                        onSelect = onSetDefaultSort,
                        contentDescription = tr("settings.sort"),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SettingDivider()

                SettingToggleRow(
                    label = tr("settings.notify"),
                    hint = tr("settings.notify.hint"),
                    checked = notifyEnabled,
                    onCheckedChange = onSetNotifyEnabled,
                )

                if (notifyEnabled) {
                    if (notifyBlocked) {
                        // The in-app toggle is on but Android is refusing, which
                        // is only fixable in system settings — so say so, and go
                        // straight there rather than leaving it looking broken.
                        SettingNote(tr("settings.notify.blocked"))
                        PonderButton(
                            text = tr("settings.notify.open"),
                            onClick = onOpenNotificationSettings,
                            variant = ButtonVariant.GHOST,
                        )
                    } else {
                        SettingRow(
                            label = tr("settings.notify.source"),
                            hint = tr("settings.notify.source.hint"),
                        ) {
                            PonderSelect(
                                selected = notifySpaceKey,
                                options = ThoughtPool.spaceFilterOptions,
                                labelOf = { key ->
                                    if (key == ThoughtPool.ALL_SPACES) {
                                        tr("settings.notify.source.all")
                                    } else {
                                        tr("tab.$key", Spaces.byKey(key).fallbackName)
                                    }
                                },
                                onSelect = onSetNotifySpace,
                                contentDescription = tr("settings.notify.source"),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (notifyPoolCount == 0) SettingNote(tr("settings.notify.empty"))

                        SettingToggleRow(
                            label = tr("settings.keepAlive"),
                            hint = tr("settings.keepAlive.hint"),
                            checked = keepAlive,
                            onCheckedChange = onSetKeepAlive,
                        )

                        SettingRow(
                            label = tr("settings.battery"),
                            hint = tr("settings.battery.hint"),
                        ) {
                            if (batteryUnrestricted) {
                                Text(
                                    text = tr("settings.battery.ok"),
                                    color = colors.accent,
                                    fontSize = 13.sp,
                                )
                            } else {
                                PonderButton(
                                    text = tr("settings.battery.ask"),
                                    onClick = onAllowBackground,
                                    variant = ButtonVariant.GHOST,
                                )
                            }
                        }
                    }
                }

                // Only once there is a widget on a home screen: until then this
                // would be a control for something the person cannot see.
                if (widgetPlaced) {
                    SettingDivider()

                    SettingRow(
                        label = tr("settings.widget"),
                        hint = tr("settings.widget.hint"),
                    ) {
                        PonderSelect(
                            selected = widgetSpaceKey,
                            options = ThoughtPool.spaceFilterOptions,
                            labelOf = { key ->
                                if (key == ThoughtPool.ALL_SPACES) {
                                    tr("settings.notify.source.all")
                                } else {
                                    tr("tab.$key", Spaces.byKey(key).fallbackName)
                                }
                            },
                            onSelect = onSetWidgetSpace,
                            contentDescription = tr("settings.widget"),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(tr("settings.close"), color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun SettingRow(label: String, hint: String, control: @Composable () -> Unit) {
    val colors = PonderTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, color = colors.faint, fontSize = 12.5.sp, lineHeight = 18.sp)
        control()
    }
}

/** A setting that is simply on or off, with the switch beside its label. */
@Composable
private fun SettingToggleRow(
    label: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = PonderTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(label, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = colors.faint, fontSize = 12.5.sp, lineHeight = 18.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.bg,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.muted,
                uncheckedTrackColor = colors.surface2,
                uncheckedBorderColor = colors.border,
            ),
        )
    }
}

/** A line of explanation that belongs to the section above it, not a control. */
@Composable
private fun SettingNote(text: String) {
    val colors = PonderTheme.colors
    Text(text, color = colors.muted, fontSize = 12.5.sp, lineHeight = 18.sp)
}

@Composable
private fun SettingDivider() {
    val colors = PonderTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.border)
    )
}

/** Offers to move on-device entries into a freshly signed-in account. */
@Composable
fun MigrationDialog(
    count: Int,
    onMove: () -> Unit,
    onKeep: () -> Unit,
) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr

    AlertDialog(
        onDismissRequest = onKeep,
        containerColor = colors.elevated,
        titleContentColor = colors.text,
        textContentColor = colors.muted,
        title = { Text(tr("migrate.title"), fontWeight = FontWeight.Bold) },
        text = { Text(tr.f("migrate.confirm", "n" to count), fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = onMove) {
                Text(tr("migrate.move"), color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) {
                Text(tr("migrate.keep"), color = colors.muted)
            }
        },
    )
}

/** Full-screen "working…" scrim that also swallows stray taps. */
@Composable
fun BusyOverlay(message: String) {
    val colors = PonderTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = interactionSource, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = colors.accent)
            Text(message, color = Color.White, fontSize = 15.sp)
        }
    }
}
