/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package kotlin.text

actual class StringBuilder private constructor (
        private var array: CharArray) : CharSequence, Appendable {

    actual constructor() : this(10)

    actual constructor(capacity: Int) : this(CharArray(capacity))

    actual constructor(content: String) : this(content.toCharArray()) {
        _length = array.size
    }

    actual constructor(content: CharSequence): this(content.length) {
        append(content)
    }

    // Of CharSequence.
    private var _length: Int = 0
        set(capacity) {
            ensureCapacity(capacity)
            field = capacity
        }
    actual override val length: Int
        get() = _length

    actual override fun get(index: Int): Char {
        checkIndex(index)
        return array[index]
    }

    actual override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = substring(startIndex, endIndex)

    // Of Appenable.
    actual override fun append(c: Char) : StringBuilder {
        ensureExtraCapacity(1)
        array[_length++] = c
        return this
    }

    actual override fun append(csq: CharSequence?): StringBuilder {
        // Kotlin/JVM processes null as if the argument was "null" char sequence.
        val toAppend = csq ?: "null"
        return append(toAppend, 0, toAppend.length)
    }

    actual override fun append(csq: CharSequence?, start: Int, end: Int): StringBuilder = this.appendRange(csq, start, end)

    // Based on Apache Harmony implementation.
    actual fun reverse(): StringBuilder {
        if (this.length < 2) {
            return this
        }
        var end = _length - 1
        var front = 0
        var frontLeadingChar = array[0]
        var endTrailingChar = array[end]
        var allowFrontSurrogate = true
        var allowEndSurrogate = true
        while (front < _length / 2) {

            var frontTrailingChar = array[front + 1]
            var endLeadingChar = array[end - 1]
            var surrogateAtFront = allowFrontSurrogate && frontTrailingChar.isLowSurrogate() && frontLeadingChar.isHighSurrogate()
            if (surrogateAtFront && _length < 3) {
                return this
            }
            var surrogateAtEnd = allowEndSurrogate && endTrailingChar.isLowSurrogate() && endLeadingChar.isHighSurrogate()
            allowFrontSurrogate = true
            allowEndSurrogate = true
            when {
                surrogateAtFront && surrogateAtEnd -> {
                    // Both surrogates - just exchange them.
                    array[end] = frontTrailingChar
                    array[end - 1] = frontLeadingChar
                    array[front] = endLeadingChar
                    array[front + 1] = endTrailingChar
                    frontLeadingChar = array[front + 2]
                    endTrailingChar = array[end - 2]
                    front++
                    end--
                }
                !surrogateAtFront && !surrogateAtEnd -> {
                    // Neither surrogates - exchange only front/end.
                    array[end] = frontLeadingChar
                    array[front] = endTrailingChar
                    frontLeadingChar = frontTrailingChar
                    endTrailingChar = endLeadingChar
                }
                surrogateAtFront && !surrogateAtEnd -> {
                    // Surrogate only at the front -
                    // move the low part, the high part will be moved as a usual character on the next iteration.
                    array[end] = frontTrailingChar
                    array[front] = endTrailingChar
                    endTrailingChar = endLeadingChar
                    allowFrontSurrogate = false
                }
                !surrogateAtFront && surrogateAtEnd -> {
                    // Surrogate only at the end -
                    // move the high part, the low part will be moved as a usual character on the next iteration.
                    array[end] = frontLeadingChar
                    array[front] = endLeadingChar
                    frontLeadingChar = frontTrailingChar
                    allowEndSurrogate = false
                }
            }
            front++
            end--
        }
        if (_length % 2 == 1 && (!allowEndSurrogate || !allowFrontSurrogate)) {
            array[end] = if (allowFrontSurrogate) endTrailingChar else frontLeadingChar
        }
        return this
    }

    actual fun append(obj: Any?): StringBuilder = append(obj.toString())

    // TODO: optimize those!
    actual fun append(boolean: Boolean): StringBuilder = append(boolean.toString())
    fun append(it: Byte) = append(it.toString())
    fun append(it: Short) = append(it.toString())
    fun append(it: Int): StringBuilder {
        ensureExtraCapacity(11)
        _length += insertInt(array, _length, it)
        return this
    }
    fun append(it: Long) = append(it.toString())
    fun append(it: Float) = append(it.toString())
    fun append(it: Double) = append(it.toString())

    actual fun append(chars: CharArray): StringBuilder {
        ensureExtraCapacity(chars.size)
        chars.copyInto(array, _length)
        _length += chars.size
        return this
    }

    actual fun append(string: String): StringBuilder {
        ensureExtraCapacity(string.length)
        _length += insertString(array, _length, string)
        return this
    }

    actual fun capacity(): Int = array.size

    actual fun ensureCapacity(minimumCapacity: Int) {
        if (minimumCapacity > array.size) {
            var newSize = array.size * 2 + 2
            if (minimumCapacity > newSize)
                newSize = minimumCapacity
            array = array.copyOf(newSize)
        }
    }

    actual fun indexOf(string: String): Int {
        return (this as CharSequence).indexOf(string, startIndex = 0, ignoreCase = false)
    }

    actual fun indexOf(string: String, startIndex: Int): Int {
        return (this as CharSequence).indexOf(string, startIndex, ignoreCase = false)
    }

    actual fun lastIndexOf(string: String): Int {
        return (this as CharSequence).lastIndexOf(string, startIndex = lastIndex, ignoreCase = false)
    }

    actual fun lastIndexOf(string: String, startIndex: Int): Int {
        return (this as CharSequence).lastIndexOf(string, startIndex, ignoreCase = false)
    }

    // TODO: optimize those!
    actual fun insert(index: Int, boolean: Boolean): StringBuilder = insert(index, boolean.toString())
    fun insert(index: Int, value: Byte)    = insert(index, value.toString())
    fun insert(index: Int, value: Short)   = insert(index, value.toString())
    fun insert(index: Int, value: Int)     = insert(index, value.toString())
    fun insert(index: Int, value: Long)    = insert(index, value.toString())
    fun insert(index: Int, value: Float)   = insert(index, value.toString())
    fun insert(index: Int, value: Double)  = insert(index, value.toString())

    actual fun insert(index: Int, char: Char): StringBuilder {
        checkInsertIndex(index)
        ensureExtraCapacity(1)
        val newLastIndex = lastIndex + 1
        for (i in newLastIndex downTo index + 1) {
            array[i] = array[i - 1]
        }
        array[index] = char
        _length++
        return this
    }

    actual fun insert(index: Int, chars: CharArray): StringBuilder {
        checkInsertIndex(index)
        ensureExtraCapacity(chars.size)

        array.copyInto(array, startIndex = index, endIndex = _length, destinationOffset = index + chars.size)
        chars.copyInto(array, destinationOffset = index)

        _length += chars.size
        return this
    }

    actual fun insert(index: Int, csq: CharSequence?): StringBuilder {
        // Kotlin/JVM inserts the "null" string if the argument is null.
        val toInsert = csq ?: "null"
        return insertRange(index, toInsert, 0, toInsert.length)
    }

    @Deprecated("Renamed to `insertRange`", ReplaceWith("insertRange(indexm csq, start, end)"), DeprecationLevel.ERROR)
    fun insert(index: Int, csq: CharSequence?, start: Int, end: Int): StringBuilder = this.insertRange(index, csq, start, end)

    actual fun insert(index: Int, obj: Any?): StringBuilder = insert(index, obj.toString())

    actual fun insert(index: Int, string: String): StringBuilder {
        checkInsertIndex(index)
        ensureExtraCapacity(string.length)
        array.copyInto(array, startIndex = index, endIndex = _length, destinationOffset = index + string.length)
        _length += insertString(array, index, string)
        return this
    }

    actual fun setLength(newLength: Int) {
        _length = newLength
    }

    actual fun substring(startIndex: Int, endIndex: Int): String {
        checkInsertIndex(startIndex)
        checkInsertIndexFrom(endIndex, startIndex)
        return unsafeStringFromCharArray(array, startIndex, endIndex - startIndex)
    }

    actual fun substring(startIndex: Int): String {
        return substring(startIndex, _length)
    }

    actual fun trimToSize() {
        if (_length < array.size)
            array = array.copyOf(_length)
    }

    override fun toString(): String = unsafeStringFromCharArray(array, 0, _length)

    @Deprecated("Renamed to `set`", ReplaceWith("set(index, value)"), DeprecationLevel.ERROR)
    fun setCharAt(index: Int, value: Char) = set(index, value)

    operator fun set(index: Int, value: Char) {
        checkIndex(index)
        array[index] = value
    }

    fun setRange(startIndex: Int, endIndex: Int, string: String): StringBuilder {
        val lengthDiff = string.length - (endIndex - startIndex)
        ensureExtraCapacity(_length + lengthDiff)
        array.copyInto(array, startIndex = endIndex, endIndex = _length, destinationOffset = startIndex + string.length)
        var replaceIndex = startIndex
        for (index in 0 until string.length) array[replaceIndex++] = string[index] // optimize
        _length += lengthDiff

        return this
    }

    @Deprecated("Renamed to `deleteAt`", ReplaceWith("deleteAt(index)"), DeprecationLevel.ERROR)
    fun deleteCharAt(index: Int) = deleteAt(index)

    fun deleteAt(index: Int): StringBuilder {
        checkIndex(index)
        array.copyInto(array, startIndex = index + 1, endIndex = _length, destinationOffset = index)
        --_length
        return this
    }

    fun deleteRange(startIndex: Int, endIndex: Int): StringBuilder {
        if (startIndex < 0 || startIndex > length) {
            throw IndexOutOfBoundsException("startIndex: $startIndex, length: $length")
        }
        if (startIndex > endIndex) {
            throw IllegalArgumentException("startIndex($startIndex) > endIndex($endIndex)")
        }

        array.copyInto(array, startIndex = endIndex, endIndex = _length, destinationOffset = startIndex)
        _length -= endIndex - startIndex
        return this
    }

    fun toCharArray(destination: CharArray, destinationOffset: Int, startIndex: Int, endIndex: Int) {
        array.copyInto(destination, destinationOffset, startIndex, endIndex)
    }

    fun appendRange(chars: CharArray, startIndex: Int, endIndex: Int): StringBuilder {
        if (startIndex < 0 || endIndex > chars.size || startIndex > endIndex) throw IndexOutOfBoundsException()
        val extraLength = endIndex - startIndex
        ensureExtraCapacity(extraLength)
        chars.copyInto(array, _length, startIndex, endIndex)
        _length += extraLength
        return this
    }

    fun appendRange(csq: CharSequence?, startIndex: Int, endIndex: Int): StringBuilder {
        // Kotlin/JVM processes null as if the argument was "null" char sequence.
        val toAppend = csq ?: "null"
        if (startIndex < 0 || endIndex > toAppend.length || startIndex > endIndex) throw IndexOutOfBoundsException()
        val extraLength = endIndex - startIndex
        ensureExtraCapacity(extraLength)
        (toAppend as? String)?.let {
            _length += insertString(array, _length, it, startIndex, extraLength)
            return this
        }
        var index = startIndex
        while (index < endIndex)
            array[_length++] = toAppend[index++]
        return this
    }


    fun insertRange(index: Int, csq: CharSequence?, startIndex: Int, endIndex: Int): StringBuilder {
        // Kotlin/JVM processes null as if the argument was "null" char sequence.
        val toInsert = csq ?: "null"
        if (startIndex < 0 || endIndex > toInsert.length || startIndex > endIndex) throw IndexOutOfBoundsException()
        checkInsertIndex(index)
        val extraLength = endIndex - startIndex
        ensureExtraCapacity(extraLength)

        array.copyInto(array, startIndex = index, endIndex = _length, destinationOffset = index + extraLength)
        var from = startIndex
        var to = index
        while (from < endIndex) {
            array[to++] = toInsert[from++]
        }

        _length += extraLength
        return this
    }

    fun insertRange(index: Int, chars: CharArray, startIndex: Int, endIndex: Int): StringBuilder {
        checkInsertIndex(index)
        if (startIndex < 0 || endIndex > chars.size || startIndex > endIndex) throw IndexOutOfBoundsException()

        val extraLength = endIndex - startIndex
        array.copyInto(array, startIndex = index, endIndex = _length, destinationOffset = index + extraLength)
        chars.copyInto(array, startIndex = startIndex, endIndex = endIndex, destinationOffset = index)

        _length += extraLength
        return this
    }

    // ---------------------------- private ----------------------------

    private fun ensureExtraCapacity(n: Int) {
        ensureCapacity(_length + n)
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= _length) throw IndexOutOfBoundsException()
    }

    private fun checkInsertIndex(index: Int) {
        if (index < 0 || index > _length) throw IndexOutOfBoundsException()
    }

    private fun checkInsertIndexFrom(index: Int, fromIndex: Int) {
        if (index < fromIndex || index > _length) throw IndexOutOfBoundsException()
    }
}

