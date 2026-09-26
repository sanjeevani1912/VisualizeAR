# Dhruv AR — Project Proposal

## 1. Introduction

This proposal defines a server–client application for combat layout planning and visualization. The system enables users to place assets on a geographically accurate map using GNSS, visualize the resulting plan in augmented reality on mobile devices, and share layouts with other users for review and approval on mobile or web clients.

## 2. Objectives

- Enable efficient creation of combat layouts anchored to real-world geography.
- Support AR visualization of layouts on physical surfaces at full scale and as miniature desk models.
- Allow layouts to be shared with selected users and approved from Android or web clients.
- Establish a backend service for authentication, layout storage, and share/approve workflows.

## 3. Features


| Feature                   | Description                                                                                                                |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| GNSS and map planning     | Plot objects on a 2D map using device location and map tiles                                                               |
| Region selection          | Select a geographic region within which assets are placed                                                                  |
| Asset placement           | Place and edit layout assets (for example tents, vehicles, antennas) in metric coordinates relative to a geographic anchor |
| AR visualization          | Render the shared plan on a detected horizontal surface using ARCore (2D footprint on the ground plane)                    |
| Miniature sand-table mode | Scale the same layout for desk-top briefing visualization                                                                  |
| Layout sharing            | Share a layout with a selected user                                                                                        |
| Cross-client approval     | Approve or reject shared layouts from Android or web                                                                       |
| Static authentication     | Controlled access via predefined accounts                                                                                  |


### Future considerations (out of scope for this proposal)

- Synchronized multi-user live viewing of the same AR session

## 4. User flow

1. User authenticates with a static account.
2. User creates a new layout:
  1. Views the map on device using a PMTiles source and MapLibre.
  2. Selects a region.
  3. Places assets within the selected region.
  4. Optionally visualizes the plan with ARCore at full scale on real ground, or as a miniature model on a desk.
3. User shares the layout with a selected recipient.
4. Recipient reviews and approves or rejects the layout on mobile or web.

```text
Login
  → Create layout (MapLibre / PMTiles → select region → place assets → optional AR)
  → Share with selected user
  → Approve or reject (Android or web)
```

## 5. System architecture

```text
┌──────────────────────┐       ┌──────────────────────┐
│     Android client   │       │     Web client       │
│  Map, GNSS, planning │       │  Layout view, admin  │
│  AR, share, approve  │       │  share / approve     │
└──────────┬───────────┘       └──────────┬───────────┘
           │                              │
           └──────────────┬───────────────┘
                          ▼
                   Backend API
              (framework TBD by team)
                          │
              ┌───────────┴───────────┐
              ▼                       ▼
        Layout / user store      PMTiles map service
```


| Component   | Responsibility                                                                                              |
| ----------- | ----------------------------------------------------------------------------------------------------------- |
| Android     | Primary authoring client: map-based planning, GNSS, AR (full-scale and miniature), share, and approve       |
| Web (React) | Admin dashboard and user-facing layout viewing and approval (no AR)                                         |
| Backend     | Static authentication, layout CRUD, share and approve state transitions; optional map tile serving or proxy |
| Map tiles   | PMTiles delivery for MapLibre on Android and web                                                            |


**Canonical data model.** A versioned layout document is the source of truth across clients. AR is a visualization layer only; session AR anchors are not persisted as part of the shared layout.

## 6. Domain model notes

Layouts requires geographic context:

- Map region / bounds for the layout
- Geographic anchor (for example latitude, longitude, and heading) so map, GNSS, and AR remain consistent
- Object positions remain in meters relative to that anchor
- Sharing metadata: owner, recipients, status (`draft`, `shared`, `approved`, `rejected`), and timestamps

The layout schema must remain versioned to support migration from the existing local JSON format.

## 7. Technology stack


| Layer   | Choice                                                                              |
| ------- | ----------------------------------------------------------------------------------- |
| Android | Kotlin, Jetpack Compose, ARCore, Camera, GNSS, MapLibre, PMTiles                    |
| Web     | React (admin dashboard and layout viewing / approval)                               |
| Backend | To be decided by the engineering team (for example FastAPI, Django, or Spring Boot) |
| Maps    | PMTiles consumed by MapLibre                                                        |


Backend implementation should satisfy a stack-agnostic API contract:

- Authentication for static accounts (token or session)
- Layout create, read, update, and list (owned and shared-with-me)
- Share to user(s); approve and reject transitions
- Versioned layout JSON compatible with the Android serializer

Map tile hosting may be co-located with the API or provided by a dedicated tile service. That deployment choice is independent of the layout API framework.

## 8. Client responsibilities

### Android

- MapLibre map display, region selection, and asset placement
- GNSS for location context during planning
- ARCore visualization at full scale and in miniature desk mode
- Login, layout sync or upload, share, and in-app approve
- Local persistence where appropriate, with server sync when available

### Web

- Login against the same static account model
- List and open shared layouts on a MapLibre map
- Approve or reject; admin views as required
- No AR requirement

