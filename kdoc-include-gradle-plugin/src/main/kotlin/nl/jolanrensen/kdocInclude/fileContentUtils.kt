package nl.jolanrensen.kdocInclude

/**
 * Last index of not [char] moving from startIndex down to 0.
 * Returns 0 if char is not found (since last index looked at is 0).
 * Returns `this.size` if [char] is found at [startIndex].
 *
 * @receiver
 * @param char Char
 * @param startIndex Start index
 * @return
 */
fun String.lastIndexOfNot(char: Char, startIndex: Int = lastIndex): Int {
    for (i in startIndex downTo 0) {
        if (this[i] == char) return i + 1
    }
    return 0
}

