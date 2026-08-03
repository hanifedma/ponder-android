package com.hanifedma.ponder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hanifedma.ponder.data.Entry
import com.hanifedma.ponder.data.Space
import com.hanifedma.ponder.data.Spaces
import com.hanifedma.ponder.i18n.Lang
import com.hanifedma.ponder.ui.Badge
import com.hanifedma.ponder.ui.Mode
import com.hanifedma.ponder.ui.PonderUiState
import com.hanifedma.ponder.core.SortOrder
import com.hanifedma.ponder.ui.components.ButtonVariant
import com.hanifedma.ponder.ui.components.EmptyState
import com.hanifedma.ponder.ui.components.EntryCard
import com.hanifedma.ponder.ui.components.PonderButton
import com.hanifedma.ponder.ui.components.PonderCard
import com.hanifedma.ponder.ui.components.PonderIconButton
import com.hanifedma.ponder.ui.components.PonderIcons
import com.hanifedma.ponder.ui.components.PonderSelect
import com.hanifedma.ponder.ui.components.PonderTextField
import com.hanifedma.ponder.ui.components.SkeletonCard
import com.hanifedma.ponder.ui.components.SpaceSwitcher
import com.hanifedma.ponder.ui.components.StatusPill
import com.hanifedma.ponder.ui.theme.Dimens
import com.hanifedma.ponder.ui.theme.PonderTheme

/**
 * Long lines are hard to read, so every row in the list is capped at the same
 * reading width the web app uses (`--maxw: 720px`) and centred in whatever space
 * is available.
 */
private fun Modifier.readingColumn(): Modifier =
    // widthIn must come first: it caps the incoming constraint, and fillMaxWidth
    // then expands to that cap rather than to the full pane.
    this.widthIn(max = Dimens.contentMaxWidth).fillMaxWidth()

/** Everything the home screen can ask the ViewModel to do. */
data class HomeCallbacks(
    val switchSpace: (Space) -> Unit,
    val toggleLang: () -> Unit,
    val toggleTheme: () -> Unit,
    val signIn: () -> Unit,
    val signOut: () -> Unit,
    val setSearch: (String) -> Unit,
    val setSort: (SortOrder) -> Unit,
    val setComposerText: (String) -> Unit,
    val setComposerSource: (String) -> Unit,
    val setComposerTag: (String) -> Unit,
    val submit: () -> Unit,
    val delete: (String) -> Unit,
    val openShuffle: () -> Unit,
    val scanDuplicates: () -> Unit,
    val export: () -> Unit,
    val openUrl: (String) -> Unit,
)

/**
 * The main screen. One reading column on a phone; on a tablet the composer and
 * filters move into their own pane so the list keeps the full height.
 */
@Composable
fun HomeScreen(
    state: PonderUiState,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = PonderTheme.colors

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Two panes only when there is genuinely room for a 360dp sidebar plus a
        // full reading column next to it.
        val twoPane = maxWidth >= 840.dp
        val roomyTopBar = maxWidth >= 600.dp

        Column(Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                roomy = roomyTopBar,
                callbacks = callbacks,
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                if (twoPane) {
                    TwoPaneContent(state, callbacks)
                } else {
                    SingleColumnContent(state, callbacks)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ top bar

@Composable
private fun TopBar(
    state: PonderUiState,
    roomy: Boolean,
    callbacks: HomeCallbacks,
) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .statusBarsPadding()
    ) {
        // Space between the two groups, like the web app's `.topbar-inner`: the
        // switcher stays on the left edge and the account controls on the right,
        // instead of both huddling at the start with the leftover width dumped
        // on the right of a wide screen.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SpaceSwitcher(
                labels = Spaces.order.map { tr("tab.${it.key}", it.fallbackName) },
                selectedIndex = Spaces.order.indexOfFirst { it.key == state.space.key },
                onSelect = { callbacks.switchSpace(Spaces.order[it]) },
                modifier = Modifier
                    // fill = false lets widthIn actually cap it on a wide screen.
                    .weight(1f, fill = false)
                    .widthIn(max = 340.dp)
                    .padding(end = 9.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (roomy) {
                    LangButton(state.lang, callbacks.toggleLang)
                    ThemeButton(state.dark, callbacks.toggleTheme)
                    AccountArea(state, callbacks, showLabel = true)
                } else {
                    AccountArea(state, callbacks, showLabel = false)
                    OverflowMenu(state, callbacks)
                }
            }
        }
        HorizontalDivider(color = colors.border, thickness = 1.dp)
    }
}

