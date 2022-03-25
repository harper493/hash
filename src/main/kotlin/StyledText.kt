/**
 * StyledText class. Represents text with associated styling (color and
 * other style, e.g. italic, bold).
 *
 * A single styled text segment is created using
 * StyledText(text, [color=named color],
 *                  [style=named style],
 *                  [background=named color])
 *
 * Different styles can be applied to different text segments by creating a StyledText
 * from a list of individual segments, e.g.:
 *
 * StyledText(StyledText("Hello", color="red", style="italic"),
 *            StyledText(" world", color="green", style="bold"))
 *
 * Styles can be applied to a container, in which case they apply to the elements
 * within the container if nothing else is specified.
 *
 * A StyledText object can be turned into text using the render() function.
 * This is automatically invoked where a String is required.
 *
 * The render mode can be set using the companion object function setRenderer().
 * Current possible values are:
 *
 * "ISO6429" - VT100 style escape sequences
 * "Plain" - don't apply markup
 *
 * Valid colors and styles are at the end of this file.
 *
 * See code below for other functions.
 */

class StyledText (
    private var text: String = "",
    private var color: String? = null,
    private var background: String? = null,
    private var style: String? = null
) {
    constructor(input: Iterable<StyledText>,
                color: String? = null,
                background: String? = null,
                style: String? = null): this(color=color, background=background, style=style) {
        input.map{ append(it.underrideFrom(this)) }
    }
    constructor(vararg input: StyledText,
                color: String? = null,
                background: String? = null,
                style: String? = null): this(color=color, background=background, style=style) {
        input.map{ append(it.underrideFrom(this)) }
    }
    private val nestedText = mutableListOf<StyledText>()
    private val isNested get() = nestedText.isNotEmpty()
    val length get() = text.length
    fun getColor() = color
    fun getText() = text

    fun render() = renderer(this)

    /*
     * append - append another StyledText object
     */

    fun append(st: StyledText): StyledText =
        also {
            when {
                isNested && st.isNested ->
                    st.nestedText.map { nestedText.add(it.underrideFrom(this)) }
                isNested ->
                    nestedText.add(st.underrideFrom(this))
                else -> {
                    nestedText.add(clone())
                    text = ""
                    append(st.underrideFrom(this))
                }
            }
        }

    /*
     * append - append a single piece of text
     */

    fun append(text: String = "",
               color: String? = null,
               background: String? = null,
               style: String? = null) =
        also {
            append(StyledText(text, color, background, style)
                .underrideFrom(this))
        }

    fun isEmpty() = text.isEmpty() && nestedText.isEmpty()
    fun isNotEmpty() = text.isNotEmpty() || nestedText.isNotEmpty()

    fun clone(
        newText: String? = null,
        newColor: String? = null,
        newBackground: String? = null,
        newStyle: String? = null
    ) = StyledText(
        newText ?: text,
        newColor ?: color,
        newBackground ?: background,
        newStyle ?: style
    )

    fun underride(
        newColor: String? = null,
        newBackground: String? = null,
        newStyle: String? = null
    ) = also {
        color = color ?: newColor
        background = background ?: newBackground
        style = style ?: newStyle
    }

    fun underrideFrom(other: StyledText) =
        underride(other.color, other.background, other.style)

    fun override(
        newColor: String? = null,
        newBackground: String? = null,
        newStyle: String? = null
    ) = also {
        color = newColor ?: color
        background = newBackground ?: background
        style = newStyle ?: style
    }

    fun addStyle(newStyle: String) = also {
        style = addStyle(style, newStyle)
    }

    fun justify(width: Int)= also {
        text = text.justify(width)
    }

    private fun translateColor(color: String?): Int? =
        colors[color ?: ""]
    private fun renderColor(op: Int, color: String?) =
        translateColor(color)?.let{ "${escape}[${op}:5:${it}m" }
            ?: "${escape}[${op + 1}m"

    private fun renderStyle() =
        style?.let {
            style!!.split(",")
                .joinToString("", transform = { "${escape}[${styles[it] ?: 0}m" })
        } ?: "${escape}[0m"

    private fun renderPlain(): String =
        if (nestedText.isEmpty()) {
            text
        } else {
            nestedText.joinToString("") { it.renderPlain() }
        }

    private fun renderISO6429(): String =
        if (nestedText.isEmpty()) {
            "${renderStyle()}${renderColor(fgOp, color)}${renderColor(bgOp, background)}$text"
        } else {
            nestedText.joinToString("") { it.renderISO6429() }
        }

    fun renderStyled() = this

    override fun toString() = render()

    companion object {
        private val colors = mapOf(
            "black" to 232,
            "red" to 9,
            "even_red" to 124,
            "green" to 40,
            "even_green" to 28,
            "yellow" to 11,
            "blue" to 20,
            "magenta" to 90,
            "cyan" to 14,
            "white" to 15,
            "grey" to 244,
            "even_grey" to 239,
            "deep_blue" to 20,
            "mid_blue" to 27,
            "orange" to 208,
            "pink" to 201,
            "brown" to 1,
            "yucky_green" to 52,
            "yucky_brown" to 22,
            "label" to 124,
            "even_label" to 28,
            "value" to 52,
            "even_value" to 22,
        )

        private val styles = mapOf(
            "none" to 0,
            "bold" to 1,
            "italic" to 3,
            "blink" to 5,
            "underline" to 4,
            "crossed" to 9,
            "inverted" to 7,
        )

        private const val escape = "\u001b"
        private const val fgOp = 38
        private const val bgOp = 48

        private lateinit var renderer: (StyledText) -> String
        fun addStyle(oldStyle: String?, newStyle: String) =
            addToTextList(oldStyle, newStyle)
        fun setRenderer(style: String) {
            renderer = when (style) {
                "ISO6429" -> { text -> text.renderISO6429() }
                else      -> { text -> text.renderPlain() }
            }
        }
        init { setRenderer("ISO6429") }
    }
}

fun Iterable<StyledText>.join(separator: StyledText) =
    StyledText(joinWith(separator))

fun Iterable<StyledText>.join(separator: String) =
    join(StyledText(separator))

/**
 * Pad a sting to fit the given [width]. If [width] is negative, left justify,
 * otherwise right justify. If [width] is zero, do nothing.
 */

fun String.justify(width: Int) = when {
    width < 0 -> padStart(-width)
    width > 0 -> padEnd(width)
    else      -> this // ==0
}

fun<T> Iterable<T>.joinWith(separator: T) =
    map{ listOf(it, separator) }.flatten().dropLast(1)

/**
 * Returns a comma (or other [delimiter]) separated list with the addition of one or more
 * new items. The result is null if it would otherwise be empty.
 */

fun addToTextList(old: String?, new: String, delimiter: String = ",") =
    (old ?: "")
        .split(delimiter)
        .filter{ it.isNotEmpty() }
        .toMutableList()
        .also { it.addAll(new.split(delimiter)) }
        .joinToString(delimiter)
        .ifBlank { null }

