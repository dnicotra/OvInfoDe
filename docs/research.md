# Research: from OVinfo (NL) to a German departures app

*November 2026*

## 1. The Dutch OVinfo app

[OVinfo](https://apps.apple.com/app/id1144468923) (Android package `nl.skywave.ovinfo`, by OVapi B.V., first released in 2011, ~800k installs) is a deliberately simple app:

- **Live departures per stop or station** for bus, tram, metro and train, with the minutes until departure.
- **Nearby stops** based on the phone's location.
- **Delays, cancellations and vehicle positions** (how far the vehicle is along its route).
- **Line view**: all stops of a line and where the vehicles are; departures grouped per line at bus/tram/metro stops.
- **Disruption overview**, **bookmarks/favourites** (added in 2.0), quick search by stop, line or operator.
- Accessibility (VoiceOver, dynamic font sizes), dark mode.
- Free with ads; a one-off in-app purchase removes them.

There is no journey planner: it only answers "when does my next bus/train leave?".

## 2. The API behind it: OVapi / KV78Turbo

OVinfo is the official client of [OVapi](https://ovapi.nl), a free REST API at `http://v0.ovapi.nl/` ([docs](https://github.com/koch-t/KV78Turbo-OVAPI/wiki)):

| Endpoint | Returns |
|---|---|
| `/tpc/{timingPointCode}[/departures]` | Passes (departures) at a stop: `TargetDepartureTime`, `ExpectedDepartureTime`, `LinePublicNumber`, `DestinationName50`, `TransportType`, `TripStopStatus` (PLANNED/DRIVING/ARRIVED/PASSED/CANCEL) |
| `/stopareacode/{code}[/departures]` | The same for a group of stops (a station area) |
| `/line/{owner_line_direction}` | Stops of a line plus the vehicles currently on it (`Actuals`) |
| `/journey/{key}` | All stops of a single trip |

The data comes from the Dutch national real-time feed (KV7 timetable + KV8 real-time "passtimes", via NDOV). No API key is needed; batch requests, gzip and a descriptive User-Agent are expected.

## 3. Options for Germany

Germany has no exact OVapi equivalent, but the national DELFI data (all operators' timetables as GTFS plus a nationwide GTFS-Realtime feed) is the counterpart of NDOV. Ways to use it from an app:

| Option | Coverage | Key needed | Status (Nov 2026) |
|---|---|---|---|
| **[Transitous](https://transitous.org/api/)** (MOTIS 2 API, community-run) | All of Germany (DELFI + real-time), plus most of Europe | No | **Works.** Free for open-source, non-commercial apps; needs User-Agent with contact and attribution |
| DB HAFAS (`reiseauskunft.bahn.de/bin/mgate.exe`) | Germany | No | **Shut down** permanently in 2025 |
| DB Navigator / bahn.de APIs via [db-vendo-client](https://github.com/public-transport/db-vendo-client) / `v6.db.transport.rest` | Germany | No | **Unreliable:** unofficial, DB blocks clients (TLS fingerprinting, `OPS_BLOCKED`); the project itself now recommends Transitous |
| DB API Marketplace "Timetables" (IRIS) | Trains only, per station | Yes (registration) | Official, but no buses/trams/U-Bahn |
| Regional APIs (VBB/BVG HAFAS, RMV, EFA of VRR/MVV/VVS…) | One region each | Sometimes | Work, but every region is different |
| Raw DELFI GTFS + GTFS-RT | Germany | Registration | Needs your own server to process |

**Choice: Transitous.** It is the closest match to OVapi: one keyless API for the whole country and all modes, with real-time delays, cancellations, platforms and line colours. During testing it returned live data for Berlin (S-Bahn, U-Bahn), Cologne (ICE/IC, S-Bahn, KVB trams and buses, including cancellations) and rural Brandenburg buses.

### Endpoints used

| Feature | Transitous endpoint | OVapi counterpart |
|---|---|---|
| Stop search | `GET /api/v1/geocode?type=STOP&text=…` | `/stopareacode/` list |
| Nearby stops | `GET /api/v6/map/stops?min=…&max=…&grouped=true` | — |
| Departure board | `GET /api/v6/stoptimes?stopId=…&center=…&radius=30&n=30` | `/tpc/…/departures` |
| Trip with all stops | `GET /api/v6/trip?tripId=…` | `/journey/…` |

`stoptimes` gets the stop's coordinates as a fallback (`center` + `radius`), because stop ids can change when the timetable feeds are updated; this keeps saved favourites working.

### Usage policy obligations (https://transitous.org/api/)

- The source code must be published under an open-source licence (prototype testing before that is fine).
- No commercial use.
- Every request sends a `User-Agent` with app name, version and a contact (the app sends `Abfahrten/<version> (Android; +<repository URL>)`).
- Link to https://transitous.org/sources/ visibly and follow OpenStreetMap attribution (done in the footer of each list and in the About screen).
- Keep the load light: the app refreshes an open departure board every 30 s and the home screen every 90 s, only while visible, and never uses the expensive `/plan` endpoints.

## 4. The Netherlands

*Added November 2026, when the app learned to work in the Netherlands.*

Transitous also covers the Netherlands, from the national **[OpenOV](https://gtfs.openov.nl)** feeds: the timetable of every operator as GTFS (CC0) plus GTFS-Realtime feeds for trains, for all other vehicles and for service alerts. It is the same national data that OVapi (the API behind OVinfo) is built from. So the app uses Transitous for the Netherlands too, rather than a second client for OVapi: one API, one data model, one attribution.

The API calls are the same as for Germany. What differs is how the data looks. These are recorded responses from 2026-11-26 (Amsterdam Centraal, Utrecht Centraal, Amsterdam Leidseplein, an NS Intercity and a train added in real time):

| Aspect | Dutch data | What the app does |
|---|---|---|
| Stop ids | `nl-OpenOV_3980180`, `nl-OpenOV_NL:S:30009577`; big stations also come with ids of other feeds: `de-DELFI_000008400058` (Amsterdam Centraal), `fr-eurostar-…_rotterdam_centraal_station_area`, Flixbus | the country is taken from a country code in the id, else from border outlines; the feed prefix alone is wrong for these stations |
| Stop names | "Amsterdam, Leidseplein" for bus, tram and metro (as in OVapi), "Utrecht Centraal" for stations, "Rotterdam, Rotterdam Centraal", "'s-Gravenhage, Centraal Station" | kept, except the repeated town and the official name of The Hague ("Den Haag") |
| Destinations | always with their town (or a station named after it) | no town lookups. Before, every destination like "Maastricht" cost a reverse-geocoding request, delaying a new board by up to 2.5 s |
| Lines | NS: route name "Intercity" / "Sprinter", train number in `tripShortName`; "ICD 1835", "ECD 9528"; Arriva/Blauwnet line numbers (RS1, RE18, IC23); "Snelbus ipv trein" for rail replacement buses | IC / SPR (as on the platforms), "Snelbus"; trains without a route colour in NS yellow with NS blue |
| Trains added in real time | mode `OTHER`, every name "?", trip id `…:IFF:IC:303531` | named and classified from the train type in the id. When NS replaces a train by cancelling it and adding one at the same time, only the running one is shown |
| Modes | all domestic NS trains, Intercity included, are `REGIONAL_RAIL`; ICD/ECD/ICE are `LONG_DISTANCE` | the filter says "Train" instead of "Regional"; metro instead of U-Bahn |
| Platforms | "5a", "11a" for trains; letters and bays ("B2", "K") for bus and tram | shown as "Spoor 5a" / "Perron B2" in Dutch |
| Alerts | GVB sends both languages in one text: "Tram 5 stopt hier niet. … -- Stop not served by tram 5. …", and cuts the header off after 140 characters | the Dutch half for Dutch readers, the English one for everyone else; a cut-off header is replaced by the full description |
| Real-time | NS trains nearly always; city buses and trams partly | as in Germany: green/red only with real-time data |

The country-specific rules live in `app/src/main/java/io/github/dnicotra/ovinfode/country/` (see the README for how to add another country).

## 5. Italy

*Added November 2026, when the app learned to work in Italy.*

Italy has no national real-time feed like DELFI or OpenOV, and no OVinfo-like API. Transitous covers it with Trenitalia's national timetable plus about forty regional and city feeds ([feed list](https://github.com/public-transport/transitous/blob/main/feeds/it.json)): Trenord, ATM (Milan), ATAC/Roma Mobilità (Rome), Cotral (Lazio), GTT (Turin), ANM and EAV (Naples), TPER (Bologna), ACTV (Venice buses and boats), Autolinee Toscane, AMT (Genoa), STA (South Tyrol), Trentino Trasporti, Sicily, Sardinia and more. Many come through [BusOne](https://busone.app), which processes them and offers real-time data (GTFS-RT, and SIRI for Trenitalia) for non-commercial use, which this app is. So the app uses Transitous for Italy too, with the same API calls.

**Gaps.** Italo (NTV), the second high-speed operator, has no open feed and is missing, so Italo trains do not show at Roma Termini or Milano Centrale. Trenitalia's data has no platforms. Real-time data for Trenitalia could not be checked (the data was recorded at night); GTT buses, Rome and FlixBus had real-time data, most city feeds have none.

What the data looked like on 2026-11-27 (Roma Termini, Milano Duomo and Cadorna, Torino Porta Nuova, Rome buses, a Frecciarossa), and what the app does:

| Aspect | Italian data | What the app does |
|---|---|---|
| Stop ids | `it-trenitalia_08409:station`, `it-Lombardia-ATM_DUOMO M3`, `it-Lazio-Rome_AD12`; only South Tyrol (STA) and DELFI use ids with a country code (`it:22021:2143:0:5235`); Bologna Centrale comes from the Austrian railway data (`at-Railway-…_Pit:22015:116`) | the country comes from the outline of Italy; stops of Italian feeds up to 6 km off it count as Italian (Procida and Capri are missing from the Natural Earth outline). San Marino and the Vatican lie inside it |
| Stop names | in capitals: "ROMA TERMINI", "REGGIO DI CALABRIA CENTRALE", "P.ZA STAZIONE S. PIETRO (FL)", "CITTA' GIARDINO"; ATM all in lower case: "stazione centrale m2 m3", "p.le cim.monumentale via bramante"; codes: GTT "Fermata 252 - PORTA NUOVA", "Fermata 3456 - STATI UNITI CAP" (capolinea), Cotral "ROMA \| Termini FS (Giolitti) # f14637", STA "Roma, Stazione di Roma Termini" | the usual spelling: words capitalised, prepositions in lower case, abbreviations (FS, M3, (MA), XX) and shortened words (P.za, V.le, Staz.ne) kept, "CITTA'" → "Città"; codes removed. Names written with care (Trenord, STA) keep their spelling. Also for stops of Italian feeds abroad (Trenitalia's "ZURIGO HB") |
| Broken text | ATM: "bicocca (universitÃ )" (UTF-8 read as Latin-1); Rome alerts: Windows-1252 quotes as control characters, `&#039;`, `\r\n`, a "(@romamobilita)" signature | repaired for every country; the signature is removed |
| Destinations | towns ("Fiumicino Aeroporto", "Cassino") or places in the same city | no town lookups |
| Lines | Trenitalia: "FR 9490", "REG 12655", "RV 4594" with colours (Frecciarossa red, regional greens); ATM metro "1"–"5" in pure ff0000/73ff01/fcff01 with black text; Rome metro "MEA", "MEB1"; Trenord "S3", "RE80" | regional trains by category ("REG", "RV"; five-digit numbers do not fit a line badge), long-distance trains with their number; M1–M5 in the network map colours; A, B, B1, C |
| Trip numbers | Trenitalia train numbers; Trenord "S3 - 823", "RE_80 - 25510"; buses: internal ids (ATAC "27333574") | train numbers only, without the line |
| Duplicates | Trenitalia has many trains twice, as versions "p" and "v" in the trip id: REG 4594 and RV 4594 at the same time, FR 9308 at 08:20 and 08:25; the "p" REG 4594 runs Roma–Fiumicino back and forth all day. Some trains are also twice as "v" (IC 503 to Savona at 04:40 and 06:05) | the "p" copy is dropped when the "v" one is on the board; double "v" trains are left, as nothing says which one runs |
| Modes | FR/FA/FB/IC/EC/ICN as high-speed or long-distance, REG/RV regional, SFM/MET suburban; Trenord all regional | "Lunga percorrenza", "Regionale", "Metro" in Italian |
