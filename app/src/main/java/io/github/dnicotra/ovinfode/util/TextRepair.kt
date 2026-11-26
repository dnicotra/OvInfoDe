package io.github.dnicotra.ovinfode.util

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/*
 * Repairs for texts that a timetable feed encoded wrongly. Seen in the Italian feeds in
 * November 2026: ATM in Milan sends "bicocca (universitÃ )" for "università" (UTF-8 read as
 * Latin-1), Roma Mobilità alerts contain Windows-1252 quotes as control characters ("dell\u0092Arco"),
 * HTML entities ("dell&#039;Esquilino") and Windows line breaks.
 */

// "Ã" or "Â" followed by a Latin-1 continuation byte: two bytes of one UTF-8 character.
private val mojibake = Regex("[ÂÃ][\u0080-¿]")
private val c1Controls = Regex("[\u0080-\u009f]")

// What Windows-1252 has at 0x80–0x9F, where Latin-1 has control characters ("\u0000": nothing).
private const val CP1252 = "€\u0000‚ƒ„…†‡ˆ‰Š‹Œ\u0000Ž\u0000\u0000‘’“”•–—˜™š›œ\u0000žŸ"

/** Undoes UTF-8 read as Latin-1 and Windows-1252 read as Latin-1. Leaves correct text alone. */
fun repairEncoding(text: String): String {
    var result = text
    if (mojibake.containsMatchIn(result) && result.all { it.code <= 0xff }) {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        try {
            result = decoder.decode(ByteBuffer.wrap(result.toByteArray(Charsets.ISO_8859_1))).toString()
        } catch (_: CharacterCodingException) {
            // Not UTF-8 after all: keep the text.
        }
    }
    if (c1Controls.containsMatchIn(result)) {
        result = c1Controls.replace(result) { m -> CP1252[m.value[0].code - 0x80].takeIf { it != '\u0000' }?.toString().orEmpty() }
    }
    return result
}

private val entity = Regex("""&(#\d{1,6}|#x[0-9a-fA-F]{1,5}|amp|quot|apos|lt|gt|nbsp);""")

/** An alert text with [repairEncoding], HTML entities decoded and Unix line breaks. */
fun cleanAlertText(text: String): String {
    var result = repairEncoding(text).replace("\r\n", "\n").replace('\r', '\n')
    if ('&' in result) {
        result = entity.replace(result) { m ->
            when (val name = m.groupValues[1]) {
                "amp" -> "&"
                "quot" -> "\""
                "apos" -> "'"
                "lt" -> "<"
                "gt" -> ">"
                "nbsp" -> " "
                else -> {
                    val code = if (name.startsWith("#x")) name.drop(2).toInt(16) else name.drop(1).toInt()
                    if (Character.isValidCodePoint(code) && code != 0) String(Character.toChars(code)) else m.value
                }
            }
        }
    }
    return result
}
