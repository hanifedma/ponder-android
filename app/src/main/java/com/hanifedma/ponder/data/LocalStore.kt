package com.hanifedma.ponder.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * The device-only backend: entries live in a small JSON file, no account and no
 * network needed. This is what the app runs on before (or instead of) signing
 * in — the counterpart to the web app's localStorage store.
 *
 * One instance per space; create them through [LocalStores] so a space always
 * has exactly one.
 */
class LocalStore(
    private val file: File,
    private val scope: CoroutineScope,
) : EntryStore {

    private val entries = MutableStateFlow<List<Entry>?>(null) // null = not loaded yet
    private val ioLock = Mutex()
    private var loadStarted = false

    override fun observe(): Flow<StoreState> {
        ensureLoaded()
        return entries.map { list ->
            StoreState(
                entries = list ?: emptyList(),
                loading = list == null,
                fromCache = true,
                isLocal = true,
            )
        }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loadStarted) return
        loadStarted = true
        scope.launch { load() }
    }

    /** Reads the file once. Holds the same lock as writes, so a mutation that
     *  lands first is never overwritten by the initial load. */
    private suspend fun load() {
        ioLock.withLock {
            if (entries.value == null) entries.value = readFile()
        }
    }

    /** Suspends until the file has been read; used by migration and export. */
    suspend fun snapshot(): List<Entry> {
        entries.value?.let { return it }
        load()
        return entries.value.orEmpty()
    }

    override suspend fun add(draft: EntryDraft): Result<Unit> = mutate { current ->
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            text = draft.text,
            source = draft.source,
            tag = draft.tag,
            createdAt = draft.createdAt ?: System.currentTimeMillis(),
        )
        listOf(entry) + current // newest first, like the web store's unshift
    }

    override suspend fun remove(id: String): Result<Unit> = mutate { current ->
        current.filterNot { it.id == id }
    }

    /** Drops every entry — used after they have been copied into an account. */
    suspend fun clear(): Result<Unit> = mutate { emptyList() }

    private suspend fun mutate(transform: (List<Entry>) -> List<Entry>): Result<Unit> =
        ioLock.withLock {
            runCatching {
                val current = entries.value ?: readFile()
                val next = transform(current)
                writeFile(next)
                entries.value = next
            }.onFailure { Log.e(TAG, "Local store write failed", it) }
        }

    private suspend fun readFile(): List<Entry> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            val array = JSONArray(file.readText())
            val out = ArrayList<Entry>(array.length())
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val text = o.optString("text", "")
                if (text.isEmpty()) continue
                out.add(
                    Entry(
                        id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                        text = text,
                        source = o.optString("source", ""),
                        tag = o.optString("tag", ""),
                        createdAt = o.optLong("createdAt", 0L),
                    )
                )
            }
            // Newest first, matching the order the cloud query returns.
            out.sortedByDescending { it.createdAt }
        }.getOrElse {
            Log.e(TAG, "Could not read ${file.name}; starting empty", it)
            emptyList()
        }
    }

    private suspend fun writeFile(list: List<Entry>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        for (e in list) {
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("text", e.text)
                    .put("source", e.source)
                    .put("tag", e.tag)
                    .put("createdAt", e.createdAt)
            )
        }
        file.parentFile?.mkdirs()
        // Write to a sibling first, then swap it in, so a crash mid-write can
        // never leave a half-written file behind.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(array.toString())
            tmp.delete()
        }
    }

    private companion object {
        const val TAG = "PonderLocalStore"
    }
}

/** Keeps one [LocalStore] per space, so both the UI and migration see the same data. */
class LocalStores(private val dir: File, private val scope: CoroutineScope) {

    private val stores = HashMap<String, LocalStore>()

    @Synchronized
    fun of(space: Space): LocalStore = stores.getOrPut(space.localKey) {
        LocalStore(File(dir, space.localKey + ".json"), scope)
    }
}
