package com.hanifedma.ponder.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.hanifedma.ponder.auth.AuthManager
import com.hanifedma.ponder.auth.UserInfo
import com.hanifedma.ponder.core.DateFmt
import com.hanifedma.ponder.core.EntrySort
import com.hanifedma.ponder.core.Similarity
import com.hanifedma.ponder.core.SortOrder
import com.hanifedma.ponder.data.CloudStore
import com.hanifedma.ponder.data.Entry
import com.hanifedma.ponder.data.EntryDraft
import com.hanifedma.ponder.data.EntryStore
import com.hanifedma.ponder.data.LocalStores
import com.hanifedma.ponder.data.NetworkMonitor
import com.hanifedma.ponder.data.Prefs
import com.hanifedma.ponder.data.Space
import com.hanifedma.ponder.data.Spaces
import com.hanifedma.ponder.data.ThoughtPool
import com.hanifedma.ponder.i18n.Lang
import com.hanifedma.ponder.i18n.Tr
import com.hanifedma.ponder.notify.BatteryPolicy
import com.hanifedma.ponder.notify.Notifications
import com.hanifedma.ponder.notify.ThoughtNotifier
import com.hanifedma.ponder.pdf.PdfExporter
import com.hanifedma.ponder.widget.PonderWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Which backend the app is currently showing. */
enum class Mode { LOGIN, CLOUD, LOCAL }

/** The small status pill above the list. */
enum class Badge { LOCAL, OFFLINE }

data class ComposerState(
    val text: String = "",
    val source: String = "",
    val tag: String = "",
)

data class ShuffleState(
    /** null = all tags. */
    val tagFilter: String? = null,
    val currentId: String? = null,
)

data class MigrationPrompt(val count: Int)

data class PonderUiState(
    val booting: Boolean = true,
    val mode: Mode = Mode.LOGIN,
    val user: UserInfo? = null,
    val firebaseAvailable: Boolean = false,
    val space: Space = Spaces.PONDER,
    val lang: Lang = Lang.EN,
    val dark: Boolean = true,
    val loading: Boolean = false,
    val entries: List<Entry> = emptyList(),
    val visible: List<Entry> = emptyList(),
    val search: String = "",
    val sort: SortOrder = SortOrder.NEWEST,
    val badge: Badge? = null,
    /** Space key the app opens on, or [Prefs.STARTUP_LAST]. */
    val startupSpaceKey: String = Prefs.STARTUP_LAST,
    /** Ordering the list starts in, on launch and after a space switch. */
    val defaultSort: SortOrder = SortOrder.NEWEST,
    /** Whether a thought is kept in the notification shade. */
    val notifyEnabled: Boolean = true,
    /** Space the notification draws from, or [ThoughtPool.ALL_SPACES]. */
    val notifySpaceKey: String = ThoughtPool.ALL_SPACES,
    /** Whether the keep-alive foreground service is wanted. */
    val keepAlive: Boolean = true,
    /** Android itself is refusing Ponder's notifications; the toggle is moot. */
    val notifyBlocked: Boolean = false,
    /** How many entries the notification currently has to choose between. */
    val notifyPoolCount: Int = 0,
    /** At least one home-screen widget exists, so its setting is worth showing. */
    val widgetPlaced: Boolean = false,
    /** Space the widgets draw from, or [ThoughtPool.ALL_SPACES]. */
    val widgetSpaceKey: String = ThoughtPool.ALL_SPACES,
    /** Android will not put Ponder to sleep to save battery. */
    val batteryUnrestricted: Boolean = true,
    val settingsOpen: Boolean = false,
    val composer: ComposerState = ComposerState(),
    val busyMessage: String? = null,
    val duplicateConfirm: List<Similarity.Match>? = null,
    val duplicateGroups: List<List<Entry>>? = null,
    val shuffle: ShuffleState? = null,
    val migrationPrompt: MigrationPrompt? = null,
    val signingIn: Boolean = false,
    val loginError: String? = null,
) {
    /** The entry the shuffle view is showing, if any. */
    val shuffleEntry: Entry?
        get() = shuffle?.currentId?.let { id -> entries.firstOrNull { it.id == id } }
}

/** One-shot messages the UI acts on but does not keep. */
sealed interface UiEvent {
    data class Snack(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
        val long: Boolean = false,
    ) : UiEvent

    data class RequestExportLocation(val fileName: String) : UiEvent
    data class OpenPdf(val uri: Uri) : UiEvent
}

