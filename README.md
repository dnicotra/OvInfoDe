# Abfahrten – live departures for Germany, the Netherlands, Belgium, Italy and Luxembourg

A simple Android app for public transport in Germany, the Netherlands, Belgium, Italy and Luxembourg, inspired by the Dutch [OVinfo](https://apps.apple.com/app/id1144468923) app: open it, see when your next bus, tram, metro, S-Bahn or train leaves, with real-time delays and cancellations. No journey planner, no account, no ads.

<p>
<img src="docs/screenshots/home_nearby.png" width="200" alt="Nearby stops with next departures">
<img src="docs/screenshots/departures.png" width="200" alt="Departure board of Köln Hauptbahnhof">
<img src="docs/screenshots/departures_by_line.png" width="200" alt="Departures grouped by line">
<img src="docs/screenshots/trip.png" width="200" alt="Trip with vehicle position">
<img src="docs/screenshots/departures_amsterdam.png" width="200" alt="Departure board of Amsterdam Centraal, in Dutch">
<img src="docs/screenshots/departures_roma.png" width="200" alt="Departure board of Roma Termini, in Italian">
<img src="docs/screenshots/departures_luxembourg.png" width="200" alt="Departure board of Luxembourg station, in French">
<img src="docs/screenshots/departures_liege.png" width="200" alt="Departure board of Liège-Guillemins, in French">
</p>

## Features

- **Nearby**: the closest stops with their next departures, based on your location.
- **Favourites**: star a stop; the home screen shows its next departures.
- **Departure board** per stop: countdown, platform/stop, real-time delay (green on time, red delayed), cancellations, platform changes and service alerts. Sort **by time** or **by line**, filter by mode (ICE/IC, regional, S-Bahn, U-Bahn, tram, bus…), load later departures. Refreshes every 30 s.
- **Trip view**: all stops of a trip with real-time times and where the vehicle is right now.
- **Stop search** across Germany, the Netherlands, Belgium, Italy and Luxembourg (and the rest of Europe covered by Transitous).
- **Local conventions per country**: German stop names shortened the DB way ("Köln Hbf"), Dutch trains as IC/SPR in NS colours, Italian names in the usual spelling instead of capitals ("ROMA TERMINI" → "Roma Termini"), Milan's metro as M1–M5 and Rome's as A/B/C, "U-Bahn" in Germany and "metro" elsewhere, GVB's bilingual alerts in one language, cross-border trains at Luxembourg station shown once under their CFL names, Belgian names in the language of the signs (Dutch in Flanders, French in Wallonia and Brussels) unless you read Dutch or French, SNCB trains as IC/L/P/S, TEC's "LIEGE Opéra" as "Liège, Opéra". See [Countries](#countries).
- German, Dutch, Italian, French and English, light and dark theme, Material You colours.

## Data

Timetables and real-time data come from **[Transitous](https://transitous.org)** (MOTIS 2 API, no key needed), which uses the nationwide DELFI timetable and real-time feeds for Germany, the national [OpenOV](https://gtfs.openov.nl) feeds (NS trains and every bus, tram and metro operator) for the Netherlands, for Italy Trenitalia's national timetable plus some forty regional and city feeds (Trenord, ATM Milan, ATAC/Roma Mobilità, GTT Turin, ANM Naples, TPER Bologna, ACTV Venice, South Tyrol…), many of them through [BusOne](https://busone.app), the national timetable of the [Administration des transports publics](https://data.public.lu/en/datasets/horaires-et-arrets-des-transport-publics-gtfs/) (CFL trains, Luxtram, AVL, RGTR and TICE buses) for Luxembourg, and for Belgium the open data of its four operators: SNCB/NMBS (trains), STIB/MIVB (Brussels), De Lijn (Flanders) and TEC (Wallonia), with real-time data except for STIB. See [docs/research.md](docs/research.md) for how the Dutch OVinfo/OVapi setup compares, why Transitous was chosen over the DB APIs and OVapi, and what the Italian, Luxembourg and Belgian data look like.

**Luxembourg has no real-time data in Transitous**: Luxembourg publishes it only through the mobiliteit.lu API, which needs a personal key. Boards there show timetable times (in neutral colours) and say so; only trains that a German, French or Belgian feed also carries can bring real-time data.

Transitous is free for **open-source, non-commercial** apps. The app follows its [usage policy](https://transitous.org/api/): it identifies itself with a User-Agent containing the repository URL, credits the [data sources](https://transitous.org/sources/) and OpenStreetMap, and keeps the request rate low.

## Install

Download `app-release.apk` (from a GitHub Actions run of this repository, or build it yourself), open it on your phone and allow installing from that source. Requires Android 8.0 or newer.

All builds are signed with the key in `keystore/shared.keystore`. It is **deliberately public**, so that every build (local, cloud, CI) can update the installed app without losing favourites. Replace it with a private key before publishing the app in a store.

## Build

Requirements: JDK 17+ and the Android SDK (platform 37.2 is downloaded automatically if your SDK licences are accepted).

```sh
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest        # unit tests on recorded API data, offline (~10 s)
./gradlew testDebugUnitTest -PliveApi --tests '*LiveApiTest'   # checks against the real API (~15 s)
./gradlew recordRoborazziDebug -Pscreenshots   # re-render docs/screenshots/ (slow, Robolectric)
```

## Code overview

```
app/src/main/java/io/github/dnicotra/ovinfode/
├── data/api/        Transitous client (OkHttp + kotlinx.serialization) and DTOs
├── data/            Mapping to app models, repository, favourites (DataStore), location
├── country/         Per-country conventions (Germany, Netherlands, Belgium, Italy, Luxembourg) and border outlines
├── util/            Countdown, line grouping, vehicle position, distances, stop names (German, Italian)
└── ui/              Jetpack Compose screens: home, search, departures, trip, about
```

## Countries

Transitous covers most of Europe in one format, so the app works everywhere it has data. What differs per country is how the timetable feeds name things and what locals call them. That lives in `country/`: one object per country that overrides what it needs from `Country`:

| Rule | Germany | Netherlands | Belgium | Italy | Luxembourg |
|---|---|---|---|---|---|
| Destinations without their town ("Hauptbahnhof") | looked up once, by district of the stop id | never needed: stops are "Town, Stop" | never needed: stations after their town, De Lijn's "Town Stop", TEC's "TOWN Stop", STIB's in Brussels | never needed: towns, stations named after them or streets of the same city | never needed: stops are "Town, Stop" ("Quarter, Stop" in the capital) |
| Line names | as in DELFI ("ICE 1697", "RE73") | "Intercity"/"Sprinter" → IC/SPR, rail replacement buses shortened, trains added in real time named from their trip id | SNCB trains by type ("IC 2003" → IC, L, P, EC; the number is in the trip view), S trains by line | Trenitalia regional trains by category (REG, RV; the number is in the trip view), long-distance with number ("FR 9503"); Milan metro "3" → M3, Rome "MEA" → A | as in the national timetable (RE, RB, IC, TER, T1, bus numbers) |
| Line colours | from the feed, else generic per mode | trains in NS yellow and blue | from the feeds (S trains, STIB, De Lijn, TEC) | Milan metro in the colours of the network map | generic per mode (the feed has none) |
| Mode names | U-Bahn, S-Bahn | metro; all domestic trains are "train" | metro; IC, L and P are "train" | metro | all CFL trains are "train" |
| Stop names | DB abbreviations (Hbf, Bf, str.) for all countries | "'s-Gravenhage" → "Den Haag" | in Dutch or French (below); TEC's towns in capitals → "Town, Stop" with accents ("LIEGE Gare des Guillemins" → "Liège, Gare des Guillemins"), De Lijn's "… perron 2" without the platform, STIB's capitals headsigns ("HOP. MILITAIRE") → the final stop's name | capitals and lower case → usual spelling ("P.ZA S. PIETRO" → "P.za S. Pietro", "stazione centrale m2 m3" → "Stazione Centrale M2 M3", "CITTA'" → "Città"), GTT and Cotral stop codes removed; also for Italian feeds' stops abroad | as in the national timetable |
| Duplicates | same line, destination and time shown once | a cancelled train and its replacement: the running one | same line, destination and time shown once | Trenitalia's outdated copies of a train ("p" versions) dropped | trains also in the German, French or Belgian feed shown once (below) |
| Trip numbers | as sent | as sent | trains only; De Lijn's and TEC's are internal | trains only (Trenord's "S3 - 823" → 823); buses' are internal ids | as sent |
| Operators | as sent | as sent | "LETEC" → TEC | Trenitalia's regional brands | as sent |
| Alerts | as sent | GVB's "Dutch -- English" texts split by language | as sent (De Lijn and TEC) | Roma Mobilità's "(@romamobilita)" signature removed | none (no real-time feed) |
| Real-time data | yes | yes | yes, except STIB | some feeds | no: the board says "timetable only" |

**Belgium's languages.** SNCB, STIB and TEC translate their names into Dutch and French properly, but asked for German or English, SNCB answers with both languages, abbreviated to fit its displays ("Brus.-/ Brux-Centr.", "Leuven / Louvain"). So Dutch and French readers get names in their language everywhere, and everyone else gets the names on the signs: boards, nearby stops and trips in Flanders are asked for in Dutch, those in Wallonia and Brussels in French (a trip in the language of the board it was opened from). The regions come from Natural Earth's Belgian provinces. Search needs no such rule: the geocoder answers with the name that matched. On boards outside Belgium (Aachen, Roosendaal) SNCB's trains still come in the reader's language, so in German "Luik-Guillemins / Liège-Guillemins".

For every country, text the feeds encoded wrongly is repaired (UTF-8 read as Latin-1, Windows quotes, HTML entities in alerts).

A stop's country comes from the country code in its id where there is one (German DHIDs like `de:08111:6115`, `nl:…`, `lu:…`, `it:22021:…` in South Tyrol) or from the id scheme of the country's own feed (Luxembourg's stop numbers start with a country digit: `000200405020` is in Luxembourg, `000500000079` in Germany), otherwise from simplified Natural Earth border outlines (`CountryOutlines.kt`, accurate to a kilometre or two at borders, which is not enough for Luxembourg's river towns like Vianden or Remich; where two outlines overlap, the country of the stop's feed wins), and for stops of a country's own feeds just off its outline (piers, islands the outline lacks such as Procida) from the feed. Line rules follow the country of the trip's feed, so a train looks the same on every board; name rules apply for the country a stop lies in and the country of its feed.

A train that crosses a border is in the timetable of every country it runs in, each version named the local way and often cut off at the border: at Luxembourg station, the RE to Trier is also "RE11" to Wasserbillig in the German feed, and the TER to Nancy only goes as far as Metz in the Luxembourg one. Departures of different feeds at the same time with the same train number (or its even/odd twin, as French trains change their number where they reverse) are shown once: with the times of the version that has real-time data, the destination of the one that goes furthest, and the line name of the stop's own country.

To add a country: create an object like `Netherlands` or `Italy`, list it in `Countries.all`, regenerate the outlines with `python3 tools/country_outlines.py NL DE IT LU BE BE-VLG BE-BRU XX > app/src/main/java/io/github/dnicotra/ovinfode/country/CountryOutlines.kt`, add a `values-xx/strings.xml` if it speaks a new language, and record a departure board as a test fixture.

Kotlin, Jetpack Compose (Material 3), Navigation Compose, no Google Play services (location uses the platform `LocationManager`).

## License

Copyright (C) 2026 OvInfoDe contributors

Abfahrten is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See [LICENSE](LICENSE) for the full text.

The timetable data the app shows, and the recorded API responses in `app/src/test/resources/fixtures/`, are not covered by this licence: they come from Transitous and its [data sources](https://transitous.org/sources/) under their own terms. The country and region outlines are derived from [Natural Earth](https://www.naturalearthdata.com) (public domain).
