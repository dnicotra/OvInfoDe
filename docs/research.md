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