/**
 * Holds every decision the web app's `app.js` makes: which store is active, what
 * the list shows after search and sort, and the duplicate / shuffle / export
 * flows. Views stay dumb and just render [state].
 */
class PonderViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val auth = AuthManager(app)
    private val network = NetworkMonitor(app)
    private val localStores = LocalStores(File(app.filesDir, "entries"), viewModelScope)

    private val firestore: FirebaseFirestore? =
        if (auth.isAvailable) runCatching { FirebaseFirestore.getInstance() }.getOrNull() else null

    private val _state = MutableStateFlow(
        // Read once: which space to open, and the ordering it opens in, both
        // come straight from the person's settings.
        prefs.initialSpace.let { startupSpace ->
            PonderUiState(
                space = startupSpace,
                lang = prefs.lang,
                dark = prefs.darkTheme,
                firebaseAvailable = auth.isAvailable,
                sort = prefs.defaultSort,
                startupSpaceKey = prefs.startupSpaceKey,
                defaultSort = prefs.defaultSort,
                notifyEnabled = prefs.notificationsEnabled,
                notifySpaceKey = prefs.notifySpaceKey,
                keepAlive = prefs.keepAlive,
                widgetSpaceKey = prefs.widgetSpaceKey,
                composer = ComposerState(tag = startupSpace.defaultTag),
            )
        }
    )
    val state: StateFlow<PonderUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var storeJob: Job? = null
    private var activeStore: EntryStore? = null

    /** Identifies the (mode, account, space) triple the active store belongs to. */
    private var activeStoreKey: String? = null

    /**
     * Just the (mode, account) half of it. When this changes the notification
     * pool is thrown away, so a thought from a signed-out account can never
     * linger in the shade of the next one.
     */
    private var activeAccountKey: String? = null

    private var online: Boolean = true

    private val tr: Tr get() = Tr(_state.value.lang)

    init {
        // Opening a space counts as using it, so "last used" stays truthful even
        // when the startup setting pins a particular one.
        prefs.activeSpaceKey = _state.value.space.key
        viewModelScope.launch {
            network.observe().collect { isOnline ->
                online = isOnline
                // Coming back online clears the "offline" pill immediately.
                if (isOnline && _state.value.badge == Badge.OFFLINE) {
                    _state.value = _state.value.copy(badge = null)
                }
            }
        }
        viewModelScope.launch {
            auth.authState().collect { user -> onAuthChanged(user) }
        }
    }

    // ---------------------------------------------------------------- session

    private suspend fun onAuthChanged(user: UserInfo?) {
        if (user != null) {
            prefs.prefersLocalMode = false
            _state.value = _state.value.copy(
                booting = false,
                mode = Mode.CLOUD,
                user = user,
                loginError = null,
            )
            syncStore()
            maybeOfferMigration(user)
        } else {
            // No account: either Firebase isn't set up at all, or the person
            // already chose to stay on this device.
            val local = !auth.isAvailable || prefs.prefersLocalMode
            _state.value = _state.value.copy(
                booting = false,
                mode = if (local) Mode.LOCAL else Mode.LOGIN,
                user = null,
                migrationPrompt = null,
            )
            syncStore()
        }
    }

    fun signIn(activity: Activity) {
        if (!auth.isAvailable) {
            emit(UiEvent.Snack(tr("signin.setup"), long = true))
            return
        }
        if (_state.value.signingIn) return
        viewModelScope.launch {
            _state.value = _state.value.copy(signingIn = true, loginError = null)
            val error = auth.signIn(activity)
            _state.value = _state.value.copy(signingIn = false)
            if (error != null) {
                val message = tr(error.messageKey)
                _state.value = _state.value.copy(loginError = message)
                emit(UiEvent.Snack(message, long = true))
            }
            // Success is picked up by the auth state listener.
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            prefs.prefersLocalMode = false
        }
    }

    fun useLocalMode() {
        prefs.prefersLocalMode = true
        _state.value = _state.value.copy(mode = Mode.LOCAL, loginError = null)
        syncStore()
    }

    // ------------------------------------------------------------ store wiring

    private fun syncStore() {
        val s = _state.value
        val uid = s.user?.uid
        val accountKey = "${s.mode}:${uid ?: "-"}"
        val key = "$accountKey:${s.space.key}"
        if (key == activeStoreKey && storeJob?.isActive == true) return

        // Signing in, signing out, or switching account: whatever the shade is
        // showing came from a store this session is no longer reading.
        if (activeAccountKey != null && activeAccountKey != accountKey) {
            Notifications.resetPool(getApplication())
        }
        activeAccountKey = accountKey

        storeJob?.cancel()
        activeStoreKey = key

        val store: EntryStore? = when {
            s.mode == Mode.CLOUD && uid != null && firestore != null ->
                CloudStore(firestore, uid, s.space.collection)

            s.mode == Mode.LOCAL -> localStores.of(s.space)
            else -> null
        }
        activeStore = store

        // Clear immediately so the previous space's entries never flash.
        _state.value = _state.value.copy(
            entries = emptyList(),
            visible = emptyList(),
            loading = store != null,
            badge = null,
            shuffle = null,
            duplicateGroups = null,
        )

        if (store == null) {
            activeStoreKey = null
            _state.value = _state.value.copy(loading = false)
            return
        }

        // Captured, not read from state later: this job is cancelled the moment
        // the space changes, so it must only ever write its own space's pool.
        val poolSpaceKey = s.space.key

        storeJob = viewModelScope.launch {
            store.observe().collect { snapshot ->
                val badge = when {
                    snapshot.isLocal -> Badge.LOCAL
                    snapshot.fromCache && !online -> Badge.OFFLINE
                    else -> null
                }
                _state.value = _state.value
                    .copy(
                        entries = snapshot.entries,
                        loading = snapshot.loading,
                        badge = badge,
                    )
                    .withRecomputedList()
                    .withPrunedOverlays()
                // Keep the pool the background notification draws from in step
                // with what the app itself is showing. The `loading` snapshot
                // carries a placeholder empty list, which would wipe it.
                if (!snapshot.loading) {
                    Notifications.updatePool(getApplication(), poolSpaceKey, snapshot.entries)
                }
                if (snapshot.error) emit(UiEvent.Snack(tr("err.load"), long = true))
            }
        }
    }

    // -------------------------------------------------------- list derivation

    private fun PonderUiState.withRecomputedList(): PonderUiState =
        copy(visible = EntrySort.visible(entries, search, sort, space))

    /** Drops overlay state that refers to entries which no longer exist. */
    private fun PonderUiState.withPrunedOverlays(): PonderUiState {
        var next = this
        val groups = next.duplicateGroups
        if (groups != null) {
            val alive = entries.mapTo(HashSet()) { it.id }
            val pruned = groups
                .map { group -> group.filter { alive.contains(it.id) } }
                .filter { it.size >= 2 }
            next = next.copy(duplicateGroups = if (pruned.isEmpty()) null else pruned)
        }
        val shuffle = next.shuffle
        if (shuffle?.currentId != null && entries.none { it.id == shuffle.currentId }) {
            next = next.copy(shuffle = shuffle.copy(currentId = null))
        }
        return next
    }

    // ------------------------------------------------------------- UI actions

    fun setSearch(value: String) {
        _state.value = _state.value.copy(search = value).withRecomputedList()
    }

    fun setSort(sort: SortOrder) {
        _state.value = _state.value.copy(sort = sort).withRecomputedList()
    }

    fun switchSpace(space: Space) {
        if (space.key == _state.value.space.key) return
        prefs.activeSpaceKey = space.key
        // Per-space view state resets, exactly like the web app — back to the
        // chosen default ordering rather than always to newest-first.
        _state.value = _state.value.copy(
            space = space,
            search = "",
            sort = _state.value.defaultSort,
            composer = _state.value.composer.copy(tag = space.defaultTag),
            duplicateConfirm = null,
        )
        syncStore()
    }

    // ------------------------------------------------------------- settings

    fun openSettings() {
        _state.value = _state.value.copy(settingsOpen = true)
        refreshNotifyStatus()
    }

    fun closeSettings() {
        _state.value = _state.value.copy(settingsOpen = false)
    }

    /** [key] is a space key, or [Prefs.STARTUP_LAST] for "wherever I left off". */
    fun setStartupSpace(key: String) {
        prefs.startupSpaceKey = key
        _state.value = _state.value.copy(startupSpaceKey = key)
    }

    /**
     * Changing the default also re-sorts what is on screen, so the choice can be
     * seen straight away instead of only on the next launch.
     */
    fun setDefaultSort(sort: SortOrder) {
        prefs.defaultSort = sort
        _state.value = _state.value
            .copy(defaultSort = sort, sort = sort)
            .withRecomputedList()
    }

    // ------------------------------------------------------- notifications

    /**
     * Re-reads the things only the system can answer — whether notifications are
     * allowed, whether the battery saver is holding Ponder back, and how many
     * entries the shade currently has to pick from. Cheap, but it touches a file,
     * so it never runs on the main thread.
     */
    fun refreshNotifyStatus() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val blocked = withContext(Dispatchers.IO) { !ThoughtNotifier.canPost(app) }
            val battery = withContext(Dispatchers.IO) { BatteryPolicy.isUnrestricted(app) }
            val count = withContext(Dispatchers.IO) {
                ThoughtPool(app).count(prefs.notifySpaceKey)
            }
            // Only worth a settings row once there is a widget for it to be
            // about; before that it is a control for something invisible.
            val widgets = withContext(Dispatchers.IO) { PonderWidgets.anyPlaced(app) }
            _state.value = _state.value.copy(
                notifyBlocked = blocked,
                batteryUnrestricted = battery,
                notifyPoolCount = count,
                widgetPlaced = widgets,
            )
        }
    }

    /**
     * Called every time the app comes to the foreground. This is the one moment
     * Android reliably permits starting the keep-alive service, so it is also
     * where a service killed while the app was away gets brought back.
     */
    fun onAppForeground() {
        Notifications.apply(getApplication(), allowServiceStart = true)
        refreshNotifyStatus()
    }

    /**
     * True the first time the app runs with notifications switched on. Asked
     * once and once only — a "no" is a decision, and re-prompting every launch
     * would make it one the person has to keep making.
     */
    fun shouldAskNotificationPermission(): Boolean =
        prefs.notificationsEnabled && !prefs.notifyPermissionAsked

    fun onNotificationPermissionAsked() {
        prefs.notifyPermissionAsked = true
    }

    /** Whatever the answer, act on it: post the first thought, or stand down. */
    fun onNotificationPermissionResult() {
        Notifications.apply(getApplication(), allowServiceStart = true)
        refreshNotifyStatus()
    }

    fun setNotifyEnabled(enabled: Boolean) {
        prefs.notificationsEnabled = enabled
        _state.value = _state.value.copy(notifyEnabled = enabled)
        Notifications.apply(getApplication(), allowServiceStart = true)
        refreshNotifyStatus()
    }

    /** [key] is a space key, or [ThoughtPool.ALL_SPACES]. */
    fun setNotifySpace(key: String) {
        prefs.notifySpaceKey = key
        _state.value = _state.value.copy(notifySpaceKey = key)
        // Redraw rather than merely ensure: if the thought on screen came from a
        // space this no longer includes, it has to be replaced now.
        Notifications.refresh(getApplication())
        refreshNotifyStatus()
    }

    fun setKeepAlive(enabled: Boolean) {
        prefs.keepAlive = enabled
        _state.value = _state.value.copy(keepAlive = enabled)
        Notifications.apply(getApplication(), allowServiceStart = true)
    }

    /** [key] is a space key, or [ThoughtPool.ALL_SPACES]. */
    fun setWidgetSpace(key: String) {
        prefs.widgetSpaceKey = key
        _state.value = _state.value.copy(widgetSpaceKey = key)
        // Each widget keeps its quote unless the new filter excludes it, in
        // which case the redraw replaces it — so narrowing to one section takes
        // visible effect immediately rather than at the next shuffle.
        PonderWidgets.refreshAll(getApplication())
    }

    fun toggleTheme() {
        val dark = !_state.value.dark
        prefs.darkTheme = dark
        _state.value = _state.value.copy(dark = dark)
        // The widget paints itself from this palette, and it is not redrawn by
        // anything else — leave it out and the home screen keeps the old theme
        // until the quote next changes.
        PonderWidgets.refreshAll(getApplication())
    }

    fun toggleLang() {
        val next = if (_state.value.lang == Lang.EN) Lang.KO else Lang.EN
        prefs.lang = next
        _state.value = _state.value.copy(lang = next)
        // The entry itself is never translated, but the section name and tag
        // around it are — redraw so neither the shade nor the home screen is
        // left in the old language.
        Notifications.refresh(getApplication())
        PonderWidgets.refreshAll(getApplication())
    }

    fun setComposerText(value: String) {
        _state.value = _state.value.copy(composer = _state.value.composer.copy(text = value))
    }

    fun setComposerSource(value: String) {
        _state.value = _state.value.copy(composer = _state.value.composer.copy(source = value))
    }

    fun setComposerTag(value: String) {
        _state.value = _state.value.copy(composer = _state.value.composer.copy(tag = value))
    }

    /** Pre-fills the composer from a share / "process text" intent. */
    fun prefillFromShare(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val existing = _state.value.composer.text
        val merged = if (existing.isBlank()) trimmed else "$existing\n$trimmed"
        _state.value = _state.value.copy(composer = _state.value.composer.copy(text = merged))
    }

    // ------------------------------------------------------------------- add

    fun submitComposer() {
        val s = _state.value
        val text = s.composer.text.trim()
        if (text.isEmpty()) return
        // Warn if something similar already exists; the person decides.
        val similar = Similarity.findSimilar(text, s.entries)
        if (similar.isNotEmpty()) {
            _state.value = s.copy(duplicateConfirm = similar)
        } else {
            commitComposer()
        }
    }

    fun confirmAddAnyway() {
        _state.value = _state.value.copy(duplicateConfirm = null)
        commitComposer()
    }

    fun dismissDuplicateConfirm() {
        _state.value = _state.value.copy(duplicateConfirm = null)
    }

    private fun commitComposer() {
        val store = activeStore ?: return
        val s = _state.value
        val text = s.composer.text.trim()
        if (text.isEmpty()) return
        val draft = EntryDraft(
            text = text,
            source = s.composer.source.trim(),
            tag = s.composer.tag.ifEmpty { s.space.defaultTag },
        )
        viewModelScope.launch {
            val result = store.add(draft)
            if (result.isSuccess) {
                // Keep the chosen tag, clear what was written — same as the web form.
                _state.value = _state.value.copy(
                    composer = _state.value.composer.copy(text = "", source = ""),
                )
            } else {
                emit(UiEvent.Snack(tr("err.save")))
            }
        }
    }

    // ---------------------------------------------------------------- delete

    fun delete(id: String) = deleteEntry(id)

    /**
     * Optimistic delete with an Undo action rather than a confirm dialog.
     * [afterDeleted] runs once the Undo message has been queued, so any follow-up
     * message lines up behind it instead of pushing it out of the way.
     */
    private fun deleteEntry(id: String, afterDeleted: (() -> Unit)? = null) {
        val store = activeStore ?: return
        val entry = _state.value.entries.firstOrNull { it.id == id } ?: return
        val backup = EntryDraft(entry.text, entry.source, entry.tag, entry.createdAt)
        viewModelScope.launch {
            val result = store.remove(id)
            if (result.isFailure) {
                emit(UiEvent.Snack(tr("err.delete")))
                return@launch
            }
            emit(
                UiEvent.Snack(
                    message = tr("deleted"),
                    actionLabel = tr("undo"),
                    onAction = {
                        viewModelScope.launch {
                            // Restores into the store it came from, even if the
                            // person has since switched space.
                            if (store.add(backup).isFailure) {
                                emit(UiEvent.Snack(tr("err.undo")))
                            }
                        }
                    },
                )
            )
            afterDeleted?.invoke()
        }
    }

    // ------------------------------------------------------------ duplicates

    fun scanForDuplicates() {
        val entries = _state.value.entries
        if (entries.size < 2) {
            emit(UiEvent.Snack(tr("dup.need2")))
            return
        }
        viewModelScope.launch {
            // The scan is O(n²); show progress once it could actually be felt.
            val heavy = entries.size > 400
            if (heavy) _state.value = _state.value.copy(busyMessage = tr("dup.scanning"))
            val groups = withContext(Dispatchers.Default) {
                Similarity.findDuplicateGroups(entries)
            }
            _state.value = _state.value.copy(busyMessage = null)
            if (groups.isEmpty()) {
                emit(UiEvent.Snack(tr("dup.none")))
            } else {
                _state.value = _state.value.copy(duplicateGroups = groups)
            }
        }
    }

    fun closeDuplicates() {
        _state.value = _state.value.copy(duplicateGroups = null)
    }

    /** Deletes from inside the duplicates view and collapses groups that shrink. */
    fun deleteFromDuplicates(id: String) {
        deleteEntry(id) {
            val groups = _state.value.duplicateGroups ?: return@deleteEntry
            // A group of fewer than two entries is no longer a duplicate group.
            val pruned = groups
                .map { group -> group.filterNot { it.id == id } }
                .filter { it.size >= 2 }
            if (pruned.isEmpty()) {
                _state.value = _state.value.copy(duplicateGroups = null)
                emit(UiEvent.Snack(tr("dup.noMore")))
            } else {
                _state.value = _state.value.copy(duplicateGroups = pruned)
            }
        }
    }

    // --------------------------------------------------------------- shuffle

    fun openShuffle() {
        if (_state.value.entries.isEmpty()) {
            emit(UiEvent.Snack(tr("shuffle.needAdd")))
            return
        }
        _state.value = _state.value.copy(shuffle = ShuffleState())
        shuffleNext()
    }

    fun closeShuffle() {
        _state.value = _state.value.copy(shuffle = null)
    }

    fun setShuffleTag(tag: String?) {
        val current = _state.value.shuffle ?: return
        _state.value = _state.value.copy(shuffle = current.copy(tagFilter = tag, currentId = null))
        shuffleNext()
    }

    fun shuffleNext() {
        val s = _state.value
        val shuffle = s.shuffle ?: return
        // Shuffle draws from everything in the space, not the filtered list.
        val pool = if (shuffle.tagFilter == null) {
            s.entries
        } else {
            s.entries.filter { it.tag == shuffle.tagFilter }
        }
        val next = when {
            pool.isEmpty() -> null
            // Never show the same entry twice in a row. Excluding the current one
            // up front keeps this bounded, where re-rolling until it differs can
            // spin for a long time on a pool of two.
            else -> pool.filterNot { it.id == shuffle.currentId }.ifEmpty { pool }.random()
        }
        _state.value = s.copy(shuffle = shuffle.copy(currentId = next?.id))
    }

    // ------------------------------------------------------------- migration

    private suspend fun maybeOfferMigration(user: UserInfo) {
        if (prefs.migrationOffered(user.uid)) return
        val total = Spaces.order.sumOf { localStores.of(it).snapshot().size }
        if (total == 0) {
            prefs.setMigrationOffered(user.uid)
            return
        }
        _state.value = _state.value.copy(migrationPrompt = MigrationPrompt(total))
    }

    fun acceptMigration() {
        val uid = _state.value.user?.uid ?: return
        val db = firestore ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                migrationPrompt = null,
                busyMessage = tr("migrate.busy"),
            )
            var moved = 0
            val failed = runCatching {
                for (space in Spaces.order) {
                    val local = localStores.of(space)
                    // Oldest first, so the account ends up in the same order.
                    val items = local.snapshot().sortedBy { it.createdAt }
                    if (items.isEmpty()) continue
                    val cloud = CloudStore(db, uid, space.collection)
                    for (entry in items) {
                        cloud.add(
                            EntryDraft(entry.text, entry.source, entry.tag, entry.createdAt)
                        ).getOrThrow()
                        moved++
                    }
                    local.clear().getOrThrow()
                }
            }.isFailure
            prefs.setMigrationOffered(uid)
            _state.value = _state.value.copy(busyMessage = null)
            if (failed) {
                emit(UiEvent.Snack(tr("migrate.err"), long = true))
            } else {
                emit(UiEvent.Snack(tr.f("migrate.moved", "n" to moved)))
            }
        }
    }

    fun declineMigration() {
        _state.value.user?.uid?.let { prefs.setMigrationOffered(it) }
        _state.value = _state.value.copy(migrationPrompt = null)
    }

    // ------------------------------------------------------------ PDF export

    fun requestExport() {
        val s = _state.value
        if (s.entries.isEmpty()) {
            emit(UiEvent.Snack(tr("pdf.nothing")))
            return
        }
        val name = "${s.space.pdfFile}-${DateFmt.isoDate(System.currentTimeMillis())}.pdf"
        emit(UiEvent.RequestExportLocation(name))
    }

    fun exportTo(uri: Uri) {
        val s = _state.value
        val entries = s.entries.sortedWith(EntrySort.comparator(s.sort, s.space))
        if (entries.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busyMessage = tr("pdf.building"))
            val result = PdfExporter.export(
                context = getApplication(),
                uri = uri,
                entries = entries,
                space = s.space,
                lang = s.lang,
                onProgress = { key ->
                    _state.value = _state.value.copy(busyMessage = tr(key))
                },
            )
            _state.value = _state.value.copy(busyMessage = null)
            if (result.isSuccess) {
                emit(
                    UiEvent.Snack(
                        message = tr.f("pdf.done", "n" to entries.size),
                        actionLabel = tr("pdf.open"),
                        onAction = { emit(UiEvent.OpenPdf(uri)) },
                        long = true,
                    )
                )
            } else {
                emit(UiEvent.Snack(tr("pdf.err"), long = true))
            }
        }
    }

    // ------------------------------------------------------------------ util

    private fun emit(event: UiEvent) {
        _events.trySend(event)
    }

    override fun onCleared() {
        super.onCleared()
        storeJob?.cancel()
    }
}
