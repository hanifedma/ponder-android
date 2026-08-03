package com.hanifedma.ponder.core

import com.hanifedma.ponder.data.Entry

/**
 * Near-duplicate detection, ported from the web app so both clients flag the
 * same pairs. Everything here is local — no network, no I/O.
 */
object Similarity {

    /** 0..1 overlap that counts as "similar" (web: SIMILAR_THRESHOLD). */
    const val THRESHOLD = 0.6

    /** How many similar entries the on-add warning shows. */
    private const val MAX_MATCHES = 5

    // Drop punctuation but keep letters/numbers of any language, then squash runs
    // of whitespace. Compiled once — these run over every entry on every scan.
    private val PUNCTUATION = Regex("[^\\p{L}\\p{N}\\s]")
    private val WHITESPACE = Regex("\\s+")

    fun normalizeText(s: String?): String =
        (s ?: "")
            .lowercase()
            .replace(PUNCTUATION, " ")
            .replace(WHITESPACE, " ")
            .trim()

    /**
     * Similarity of two already-normalized strings, 0..1.
     *
     * Takes the stronger of two signals: word overlap (order-independent, so it
     * catches reordering) and character-bigram overlap (catches typos and small
     * edits).
     */
    fun similarityScore(aNorm: String, bNorm: String): Double {
        if (aNorm.isEmpty() || bNorm.isEmpty()) return 0.0
        if (aNorm == bNorm) return 1.0
        // One text fully contains the other (e.g. you added a few words).
        if (aNorm.length > 10 && bNorm.length > 10 &&
            (aNorm.contains(bNorm) || bNorm.contains(aNorm))
        ) {
            return 0.95
        }
        return maxOf(
            jaccardWords(wordsOf(aNorm), wordsOf(bNorm)),
            diceBigrams(bigramsOf(aNorm), bigramsOf(bNorm)),
        )
    }

    fun wordsOf(norm: String): Set<String> =
        if (norm.isEmpty()) emptySet() else norm.split(" ").toSet()

    fun jaccardWords(a: Set<String>, b: Set<String>): Double {
        var inter = 0
        // Walk the smaller set so the lookups happen against the larger one.
        val (small, large) = if (a.size <= b.size) a to b else b to a
        for (w in small) if (large.contains(w)) inter++
        val union = a.size + b.size - inter
        return if (union != 0) inter.toDouble() / union else 0.0
    }

    /** Character bigram counts, plus the total, for Sørensen–Dice. */
    class Bigrams(val counts: Map<String, Int>, val total: Int)

    fun bigramsOf(norm: String): Bigrams {
        if (norm.length < 2) return Bigrams(emptyMap(), 0)
        val m = HashMap<String, Int>(norm.length)
        for (i in 0 until norm.length - 1) {
            val g = norm.substring(i, i + 2)
            m[g] = (m[g] ?: 0) + 1
        }
        return Bigrams(m, norm.length - 1)
    }

    fun diceBigrams(a: Bigrams, b: Bigrams): Double {
        if (a.total == 0 || b.total == 0) return 0.0
        // Iterate the smaller map; min() is symmetric so the result is identical.
        val (small, large) = if (a.counts.size <= b.counts.size) a to b else b to a
        var inter = 0
        for ((g, c) in small.counts) {
            val other = large.counts[g] ?: continue
            inter += minOf(c, other)
        }
        return (2.0 * inter) / (a.total + b.total)
    }

    /** A candidate returned by [findSimilar], most-similar first. */
    data class Match(val entry: Entry, val score: Double)

    /** Up to five existing entries similar to [text]. Used before adding. */
    fun findSimilar(text: String, entries: List<Entry>): List<Match> {
        val newNorm = normalizeText(text)
        if (newNorm.isEmpty()) return emptyList()
        val out = ArrayList<Match>()
        for (e in entries) {
            val score = similarityScore(newNorm, normalizeText(e.text))
            if (score >= THRESHOLD) out.add(Match(e, score))
        }
        out.sortByDescending { it.score }
        return out.take(MAX_MATCHES)
    }

    /**
     * Groups of mutually-similar entries (each 2+ entries), largest group first.
     *
     * Same pairwise rule as [similarityScore] and the same union-find clustering
     * as the web app, but bigram tables are built once per entry instead of once
     * per comparison, and pairs whose lengths make the threshold unreachable are
     * skipped. Both are lossless: the set of flagged pairs is unchanged.
     */
    fun findDuplicateGroups(entries: List<Entry>): List<List<Entry>> {
        val n = entries.size
        if (n < 2) return emptyList()

        val norm = Array(n) { normalizeText(entries[it].text) }
        val words = Array(n) { wordsOf(norm[it]) }
        val grams = Array(n) { bigramsOf(norm[it]) }

        val parent = IntArray(n) { it }
        fun find(start: Int): Int {
            var x = start
            while (parent[x] != x) {
                parent[x] = parent[parent[x]] // path halving
                x = parent[x]
            }
            return x
        }

        for (i in 0 until n) {
            if (norm[i].isEmpty()) continue
            for (j in i + 1 until n) {
                if (norm[j].isEmpty()) continue
                if (find(i) == find(j)) continue // already clustered together
                if (isSimilar(norm[i], norm[j], words[i], words[j], grams[i], grams[j])) {
                    val ri = find(i)
                    val rj = find(j)
                    if (ri != rj) parent[ri] = rj
                }
            }
        }

        val byRoot = LinkedHashMap<Int, MutableList<Entry>>()
        for (i in 0 until n) {
            byRoot.getOrPut(find(i)) { ArrayList() }.add(entries[i])
        }
        return byRoot.values
            .filter { it.size >= 2 }
            .sortedByDescending { it.size }
    }

    /**
     * Exactly `similarityScore(a, b) >= THRESHOLD`, but able to answer without
     * the bigram pass when the two lengths make the threshold unreachable.
     */
    private fun isSimilar(
        aNorm: String,
        bNorm: String,
        aWords: Set<String>,
        bWords: Set<String>,
        aGrams: Bigrams,
        bGrams: Bigrams,
    ): Boolean {
        if (aNorm == bNorm) return true
        if (aNorm.length > 10 && bNorm.length > 10 &&
            (aNorm.contains(bNorm) || bNorm.contains(aNorm))
        ) {
            return true
        }
        if (jaccardWords(aWords, bWords) >= THRESHOLD) return true
        // Dice can be at most 2*min(totals)/(sum of totals): if that is below the
        // threshold, computing it exactly cannot change the answer.
        val sum = aGrams.total + bGrams.total
        if (sum == 0) return false
        val ceiling = 2.0 * minOf(aGrams.total, bGrams.total) / sum
        if (ceiling < THRESHOLD) return false
        return diceBigrams(aGrams, bGrams) >= THRESHOLD
    }
}
