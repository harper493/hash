import kotlin.math.absoluteValue

open class Table (
    private val maxColumnWidth: Int = 0,
    private val columnSpacing: Int = 2,
    private val squishHeadings: Boolean = true,
    private val headingColor: String? = null,
    private val headingBackground: String? = null,
    private val headingStyle: String? = null,
    private val underlineHeadings: Boolean = true,
    private val stripeColors: Iterable<String?>? = null,
    private val showHeadings: Boolean = true,
    private val verticalPadAbove: Boolean = false,
) {
    class Column(
        var heading: String,
        var position: Int = --defaultPosition,
        var maxWidth: Int = 0
    ) {
        val content: MutableList<StyledText> = mutableListOf()
        val size get() = content.size
        val width get() = content.map { it.length }.maxOrNull() ?: 0

        fun padTo(n: Int): Column {
            repeat(n - content.size) { content += StyledText("") }
            return this
        }

        fun append(
            text: String,
            color: String? = null,
            background: String? = null,
            style: String? = null
        ): Column {
            content += StyledText(text, color, background, style)
            return this
        }

        companion object {
            private var defaultPosition = 10000
        }
    }

    private val columns = mutableMapOf<String, Column>()
    private var sortedCols: List<Column> = listOf()

    private val breadth get() = columns.size
    private val depth get() = columns.values.map { it.size }.maxOrNull() ?: 0
    private var headings = listOf<StyledText>()
    private var wrappedHeadings = listOf<List<StyledText>>()
    private var body = listOf<List<StyledText>>()

    fun append(
        columnName: String,
        text: String,
        color: String? = null,
        background: String? = null,
        style: String? = null
    ) = also {
        (columns[columnName]
            ?: Column(columnName, breadth, maxColumnWidth)
                .also { columns[columnName] = it })
            .padTo(depth - 1)
            .append(text, color, background, style)
    }

    fun append(vararg values: Pair<String,String>,
               color: String? = null,
               background: String? = null,
               style: String? = null) = also {
        values.map{ append(it.first, it.second,
            color=color, background=background, style=style) }
    }

    fun orderColumns(fn: (String) -> Int) =
        also {
            for (col in columns.keys) columns[col]!!.position = fn(col)
        }

    fun setColumnWidths(fn: (String) -> Int) =
        also {
            for (col in columns.keys) columns[col]!!.maxWidth = fn(col)
        }

    fun setHeaders(fn: (String) -> String) =
        also {
            for (col in columns.keys) columns[col]!!.heading = fn(col)
        }


    fun setColumns(fn: (String, Column) -> Unit) =
        also {
            for ((name, col) in columns) fn(name, col)
            return this
        }

    fun isEmpty() = columns.isEmpty()

    private fun splitCells(row: Iterable<StyledText>, padAtEnd: Boolean): List<List<StyledText>> {
        val splitRows = sortedCols.zip(row).map { colCell ->
            colCell.second.getText().wrap(colCell.first.maxWidth.absoluteValue, force=true).toMutableList()
        }
        val depth = splitRows.maxSize()
        for (subCol in splitRows) {
            repeat(depth - subCol.size) { subCol.add((if (padAtEnd) subCol.size else 0), "") }
        }
        return splitRows.transpose().map { subRow ->
            row.zip(subRow)
                .map { rowSub ->
                    rowSub.first.clone(rowSub.second)
                }
        }
    }

    private fun complete() = also {
        if (sortedCols.isEmpty()) {
            columns.values.map { it.padTo(depth) }
            sortedCols = columns.values.sortedBy { it.position }
            sortedCols.map { col ->
                val w = if (col.maxWidth != 0) minOf(col.maxWidth.absoluteValue, col.width)
                else col.width
                val s = if (col.maxWidth < 0) -1 else 1
                col.maxWidth = s * maxOf(
                    w,
                    if (squishHeadings) col.heading.wrap(maxOf(w, 1))
                        .map { it.length }.maxOrNull() ?: 0
                    else col.heading.length
                )
            }
            headings = sortedCols.map {
                StyledText(it.heading, headingColor, headingBackground, headingStyle)
            }
            sortedCols.map { col ->
                val colorIterator = (stripeColors?.anyOrNull() ?: listOf(null)).cycle().iterator()
                col.content.map { it.underride(newColor = colorIterator.next()) }
            }
            if (showHeadings) {
                wrappedHeadings = splitCells(headings, padAtEnd = false)
                if (underlineHeadings && wrappedHeadings.isNotEmpty()) {
                    for (h in wrappedHeadings.last()) h.addStyle("underline")
                }
            } else {
                wrappedHeadings = listOf()
            }
            body = sortedCols.map {
                it.content
            }.transpose().map {
                splitCells(it, padAtEnd = !verticalPadAbove)
            }.flatten()
        }
    }

    fun render(): String {
        complete()
        return listOf(wrappedHeadings, body).flatMap { rows ->
            rows.map { row ->
                sortedCols.zip(row).joinToString(" ".repeat(columnSpacing)) { colRow ->
                    colRow.second.justify(colRow.first.maxWidth).render()
                }
            }
        }.joinToString("\n")
    }

    fun renderStyled(): StyledText {
        complete()
        val spacer = StyledText(" ".repeat(columnSpacing))
        return listOf(wrappedHeadings, body).flatMap { rows ->
            rows.map { row ->
                sortedCols.zip(row).map { colRow ->
                    colRow.second.justify(colRow.first.maxWidth)
                }
            }
        }.map{it.join(spacer)}
            .join("\n")
    }

}

