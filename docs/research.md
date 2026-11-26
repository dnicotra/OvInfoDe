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

## 5. Luxembourg

*Added November 2026, when the app learned to work in Luxembourg.*

Public transport in Luxembourg is free and run as one network: CFL trains, the Luxtram, the buses of the City of Luxembourg (AVL), the regional RGTR buses and TICE in the south. The Administration des transports publics (ATP) publishes the national timetable as GTFS on [data.public.lu](https://data.public.lu/en/datasets/horaires-et-arrets-des-transport-publics-gtfs/), and Transitous uses it (feed `lu-administration-des-transports-publics`), so the app needs no new API.

**No real-time data.** Transitous lists no real-time source for Luxembourg. The ATP offers real-time departures only through the mobiliteit.lu (HAFAS) API, which needs a personal key requested by e-mail ([API mobiliteit.lu](https://data.public.lu/en/datasets/api-mobiliteit-lu)); a key cannot be shipped in an open-source app, and every user requesting their own is not an option. A public GTFS-RT feed was announced years ago but is not available. So the app shows timetable times in Luxembourg, in the neutral colour it uses for any departure without real-time data, and the board adds "timetable only, no real-time data here". Trains that a neighbouring country's feed also carries (DELFI for trains to Trier, SNCF for France, SNCB for Belgium) can still bring real-time data from there.

What the data looks like (recorded responses from 2026-11-26/27: Luxembourg station, the tram and bus stop in front of it, an RE to Trier):

| Aspect | Luxembourg data | What the app does |
|---|---|---|
| Stop ids | `lu-administration-des-transports-publics_000200405020`: HAFAS station numbers whose first digit after `000` is the country (1, 2, 9 Luxembourg; 3 Belgium; 4 France; 5 Germany). DELFI has Luxembourg stations as `lu:25000:…`; the French and Belgian feeds have their own ids for Luxembourg station | the country comes from the id. The Natural Earth outlines put 165 of the 2,521 Luxembourg stops outside Luxembourg, 147 of them in Germany (Vianden, Echternach, Remich, Wasserbillig lie right on the Our, Sûre and Moselle) |
| Stop names | "Esch-sur-Alzette, Gare", "Luxembourg, Gare Centrale"; in the capital "Quarter, Stop" ("Bonnevoie, Rotonde", "Kirchberg, Luxexpo"); Luxembourgish names ("Cents, Waassertuerm"); some with the mode ("Hamilius (Bus)", "Hamilius-Centre (Tram)") | kept as they are, as on mobiliteit.lu; no town lookups needed |
| Lines | trains "RE", "RB", "IC", "TER", "TGV" with the train number in `tripShortName` ("05111"), tram "T1", bus numbers (AVL 1–40, RGTR 100–999). No route colours | kept; all trains are `REGIONAL_RAIL` (the TGV too), so the filter says "Train" |
| Platforms | none | none shown |
| Cross-border trains | the same train comes from up to three feeds, named differently and cut off at their borders: RE 5111 is "RE" to Trier in Luxembourg's feed and "RE11" to Wasserbillig in DELFI; the TER to Nancy is 88520 in the SNCF feed and 88521 to Metz-Ville in Luxembourg's (French trains change number by one where they reverse); TGV 2864 to Paris Est is "TGV" 02865 to Metz | shown once (see the README), with the times of a version with real-time data, the furthest destination and the Luxembourg name. Before, 7 of the 40 departures at Luxembourg station on a weekday morning were duplicates |

Luxembourg speaks Luxembourgish, French and German; the app got a French translation, which also serves cross-border commuters from France and Belgium.