/**
 * Clears the content of this string builder making it empty.
 *
 * @sample samples.text.Strings.clearStringBuilder
 */
@SinceKotlin("1.3")
public actual fun StringBuilder.clear(): StringBuilder = apply { setLength(0) }

/**
 * Sets the character at the specified [index] to the specified [value].
 */
@kotlin.internal.InlineOnly
public actual inline operator fun StringBuilder.set(index: Int, value: Char): Unit = this.set(index, value)

public actual inline fun StringBuilder.setRange(startIndex: Int, endIndex: Int, string: String): StringBuilder =
        this.setRange(startIndex, endIndex, string)

public actual inline fun StringBuilder.deleteAt(index: Int): StringBuilder = this.deleteAt(index)

public actual inline fun StringBuilder.deleteRange(startIndex: Int, endIndex: Int): StringBuilder = this.deleteRange(startIndex, endIndex)

public actual inline fun StringBuilder.toCharArray(destination: CharArray, destinationOffset: Int, startIndex: Int, endIndex: Int) =
        this.toCharArray(destination, destinationOffset, startIndex, endIndex)

public actual inline fun StringBuilder.appendRange(chars: CharArray, startIndex: Int, endIndex: Int): StringBuilder =
        this.appendRange(chars, startIndex, endIndex)

public actual inline fun StringBuilder.appendRange(csq: CharSequence?, startIndex: Int, endIndex: Int): StringBuilder =
        this.appendRange(csq, startIndex, endIndex)

public actual inline fun StringBuilder.insertRange(index: Int, chars: CharArray, startIndex: Int, endIndex: Int): StringBuilder =
        this.insertRange(index, chars, startIndex, endIndex)

public actual inline fun StringBuilder.insertRange(index: Int, csq: CharSequence?, startIndex: Int, endIndex: Int): StringBuilder =
        this.insertRange(index, csq, startIndex, endIndex)
