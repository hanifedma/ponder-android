package com.hanifedma.ponder.data

import kotlinx.coroutines.flow.Flow

/**
 * What the UI knows about the active backend at a point in time.
 *
 * @param loading true only while waiting for the very first cloud snapshot.
 * @param fromCache the data came from the on-device cache, not the server.
 * @param isLocal this is the device-only store (no account involved).
 * @param error the backend reported a problem; [entries] is the last good copy.
 */
data class StoreState(
    val entries: List<Entry> = emptyList(),
    val loading: Boolean = false,
    val fromCache: Boolean = false,
    val isLocal: Boolean = false,
    val error: Boolean = false,
)

/**
 * One interface over the two backends the app can run on:
 * [LocalStore] (this device) and [CloudStore] (Firestore, synced per account) —
 * the same split the web app uses.
 */
interface EntryStore {

    /** Emits on every change. Cold: collecting starts the underlying listener. */
    fun observe(): Flow<StoreState>

    /**
     * Adds an entry. Returns as soon as the write is applied locally, so the UI
     * updates instantly and nothing hangs while offline; a rejected write is
     * reported later through [observe]'s error state.
     */
    suspend fun add(draft: EntryDraft): Result<Unit>

    suspend fun remove(id: String): Result<Unit>
}
