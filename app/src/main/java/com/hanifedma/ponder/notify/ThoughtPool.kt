package com.hanifedma.ponder.notify

import android.content.Context
import android.util.Log
import com.hanifedma.ponder.data.Entry
import com.hanifedma.ponder.data.Space
import com.hanifedma.ponder.data.Spaces
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One entry, reduced to the fields a notification can show. */
data class Thought(
    val spaceKey: String,
    val id: String,
    val text: String,
    val source: String,
    val tag: String,
) {
    /** Identifies a thought across spaces, so "don't repeat" survives a restart. */
    val key: String get() = "$spaceKey/$id"
}

/**
 * The pool the notification draws from: a plain snapshot of the entries the app
 * last had on screen, per space, written to one small file.
 *
 * Notifications have to keep working after a reboot, with no network and with
 * the app never opened — so the background code deliberately does *not* reach
 * for Firestore or for whichever store happens to be active. It reads this file,
 * which the running app keeps up to date ([replace]) whenever entries load.
 *
 * Callers build a throwaway instance wherever they need one, so every file
 * access takes the shared [LOCK] rather than the instance monitor — the app may
 * be writing from a coroutine while a broadcast receiver reads on its own
 * thread, and per-instance locking would guard nothing at all.
 */
class ThoughtPool(context: Context) {

    private val file = File(File(context.applicationContext.filesDir, "notify"), "pool.json")

    /**
     * Replaces everything known about one space. Read and write are one critical
     * section: two spaces loading at once must not each write back the map they
     * read before the other's changes landed.
     */
    fun replace(spaceKey: String, entries: List<Entry>) = synchronized(LOCK) {
        val current = readAll().toMutableMap()
        current[spaceKey] = entries.map {
            Thought(spaceKey, it.id, it.text, it.source, it.tag)
        }
        write(current)
    }

    /** Forgets everything, so the next read starts from an empty pool. */
    fun clear() = synchronized(LOCK) {
        runCatching { if (file.exists()) file.delete() }
            .onFailure { Log.e(TAG, "Could not clear the notification pool", it) }
        Unit
    }

    /** Every thought in the spaces [spaceFilter] selects. */
    fun candidates(spaceFilter: String): List<Thought> = synchronized(LOCK) {
        val all = readAll()
        return if (spaceFilter == ALL_SPACES) {
            // Nav order, so a pool built from both spaces is at least stable.
            Spaces.order.flatMap { all[it.key].orEmpty() }
        } else {
            all[spaceFilter].orEmpty()
        }
    }

    /** Total across the selected spaces — used to explain the setting in the UI. */
    fun count(spaceFilter: String): Int = candidates(spaceFilter).size

    /**
     * The next thought to show: random, and never the one already showing unless
     * it is the only one there is.
     */
    fun pick(spaceFilter: String, excludeKey: String?): Thought? =
        pickFrom(candidates(spaceFilter), excludeKey)

    private fun readAll(): Map<String, List<Thought>> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val root = JSONObject(file.readText())
            val out = LinkedHashMap<String, List<Thought>>()
            for (spaceKey in root.keys()) {
                val array = root.optJSONArray(spaceKey) ?: continue
                val items = ArrayList<Thought>(array.length())
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val text = o.optString("text", "")
                    if (text.isEmpty()) continue
                    items.add(
                        Thought(
                            spaceKey = spaceKey,
                            id = o.optString("id", ""),
                            text = text,
                            source = o.optString("source", ""),
                            tag = o.optString("tag", ""),
                        )
                    )
                }
                out[spaceKey] = items
            }
            out
        }.getOrElse {
            Log.e(TAG, "Could not read the notification pool; starting empty", it)
            emptyMap()
        }
    }

    private fun write(all: Map<String, List<Thought>>) {
        runCatching {
            val root = JSONObject()
            for ((spaceKey, items) in all) {
                val array = JSONArray()
                for (t in items) {
                    array.put(
                        JSONObject()
                            .put("id", t.id)
                            .put("text", t.text)
                            .put("source", t.source)
                            .put("tag", t.tag)
                    )
                }
                root.put(spaceKey, array)
            }
            file.parentFile?.mkdirs()
            // Same swap-in-place trick the entry store uses: a crash mid-write
            // must never leave half a file behind.
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        }.onFailure { Log.e(TAG, "Could not write the notification pool", it) }
    }

    companion object {
        /** [spaceFilter] value meaning "draw from every space". */
        const val ALL_SPACES = "all"

        /** The options the settings screen offers, in order. */
        val spaceFilterOptions: List<String> = listOf(ALL_SPACES) + Spaces.order.map { it.key }

        fun spaceOf(spaceKey: String): Space = Spaces.byKey(spaceKey)

        /**
         * The choice itself, kept free of files so it can be tested directly:
         * a random one that is not [excludeKey], falling back to the excluded one
         * only when it is the sole entry there is.
         *
         * Excluding up front rather than re-rolling until the draw differs keeps
         * this bounded — on a pool of two, re-rolling can spin for a long time.
         */
        fun pickFrom(pool: List<Thought>, excludeKey: String?): Thought? {
            if (pool.isEmpty()) return null
            val fresh = if (excludeKey == null) pool else pool.filterNot { it.key == excludeKey }
            return fresh.ifEmpty { pool }.random()
        }

        /** Held for the whole of every read and every write of the pool file. */
        private val LOCK = Any()

        private const val TAG = "PonderThoughtPool"
    }
}