@Composable
private fun LangButton(lang: Lang, onToggle: () -> Unit) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr
    Row(
        Modifier
            .height(Dimens.controlHeight)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(colors.surface2)
            .border(1.dp, colors.border, RoundedCornerShape(Dimens.radiusSm))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            PonderIcons.Language,
            contentDescription = tr("lang.title"),
            tint = colors.muted,
            modifier = Modifier.size(16.dp),
        )
        Text(lang.label, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ThemeButton(dark: Boolean, onToggle: () -> Unit) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr
    Row(
        Modifier
            .height(Dimens.controlHeight)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(colors.surface2)
            .border(1.dp, colors.border, RoundedCornerShape(Dimens.radiusSm))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            if (dark) PonderIcons.DarkMode else PonderIcons.LightMode,
            contentDescription = tr("theme.title"),
            tint = colors.muted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (dark) tr("theme.dark") else tr("theme.light"),
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AccountArea(
    state: PonderUiState,
    callbacks: HomeCallbacks,
    showLabel: Boolean,
) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr

    when (state.mode) {
        Mode.CLOUD -> {
            val user = state.user
            Row(
                Modifier
                    .height(Dimens.controlHeight)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(Dimens.radiusSm))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (user?.photoUrl != null) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colors.surface2),
                    )
                } else {
                    Icon(
                        PonderIcons.Person,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (showLabel) {
                    Text(
                        text = user?.displayName.orEmpty(),
                        color = colors.muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 130.dp),
                    )
                    Text(
                        text = tr("signout"),
                        color = colors.accent,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = callbacks.signOut)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }

        Mode.LOCAL, Mode.LOGIN -> {
            if (showLabel) {
                Row(
                    Modifier
                        .height(Dimens.controlHeight)
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(Dimens.radiusSm))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("chip.local"), color = colors.muted, fontSize = 13.sp, maxLines = 1)
                }
                PonderButton(
                    text = tr("signin.short"),
                    onClick = callbacks.signIn,
                    variant = ButtonVariant.PRIMARY,
                    icon = PonderIcons.Google,
                    preserveIconColors = true,
                )
            } else {
                PonderIconButton(
                    icon = PonderIcons.Login,
                    contentDescription = tr("signin.short"),
                    onClick = callbacks.signIn,
                    tint = colors.accent,
                    bordered = true,
                )
            }
        }
    }
}

/** Compact-width home for language, theme and sign-out. */
@Composable
private fun OverflowMenu(state: PonderUiState, callbacks: HomeCallbacks) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr
    var expanded by remember { mutableStateOf(false) }

    Box {
        PonderIconButton(
            icon = PonderIcons.MoreVert,
            contentDescription = tr("aria.sort"),
            onClick = { expanded = true },
            tint = colors.muted,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.elevated,
        ) {
            DropdownMenuItem(
                text = { Text("${tr("lang.title")} · ${state.lang.label}", color = colors.text) },
                leadingIcon = {
                    Icon(PonderIcons.Language, null, tint = colors.muted, modifier = Modifier.size(20.dp))
                },
                onClick = {
                    expanded = false
                    callbacks.toggleLang()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "${tr("theme.title")} · ${if (state.dark) tr("theme.dark") else tr("theme.light")}",
                        color = colors.text,
                    )
                },
                leadingIcon = {
                    Icon(
                        if (state.dark) PonderIcons.DarkMode else PonderIcons.LightMode,
                        null,
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    expanded = false
                    callbacks.toggleTheme()
                },
            )
            if (state.mode == Mode.CLOUD) {
                DropdownMenuItem(
                    text = { Text(tr("signout"), color = colors.text) },
                    leadingIcon = {
                        Icon(PonderIcons.Logout, null, tint = colors.muted, modifier = Modifier.size(20.dp))
                    },
                    onClick = {
                        expanded = false
                        callbacks.signOut()
                    },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ layouts

@Composable
private fun SingleColumnContent(state: PonderUiState, callbacks: HomeCallbacks) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = Dimens.screenPadding,
            end = Dimens.screenPadding,
            top = 18.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.mode == Mode.LOCAL && !state.firebaseAvailable) {
            item(key = "local-note") { LocalOnlyNote(Modifier.readingColumn()) }
        }
        item(key = "composer") { Composer(state, callbacks, Modifier.readingColumn()) }
        item(key = "toolbar") { Toolbar(state, callbacks, compact = true, modifier = Modifier.readingColumn()) }
        item(key = "meta") { ListMeta(state, Modifier.readingColumn()) }
        listBody(state, callbacks)
    }
}

@Composable
private fun TwoPaneContent(state: PonderUiState, callbacks: HomeCallbacks) {
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(380.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.mode == Mode.LOCAL && !state.firebaseAvailable) LocalOnlyNote()
            Composer(state, callbacks)
            Toolbar(state, callbacks, compact = false)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 20.dp,
                top = 18.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "meta") { ListMeta(state, Modifier.readingColumn()) }
            listBody(state, callbacks)
        }
    }
}

