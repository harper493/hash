import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.random.Random
import java.math.BigInteger

val size = findPrime(1000000).toInt()

data class Params(val size: Int, val bucketSize: Int, val load: Double)

fun main(args: Array<String>) {
    val load = args[0].toDouble()
    listOf(::t3, ::t4).forEach{ println("${it(load)}\n\n") }
}

fun t1(load: Double): String {
    val bucketSize = findPrime(128).toInt()
    val seq =  (1..19).map{ it.toDouble()/20.0 }.map{ it to Params(size, bucketSize, it) }
    return test("Load", seq)
}

fun t2(load: Double): String {
    val seq =  (4..10).map{ 1 shl it }.map{ it to Params(size, it, load) }
    return test("Bucket Size", seq)
}

fun t3(load: Double): String {
    val seq =  (6..20).map{ it to Params(size, it, load) }
    return test("Bucket Size", seq)
}

fun t4(load: Double): String {
    val bucketSize = 10
    val seq =  (6..20).map{ it.toDouble()/100.0 }.map{ it to Params(size, bucketSize, it) }
    return test("Load", seq)
}

fun crc(v: Long) =
    with (CRC32()) {
        var vv = v
        (0..7).forEach {
            update((vv.toInt() and 0xff))
            vv = vv shr 8
        }
        value
    }

fun linearRehash(h: Int, attempt: Int, size: Int) =
    quadraticRehash(h, 0, size)

fun quadraticRehash(h: Int, attempt: Int, size: Int) =
    (h + 1 + attempt) % size

fun<T> test(varName: String, seq: List<Pair<T,Params>>) =
    with (Table()) {
        seq.map { it to testOne(it.second.size, it.second.bucketSize, it.second.load) }
            .forEach {
                    append(varName, it.first.first.toString())
                    .append("Entries", "%5d".format(it.second.occupied))
                    .append(
                        "Load %",
                        "%6.1f".format((100 * it.second.occupied.toFloat() / it.first.second.size.toFloat()))
                    )
                    .append("Av Length", "%.2f".format(it.second.avgLength))
                    .append("Max Length", "%4d".format(it.second.maxLength))
                    .append("Failures", "%4d".format(it.second.failures))
            }
        render()
    }

fun testOne(size: Int, bucketSize: Int, load: Double): Stats {
    val ht = HashTable(size, bucketSize, ::crc, ::linearRehash, false)
    var failures = 0
    for (i in 0..(size * load).toInt()) {
        val v = Random.nextLong()
        if (!ht.insert(v)) {
            ++failures
        }
    }
    return ht.getStats().setFailures(failures)
}

fun isProbablyPrime(n: Long): Boolean {
    val nn = BigInteger(n.toString())
    return nn.isProbablePrime(10)
}

fun findPrime(n: Long): Long {
    (n downTo 1).forEach {
        if (isProbablyPrime(it)) return it
    }
    return 1 // can't happen
}