/**
 * Hyphenate string, choosing the longest fragment that will fit in [size],
 * returning a pair corresponding to the split. If there is no suitable hyphenation
 * the result is an empty string followed by the full word.
 *
 * If [size] is -1, pick the shortest fragment.
 */

fun String.hyphenate(size: Int): Pair<String,String> =
    Pair("", this)
/*
    if ("-" in this) Pair("", this)
    else splitAt(
        (Properties.get("hyphenate", this.toLowerCase()) ?: this)
            .split("-")
            .map{ it.length }
            .let{
                when {
                    size < 0 && it.first() < length -> it.first()
                    size < 0 && it.first() >= length -> 0
                    else ->  it.chooseSplit(size, takeFirst = false)
                }
            })
*/


/**
 * Default splitter function for wrap. Split the string at the first non-alphanumeric
 * character, ignoring any such at the beginning. If the character is in ',;-' split
 * immediately after it. Otherwise, splt before it.
 */

fun baseSplitter(text: String): Pair<String, String> =
    Regex("^([^a-zA_Z0-9]*)([a-zA-Z0-9]*)(.*)$").find(text)?.groupValues!!
        .let{ match ->
            val (prefix, body, remnant) = Triple(match[1], match[2], match[3])
            when {
                remnant.isEmpty() -> Pair(text, "")
                remnant[0] in ",;-" -> Pair("$prefix$body${remnant[0]}", remnant.drop(1))
                else -> Pair("$prefix$body", remnant)
            }
        }

/**
 * Wrap a string to fit within a given width, breaking the text at spaces as
 * necessary. If [force] is true, lines which are still too long are just
 * split at the required length. Otherwise, it does its best but the
 * longest line  may be longer than [width].
 *
 * [splitter] should be a regex which will split a string in an
 * an intelligent way (see baseSplitter).
 */

fun String.wrap(width: Int, force: Boolean = false,
                splitter: (String)->Pair<String,String> = { baseSplitter(it) }): Iterable<String> =
    with (split("\n")) {
        if (width==0) this
        else map { it.wrapLine(width, force, splitter) }.flatten()
    }

fun String.wrapLine(width: Int,
                    force: Boolean = false,
                    splitter: (String)->Pair<String,String>): Iterable<String> {
    var newWidth = width
    var line = ""
    return if (width==0) {
        listOf(this)
    } else {
        this.trim().split(" ").map { it.trim() }
            .map { word ->
                val thisResult = mutableListOf<String>()
                val space = if (line.isEmpty()) "" else " "
                if (line.length + word.length + space.length <= newWidth) {
                    line = "$line$space$word"
                } else {
                    if (!force) {
                        newWidth = maxOf(newWidth, line.length)
                    }
                    var (prefix, residue) = word.hyphenate(maxOf(0, newWidth - line.length - space.length - 1))
                    if (prefix.isNotEmpty()) {
                        if (line.length + prefix.length + space.length <= newWidth - 1) {
                            line = "$line$space${prefix}-"
                        } else {
                            residue = prefix + residue
                        }
                    }
                    if (line.isNotEmpty()) {
                        thisResult.add(line)
                        newWidth = maxOf(newWidth, line.length)
                        line = ""
                    }
                    if (residue.length > newWidth) {
                        if (force) {
                            residue.hyphenate(-1)
                                .also {
                                    prefix = it.first
                                    residue = it.second
                                }
                            if (prefix.length > newWidth - 1) {
                                val chunks =
                                    prefix.splitUsing(splitter, newWidth).map { it.chunked(newWidth) }.flatten()
                                thisResult.addAll(chunks.dropLast(1))
                                residue = chunks.last()
                            } else if (prefix.isNotEmpty()) {
                                thisResult.add("${prefix}-")
                            }
                            if (residue.length > newWidth) {
                                val chunks =
                                    residue.splitUsing(splitter, newWidth).map { it.chunked(newWidth) }.flatten()
                                thisResult.addAll(chunks.dropLast(1))
                                residue = chunks.last()
                            }
                        } else {
                            val h3 = residue.hyphenate(-1)
                            if (h3.first.isNotEmpty()) {
                                thisResult.add("${h3.first}-")
                                residue = h3.second
                            }
                        }
                    }
                    line = residue
                }
                thisResult
            }.flatten()
            .appendIf(line) { line.isNotEmpty() }
    }
}