/** The list itself — skeletons, empty states, or the entries. */
private fun androidx.compose.foundation.lazy.LazyListScope.listBody(
    state: PonderUiState,
    callbacks: HomeCallbacks,
) {
    if (state.loading && state.entries.isEmpty()) {
        items(3, key = { "skeleton-$it" }) { SkeletonCard(Modifier.readingColumn()) }
        return
    }

    if (state.entries.isEmpty()) {
        item(key = "empty") {
            val tr = PonderTheme.tr
            EmptyState(
                emoji = "📝",
                title = tr("empty.${state.space.key}.title"),
                subtitle = tr("empty.${state.space.key}.sub"),
                modifier = Modifier.readingColumn(),
            )
        }
        return
    }

    if (state.visible.isEmpty()) {
        item(key = "no-match") {
            val tr = PonderTheme.tr
            EmptyState(
                emoji = "🔎",
                title = tr("empty.nomatch.title"),
                subtitle = tr("empty.nomatch.sub"),
                modifier = Modifier.readingColumn(),
            )
        }
        return
    }

    items(state.visible, key = { it.id }) { entry ->
        EntryCard(
            entry = entry,
            onDelete = { callbacks.delete(entry.id) },
            onOpenUrl = callbacks.openUrl,
            modifier = Modifier.readingColumn(),
        )
    }
}

// ----------------------------------------------------------------- composer

@Composable
private fun Composer(state: PonderUiState, callbacks: HomeCallbacks, modifier: Modifier = Modifier) {
    val tr = PonderTheme.tr
    val space = state.space

    PonderCard(modifier = modifier, padding = PaddingValues(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PonderTextField(
                value = state.composer.text,
                onValueChange = callbacks.setComposerText,
                placeholder = tr("ph.${space.key}"),
                singleLine = false,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            PonderTextField(
                value = state.composer.source,
                onValueChange = callbacks.setComposerSource,
                placeholder = tr("ph.source"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { callbacks.submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PonderSelect(
                    selected = state.composer.tag.ifEmpty { space.defaultTag },
                    options = space.tags,
                    labelOf = { tr.tag(it) },
                    onSelect = callbacks.setComposerTag,
                    contentDescription = tr("aria.tag"),
                    filled = true,
                    modifier = Modifier.weight(1f),
                )
                PonderButton(
                    text = tr("add.${space.key}"),
                    onClick = callbacks.submit,
                    variant = ButtonVariant.PRIMARY,
                    enabled = state.composer.text.isNotBlank(),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ toolbar

@Composable
private fun Toolbar(
    state: PonderUiState,
    callbacks: HomeCallbacks,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val tr = PonderTheme.tr

    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        PonderTextField(
            value = state.search,
            onValueChange = callbacks.setSearch,
            placeholder = tr("ph.search"),
            leadingIcon = PonderIcons.Search,
            filled = false,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PonderSelect(
                selected = state.sort,
                options = listOf(SortOrder.NEWEST, SortOrder.OLDEST, SortOrder.BY_TAG),
                labelOf = {
                    when (it) {
                        SortOrder.NEWEST -> tr("sort.newest")
                        SortOrder.OLDEST -> tr("sort.oldest")
                        SortOrder.BY_TAG -> tr("sort.tag")
                    }
                },
                onSelect = callbacks.setSort,
                contentDescription = tr("aria.sort"),
                modifier = Modifier.weight(1f),
            )

            if (compact) {
                PonderIconButton(
                    icon = PonderIcons.Shuffle,
                    contentDescription = tr("btn.shuffle"),
                    onClick = callbacks.openShuffle,
                    bordered = true,
                )
                PonderIconButton(
                    icon = PonderIcons.Duplicates,
                    contentDescription = tr("btn.dup"),
                    onClick = callbacks.scanDuplicates,
                    bordered = true,
                )
                PonderIconButton(
                    icon = PonderIcons.Download,
                    contentDescription = tr("btn.export"),
                    onClick = callbacks.export,
                    bordered = true,
                )
            }
        }

        if (!compact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PonderButton(
                    text = tr("btn.shuffle"),
                    onClick = callbacks.openShuffle,
                    icon = PonderIcons.Shuffle,
                    modifier = Modifier.fillMaxWidth(),
                )
                PonderButton(
                    text = tr("btn.dup"),
                    onClick = callbacks.scanDuplicates,
                    icon = PonderIcons.Duplicates,
                    modifier = Modifier.fillMaxWidth(),
                )
                PonderButton(
                    text = tr("btn.export"),
                    onClick = callbacks.export,
                    icon = PonderIcons.Download,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// --------------------------------------------------------------- list header

@Composable
private fun ListMeta(state: PonderUiState, modifier: Modifier = Modifier) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr

    val total = state.entries.size
    val shown = state.visible.size
    val label = when {
        total == 0 -> ""
        shown == total -> if (total == 1) tr("count.one") else tr.f("count.all", "n" to total)
        else -> tr.f("count.of", "n" to shown, "m" to total)
    }

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.muted, fontSize = 13.sp)
        when (state.badge) {
            Badge.LOCAL -> StatusPill(tr("badge.local"))
            Badge.OFFLINE -> StatusPill(tr("badge.offline"))
            null -> Spacer(Modifier.size(0.dp))
        }
    }
}

@Composable
private fun LocalOnlyNote(modifier: Modifier = Modifier) {
    val colors = PonderTheme.colors
    val tr = PonderTheme.tr
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(colors.accentSoft)
            .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(Dimens.radiusSm))
            .padding(horizontal = 15.dp, vertical = 12.dp)
    ) {
        Text(tr("local.note"), color = colors.muted, fontSize = 13.5.sp, lineHeight = 21.sp)
    }
}
