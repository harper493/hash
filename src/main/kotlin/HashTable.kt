
data class Stats(val occupied: Int, val avgLength: Double, val maxLength: Int, var failures: Int=0) {

    fun setFailures(f: Int) =
        also {
            failures = f
        }
}

class HashTable<T> (val size: Int,
                    val bucketSize: Int,
                    val hashfn: (T)->Long,
                    val rehash: (Int, Int, Int)->Int,
                    val hashWithinBucket: Boolean = true) {
    val bucketCount = size / bucketSize

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

        fun find(hash: Long): T? {
            var e = getEntry(hash)
            var attempt = 0
            while (true) {
                val entry = content[e]
                when {
                    entry == null ->
                        return null
                    entry.hash == hash ->
                        return entry.value
                    attempt >= size ->
                        return null
                    else -> {
                        e = table.rehash(e, attempt, size)
                        ++attempt
                    }
                }
            }
        }

        fun getStats(): Stats =
            with(content.mapIndexed { index, _ -> chainLength(index) }) {
                Stats(entries, (filter{it>0}.nullIfEmpty()?.average() ?: 0.0), maxOf{ it })
            }

        fun chainLength(e: Int) =
            (0..size).fold(0 to e){ where, len ->
                if (content[where.second]==null) return where.first
                where.first + 1 to table.rehash(where.second, where.first, size)
            }.first
    }

    val buckets = Array<Bucket<T>>(bucketCount, { Bucket(this, bucketSize) })

    fun insert(entry: T): Boolean {
        val hash = hashfn(entry)
        val bucket = (hash % bucketCount).toInt()
        return buckets[bucket].insert(hash, entry)
    }

    fun find(entry: T): T? {
        val hash = hashfn(entry)
        val bucket = (hash % bucketCount).toInt()
        return buckets[bucket].find(hash)
    }

    fun getStats(): Stats =
        with(buckets.map{ it.getStats() }) {
            Stats(sumOf{ it.occupied }, map{ it.avgLength }.average(), map{ it.maxLength }.maxOf{ it })
        }
}

/**
 * Return the given iterable if it is not empty, else null
 */

fun<T> Iterable<T>.nullIfEmpty(): Iterable<T>? =
    if (any()) this else null
