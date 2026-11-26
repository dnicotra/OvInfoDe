package io.github.dnicotra.ovinfode.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TextRepairTest {

    @Test
    fun `utf-8 read as latin-1 is undone`() {
        // ATM Milan, 2026-11-27.
        assertEquals("bicocca (università)", repairEncoding("bicocca (universitÃ )"))
        assertEquals("Köln Hbf", repairEncoding("KÃ¶ln Hbf"))
        // Correct text, also with these letters, stays.
        assertEquals("SÃO PAULO", repairEncoding("SÃO PAULO"))
        assertEquals("Città", repairEncoding("Città"))
        assertEquals("Ã©t€", repairEncoding("Ã©t€"))
    }

    @Test
    fun `windows-1252 characters read as latin-1 are undone`() {
        // Roma Mobilità, 2026-11-27.
        assertEquals("Via dell’Arco di Travertino", repairEncoding("Via dell\u0092Arco di Travertino"))
        assertEquals("“A” – 5 €", repairEncoding("\u0093A\u0094 \u0096 5 \u0080"))
        assertEquals("Ÿ", repairEncoding("\u009f"))
        assertEquals("ab", repairEncoding("a\u0081b"))
    }

    @Test
    fun `alert texts`() {
        assertEquals(
            "via Agostino Depretis, piazza dell'Esquilino\nvia Liberiana",
            cleanAlertText("via Agostino Depretis, piazza dell&#039;Esquilino\r\nvia Liberiana"),
        )
        assertEquals("A & B <c> \"d\" 'e'", cleanAlertText("A &amp; B &lt;c&gt; &quot;d&quot; &apos;e&apos;"))
        assertEquals("é", cleanAlertText("&#xE9;"))
        assertEquals("&unknown; &#0;", cleanAlertText("&unknown; &#0;"))
        assertEquals("a\nb", cleanAlertText("a\rb"))
    }
}
