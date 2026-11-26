package io.github.dnicotra.ovinfode.util

import io.github.dnicotra.ovinfode.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StopNamesTest {

    @Test
    fun `standard abbreviations`() {
        assertEquals("Köln Hbf", shortStopName("Köln Hauptbahnhof"))
        assertEquals("Frankfurt (Main) Hbf", shortStopName("Frankfurt (Main) Hauptbahnhof"))
        assertEquals("S+U Berlin Hbf", shortStopName("S+U Berlin Hauptbahnhof"))
        assertEquals("Hbf", shortStopName("Hauptbahnhof"))
        assertEquals("Leipzig Hbf (tief)", shortStopName("Leipzig Hauptbahnhof (tief)"))
        assertEquals("Fulda Bf", shortStopName("Fulda Bahnhof"))
        assertEquals("Frankfurt(M) Flughafen Fernbf", shortStopName("Frankfurt(M) Flughafen Fernbahnhof"))
        assertEquals("Flughafen Regionalbf", shortStopName("Flughafen Regionalbahnhof"))
        assertEquals("Ulm ZOB", shortStopName("Ulm Zentraler Omnibusbahnhof"))
        assertEquals("St. Augustin Markt", shortStopName("Sankt Augustin Markt"))
    }

    @Test
    fun `streets are shortened, also inside compounds`() {
        assertEquals("S+U Hermannstr. (Berlin)", shortStopName("S+U Hermannstraße (Berlin)"))
        assertEquals("Karl-Marx-Str.", shortStopName("Karl-Marx-Straße"))
        assertEquals("Köln, Kölner Str.", shortStopName("Köln, Kölner Straße"))
        assertEquals("Bahnhofstr.", shortStopName("Bahnhofstraße"))
        assertEquals("Zürich, Badenerstr.", shortStopName("Zürich, Badenerstrasse"))
        assertEquals("Straßenbahndepot", shortStopName("Straßenbahndepot"))
    }

    @Test
    fun `town comma before Hbf or Bf is dropped`() {
        assertEquals("Mannheim Hbf", shortStopName("Mannheim, Hauptbahnhof"))
        assertEquals("Osterburken Bf", shortStopName("Osterburken, Bahnhof"))
        assertEquals("Wiesloch-Walldorf Bf", shortStopName("Wiesloch-Walldorf, Bf"))
        assertEquals("Stuttgart Hbf", shortStopName("Stuttgart, Hauptbahnhof"))
        // Only when Hbf/Bf is all that follows the town.
        assertEquals("Bahnstadt, Hbf Süd", shortStopName("Bahnstadt, Hauptbahnhof Süd"))
    }

    @Test
    fun `whole words only`() {
        assertEquals("Bahnhofsplatz", shortStopName("Bahnhofsplatz"))
        assertEquals("Hauptbahnhofstr.", shortStopName("Hauptbahnhofstraße"))
        assertEquals("S-Bahnhof Pankow", shortStopName("S-Bahnhof Pankow"))
        assertEquals("Köln Dom/Hbf", shortStopName("Köln Dom/Hbf"))
        assertEquals("Alexanderplatz", shortStopName("Alexanderplatz"))
        assertEquals("Ostbahnhof", shortStopName("Ostbahnhof"))
    }

    @Test
    fun `every recorded stop name is shortened safely`() {
        val names = listOf(
            "stoptimes_berlin_alexanderplatz.json", "stoptimes_koeln_hbf.json", "stoptimes_heidelberg_hbf.json",
            "trip_s3.json", "trip_ice1697.json", "geocode_alexanderplatz.json", "mapstops_koeln.json",
        ).flatMap { file -> Regex(""""(?:name|headsign)": "([^"]+)"""").findAll(Fixtures.text(file)).map { it.groupValues[1] } }
            .toSortedSet()
        val glued = Regex("""\p{L}(?:Hbf|Bf)|(?:Hbf|Bf)\p{L}""")
        for (name in names) {
            val short = shortStopName(name)
            if (short != name) println("$name → $short")
            assertEquals("not idempotent: $name", short, shortStopName(short))
            assertFalse("glued abbreviation in '$short'", glued.containsMatchIn(short))
        }
    }
}
