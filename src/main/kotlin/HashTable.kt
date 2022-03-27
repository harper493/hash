
data class Stats(val occupied: Int, val avgLength: Double, val maxLength: Int, val overflows: Int, var failures: Int=0) {

    fun setFailures(f: Int) =
        also {
            failures = f
        }
}

class HashTable<T> (val size: Int,
                    val bucketSize: Int,
                    val hashfn: (T)->Long,
                    val rehash: (Int, Int, Int)->Int,
                    val hashWithinBucket: Boolean = true,
                    val overflowNextBucket: Boolean = false) {
    val bucketCount = size / bucketSize
    var overflows = 0

    class Bucket<T>(val table: HashTable<T>, val size: Int) {

        val content = Array<Entry<T>?>(size, { null })
        var entries = 0

        data class Entry<TT>(val hash: Long, val value: TT)

        fun getEntry(hash: Long) =
            if (table.hashWithinBucket) (hash % size).toInt() else 0

        fun insert(hash: Long, value: T): Boolean {
            if (entries < size) {
                var e = getEntry(hash)
                var attempt = 0
                while (true) {
                    when {
                        content[e] == null -> {
                            content[e] = Entry(hash, value)
                            break
                        }
                        attempt >= size ->
                            return false
                        else -> {
                            e = table.rehash(e, attempt, size)
                            ++attempt
                        }
                    }
                }
                ++entries
                return true
            } else {
                return false
            }
        }

        fun find(hash: Long): Pair<T?, Boolean> {
            var e = getEntry(hash)
            var attempt = 0
            while (true) {
                val entry = content[e]
                when {
                    entry == null ->
                        return null to false
                    entry.hash == hash ->
                        return entry.value to false
                    attempt >= size ->
                        return null to true
                    else -> {
                        e = table.rehash(e, attempt, size)
                        ++attempt
                    }
                }
            }
        }

        fun getStats(): Stats =
            with(content.mapIndexed { index, _ -> chainLength(index) }) {
                Stats(entries, (filter{it>0}.nullIfEmpty()?.average() ?: 0.0), maxOf{ it }, 0)
            }

        fun chainLength(e: Int) =
            minOf(size, (0..size).fold(0 to e){ where, len ->
                if (content[where.second]==null) return where.first
                where.first + 1 to table.rehash(where.second, where.first, size)
            }.first)
    }

    val buckets = Array<Bucket<T>>(bucketCount, { Bucket(this, bucketSize) })

    fun insert(entry: T): Boolean {
        val hash = hashfn(entry)
        val bucket = (hash % bucketCount).toInt()
        var b = bucket
        while (true) {
            val r = buckets[b].insert(hash, entry)
            if (r || !overflowNextBucket) {
                return r
            }
            b = nextBucket(b)
            if (b==bucket) {
                return false
            }
            ++overflows
        }
    }

    fun find(entry: T): T? {
        val hash = hashfn(entry)
        val bucket = (hash % bucketCount).toInt()
        var b = bucket
        while (true) {
            val f = buckets[bucket].find(hash)
            if (!f.second || !overflowNextBucket) {
                return f.first
            }
            b = nextBucket(b)
            if (b==bucket) {
                return null
            }
        }
    }

    fun getStats(): Stats =
        with(buckets.map{ it.getStats() }) {
            Stats(sumOf{ it.occupied }, map{ it.avgLength }.filter{ it > 0 }.average(), map{ it.maxLength }.maxOf{ it }, overflows)
        }

    fun nextBucket(b: Int) =
        with (b + 1) {
            if (this>=bucketCount) 0 else this
        }
}

/**
 * Return the given iterable if it is not empty, else null
 */

fun<T> Iterable<T>.nullIfEmpty(): Iterable<T>? =
    if (any()) this else null
