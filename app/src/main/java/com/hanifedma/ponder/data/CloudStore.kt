package com.hanifedma.ponder.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import java.util.Date

/**
 * The cloud backend: the *same* Firestore documents the web app reads and
 * writes, at `/users/{uid}/{collection}/{docId}` with fields `text`, `source`,
 * `tag` and `createdAt`.
 *
 * Per-user subcollections give strong isolation (see `firestore.rules` in the
 * web repo) and need no composite index. Firestore's on-device cache is enabled
 * by default on Android, so this keeps working with little or no connectivity.
 */
class CloudStore(
    private val db: FirebaseFirestore,
    private val uid: String,
    private val collectionName: String,
) : EntryStore {

    private fun collection() =
        db.collection("users").document(uid).collection(collectionName)

    override fun observe(): Flow<StoreState> = callbackFlow {
        trySend(StoreState(loading = true))

        // Keep the last good list so a mid-session error shows stale data rather
        // than an empty screen — the same choice the web app makes.
        var lastGood: List<Entry> = emptyList()

        val registration = collection()
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore listener error", error)
                    trySend(
                        StoreState(
                            entries = lastGood,
                            fromCache = true,
                            error = true,
                        )
                    )
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { toEntry(it) }
                lastGood = items
                trySend(StoreState(entries = items, fromCache = snapshot.metadata.isFromCache))
            }

        awaitClose { registration.remove() }
    }.buffer(Channel.CONFLATED)

    override suspend fun add(draft: EntryDraft): Result<Unit> = runCatching {
        val data = hashMapOf(
            "text" to draft.text,
            "source" to draft.source,
            "tag" to draft.tag,
            // Preserve the original time when restoring a delete or migrating
            // device entries in; otherwise let the server stamp it.
            "createdAt" to (draft.createdAt?.let { Timestamp(Date(it)) }
                ?: FieldValue.serverTimestamp()),
        )
        // Deliberately not awaited: Firestore applies the write to the local
        // cache immediately (so the entry appears at once and offline adds work),
        // and only settles this task once the server has it. A rejected write is
        // rolled back and surfaces through the snapshot listener's error path.
        val pending = collection().add(data)
        pending.addOnFailureListener { Log.e(TAG, "Add failed", it) }
    }

    override suspend fun remove(id: String): Result<Unit> = runCatching {
        val pending = collection().document(id).delete()
        pending.addOnFailureListener { Log.e(TAG, "Delete failed", it) }
    }

    private fun toEntry(doc: DocumentSnapshot): Entry? {
        val text = doc.getString("text") ?: return null
        return Entry(
            id = doc.id,
            text = text,
            source = doc.getString("source") ?: "",
            tag = doc.getString("tag") ?: "",
            createdAt = readCreatedAt(doc),
            pending = doc.metadata.hasPendingWrites(),
        )
    }

    /**
     * ESTIMATE means a just-added entry carries Firestore's local guess at the
     * server time instead of null, so it sorts into place immediately instead of
     * jumping once the server replies.
     */
    private fun readCreatedAt(doc: DocumentSnapshot): Long {
        val ts = runCatching {
            doc.getTimestamp(CREATED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }.getOrNull()
        if (ts != null) return ts.toDate().time
        // Tolerate an entry written with a plain epoch number.
        (doc.get(CREATED_AT) as? Number)?.let { return it.toLong() }
        return System.currentTimeMillis()
    }

    private companion object {
        const val TAG = "PonderCloudStore"
        const val CREATED_AT = "createdAt"
    }
}