/**
 * Make a [Sequence] returning elements from the iterable and saving a copy of each.
 * When the iterable is exhausted, return elements from the saved copy. Repeats indefinitely.
 *
 */

fun<T> Iterable<T>.cycle(): Sequence<T> = sequence {
    val saved = mutableListOf<T>()
    for (elem in this@cycle) {
        saved.add(elem)
        yield(elem)
    }
    while (true) {
        for (elem in saved) yield(elem)
    }
}

/**
 * Append one list to another
 */

fun<T> MutableList<T>.append(other: Iterable<T>): MutableList<T> {
    for (t in other) {
        add(t)
    }
    return this
}

fun<T> Iterable<T>.append(other: Iterable<T>) = listOf(this, other).flatten()

fun<T> Iterable<T>.appendIf(other: Iterable<T>, fn: ()->Boolean) =
    if (fn()) append(other) else this

fun<T> Iterable<T>.append(other: T) = append(listOf(other))

fun<T> Iterable<T>.appendIf(other: T, fn: ()->Boolean) =
    if (fn()) append(other) else this

/**
 * Given a list of lists, return a list of lists transposed, i.e. if they
 * were in column order, they are now in row order
 */

fun<T> Iterable<Iterable<T>>.transpose() : Iterable<Iterable<T>> {
    val colIters = map{ it.iterator() }
    val result: MutableList<List<T>> = mutableListOf()
    if (colIters.isNotEmpty()) {
        while (colIters.map { it.hasNext() }.all { it }) {
            result.add(colIters.map { it.next() })
        }
    }
    return result
}

/**
 * Return the given iterable if it is not empty, else null
 */

fun<T> Iterable<T>.anyOrNull(): Iterable<T>? = if (any()) this else null

/**
 * Split a string into two at the given [index]
 */

fun String.splitAt(index: Int) =
    if (index < length) Pair(take(index), drop(index)) else Pair(this, "")

/**
 * Split a string into multiple pieces at the given locations. Any index
 * which is out of range or out of order is ignored.
 */

fun String.splitAt(indices: Iterable<Int>): List<String> {
    var prevSplit = 0
    return indices
        .filter{it in 1..(this.length)}
        .makeAscending()
        .map{ index ->
            this.drop(prevSplit)
                .take(index - prevSplit)
                .also{ prevSplit = index }
        }
        .append(this.drop(prevSplit))
}

/**
 * Given a [splitter] function that will divide a string in two, apply it repeatedly
 * to break the string into multiple pieces wherever the splitter applies.
 */

fun String.splitBy(splitter: (String)->Pair<String,String>): List<String> =
    if (isNotEmpty()) {
        splitter(this).let {
            listOf(it.first).append(if (it.first.isNotEmpty()) it.second.splitBy(splitter) else listOf())
        }
    } else listOf()

/**
 * Given a splitter function and a target size, split the string into pieces no
 * larger than the given size, according to the splitter function, if possible.
 */

fun String.splitUsing(splitter: (String)->Pair<String,String>, size: Int): List<String> {
    val substrs = splitBy { splitter(it) }
    return splitAt(substrs.map{it.length}.runningReduceLimit(size).runningReduce{ a,b -> a+b})
}

/**
 * Given a sequence of integers and a limit, return the highest partial
 * sum starting at the beginning which is less than or equal to the limit.
 * If the first value is already too large, return either it or 0 depending
 * on whether takeFirst is true.
 */

fun Iterable<Int>.chooseSplit(limit: Int, takeFirst: Boolean=false): Int =
    runningReduce { pfx, sz -> pfx + sz }
        .let { cumSizes ->
            when {
                cumSizes.isEmpty() -> 0
                takeFirst && cumSizes.first() >= limit -> cumSizes.first()
                else -> cumSizes.lastOrNull { it <= limit } ?: 0
            }
        }

/**
 * Given a sequence of integers, return only those which are greater
 * than all previous values.
 */

fun Iterable<Int>.makeAscending() =
    take(1)
        .append(this.runningReduce{a, b -> maxOf(a, b)}.dropLast(1)
            .zip(this.drop(1))
            .filter{ it.first < it.second }
            .map{ it.second })

/**
 * Given a list of integers, return them grouped and summed in
 * such a way that no total exceeds the given limit, unless an
 * individual value does. E.g.
 *
 * 1 2 3 6 3 2 1 limit=5 => 3 3 6 5 1
 */

fun Iterable<Int>.runningReduceLimit(limit: Int): Iterable<Int> {
    var sum = 0
    return  map { n ->
        when {
            n >= limit && sum > 0 -> listOf(sum, n).also { sum = 0 }
            n >= limit && sum == 0 -> listOf(n)
            sum + n > limit -> listOf(sum).also { sum = n }
            else -> listOf(null).also { sum += n }
        }
    }.flatten()
        .append(if (sum>0) sum else null)
        .filterNotNull()
}

/**
 * Given a list of lists, return the size of the largest sub-list
 */

fun<T> Iterable<List<T>>.maxSize() : Int = map{it.size}.maxOrNull() ?: 0

