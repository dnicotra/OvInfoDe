# Abfahrten – live departures for Germany, the Netherlands and Italy

A simple Android app for public transport in Germany, the Netherlands and Italy, inspired by the Dutch [OVinfo](https://apps.apple.com/app/id1144468923) app: open it, see when your next bus, tram, metro, S-Bahn or train leaves, with real-time delays and cancellations. No journey planner, no account, no ads.

<p>
<img src="docs/screenshots/home_nearby.png" width="200" alt="Nearby stops with next departures">
<img src="docs/screenshots/departures.png" width="200" alt="Departure board of Köln Hauptbahnhof">
<img src="docs/screenshots/departures_by_line.png" width="200" alt="Departures grouped by line">
<img src="docs/screenshots/trip.png" width="200" alt="Trip with vehicle position">
<img src="docs/screenshots/departures_amsterdam.png" width="200" alt="Departure board of Amsterdam Centraal, in Dutch">
<img src="docs/screenshots/departures_roma.png" width="200" alt="Departure board of Roma Termini, in Italian">
</p>

## Features

- **Nearby**: the closest stops with their next departures, based on your location.
- **Favourites**: star a stop; the home screen shows its next departures.
- **Departure board** per stop: countdown, platform/stop, real-time delay (green on time, red delayed), cancellations, platform changes and service alerts. Sort **by time** or **by line**, filter by mode (ICE/IC, regional, S-Bahn, U-Bahn, tram, bus…), load later departures. Refreshes every 30 s.
- **Trip view**: all stops of a trip with real-time times and where the vehicle is right now.
- **Stop search** across Germany, the Netherlands and Italy (and the rest of Europe covered by Transitous).
- **Local conventions per country**: German stop names shortened the DB way ("Köln Hbf"), Dutch trains as IC/SPR in NS colours, Italian names in the usual spelling instead of capitals ("ROMA TERMINI" → "Roma Termini"), Milan's metro as M1–M5 and Rome's as A/B/C, "U-Bahn" in Germany and "metro" elsewhere, GVB's bilingual alerts in one language. See [Countries](#countries).
- German, Dutch, Italian and English, light and dark theme, Material You colours.

## Data

Timetables and real-time data come from **[Transitous](https://transitous.org)** (MOTIS 2 API, no key needed), which uses the nationwide DELFI timetable and real-time feeds for Germany, the national [OpenOV](https://gtfs.openov.nl) feeds (NS trains and every bus, tram and metro operator) for the Netherlands, and for Italy Trenitalia's national timetable plus some forty regional and city feeds (Trenord, ATM Milan, ATAC/Roma Mobilità, GTT Turin, ANM Naples, TPER Bologna, ACTV Venice, South Tyrol…), many of them through [BusOne](https://busone.app). See [docs/research.md](docs/research.md) for how the Dutch OVinfo/OVapi setup compares, why Transitous was chosen over the DB APIs and OVapi, and what the Italian data looks like.

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
├── country/         Per-country conventions (Germany, Netherlands, Italy) and border outlines
├── util/            Countdown, line grouping, vehicle position, distances, stop names (German, Italian)
└── ui/              Jetpack Compose screens: home, search, departures, trip, about
```

## Countries

Transitous covers most of Europe in one format, so the app works everywhere it has data. What differs per country is how the timetable feeds name things and what locals call them. That lives in `country/`: one object per country that overrides what it needs from `Country`:

| Rule | Germany | Netherlands | Italy |
|---|---|---|---|
| Destinations without their town ("Hauptbahnhof") | looked up once, by district of the stop id | never needed: stops are "Town, Stop" | never needed: towns, stations named after them or streets of the same city |
| Line names | as in DELFI ("ICE 1697", "RE73") | "Intercity"/"Sprinter" → IC/SPR, rail replacement buses shortened, trains added in real time named from their trip id | Trenitalia regional trains by category (REG, RV; the number is in the trip view), long-distance with number ("FR 9503"); Milan metro "3" → M3, Rome "MEA" → A |
| Line colours | from the feed, else generic per mode | trains in NS yellow and blue | Milan metro in the colours of the network map |
| Mode names | U-Bahn, S-Bahn | metro; all domestic trains are "train" | metro |
| Stop names | DB abbreviations (Hbf, Bf, str.) for all countries | "'s-Gravenhage" → "Den Haag" | capitals and lower case → usual spelling ("P.ZA S. PIETRO" → "P.za S. Pietro", "stazione centrale m2 m3" → "Stazione Centrale M2 M3", "CITTA'" → "Città"), GTT and Cotral stop codes removed; also for Italian feeds' stops abroad |
| Duplicates | same line, destination and time shown once | a cancelled train and its replacement: the running one | Trenitalia's outdated copies of a train ("p" versions) dropped |
| Trip numbers | as sent | as sent | trains only (Trenord's "S3 - 823" → 823); buses' are internal ids |
| Alerts | as sent | GVB's "Dutch -- English" texts split by language | Roma Mobilità's "(@romamobilita)" signature removed |

For every country, text the feeds encoded wrongly is repaired (UTF-8 read as Latin-1, Windows quotes, HTML entities in alerts).

A stop's country comes from the country code in its id where there is one (German DHIDs like `de:08111:6115`, `nl:…`, `it:22021:…` in South Tyrol), otherwise from simplified Natural Earth border outlines (`CountryOutlines.kt`, accurate to a kilometre or two at borders), and for stops of a country's own feeds just off its outline (piers, islands the outline lacks such as Procida) from the feed. Line rules follow the country of the trip's feed, so a train looks the same on every board; name rules apply for the country a stop lies in and the country of its feed.

To add a country: create an object like `Netherlands` or `Italy`, list it in `Countries.all`, regenerate the outlines with `python3 tools/country_outlines.py NL DE IT XX > app/src/main/java/io/github/dnicotra/ovinfode/country/CountryOutlines.kt`, add a `values-xx/strings.xml` if it speaks a new language, and record a departure board as a test fixture.

Kotlin, Jetpack Compose (Material 3), Navigation Compose, no Google Play services (location uses the platform `LocationManager`).

## License

Copyright (C) 2026 OvInfoDe contributors

Abfahrten is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See [LICENSE](LICENSE) for the full text.

The timetable data the app shows, and the recorded API responses in `app/src/test/resources/fixtures/`, are not covered by this licence: they come from Transitous and its [data sources](https://transitous.org/sources/) under their own terms. The country outlines are derived from [Natural Earth](https://www.naturalearthdata.com) (public domain).
