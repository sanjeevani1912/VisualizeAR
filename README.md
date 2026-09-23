# Suraksha AR Planner

Offline Android planning and AR visualization prototype.

Create a 2D metric field layout, save it locally, then project the same plan onto a real horizontal surface with ARCore.

This is a **planning / visualization prototype**. It is not a surveying system and does not claim survey-grade or military-grade accuracy.

## Technology stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| Architecture | Lightweight MVVM (no DI framework) |
| Persistence | Local JSON files |
| AR | Google ARCore + custom OpenGL ES 2.0 |
| Min / target SDK | 24 / 36 |

## Architecture

```
MainActivity
  └─ AppNavigation (shared PlanningViewModel)
       ├─ HomeScreen
       ├─ PlanningScreen          (2D editor)
       └─ ARVisualizationScreen   (ARCore + GLES)
```

| Package | Role |
|---------|------|
| `domain/model` | `Layout`, `LayoutObject`, `Anchor`, `AssetType` |
| `domain/spatial` | 2D canvas math, AR pose mapping, distances |
| `data/` | Offline JSON repository + serializer |
| `viewmodel/` | Planning UI + persistence state |
| `ui/screens` | Home, Planning, AR |
| `ui/components` | Canvas, asset bar, inspector cards |
| `ui/ar/render` | Session renderers + procedural meshes |
| `ui/ar/capture` | PixelCopy / GL capture + MediaStore |
| `navigation/` | Compose NavHost routes |

**Source of truth:** the saved `Layout`. AR is a visualization layer only.

## How to build

1. Open the project in Android Studio (Ladybug / recent AGP).
2. Sync Gradle.
3. Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

Or use **Build → Make Project** in Android Studio.

## How to run

1. Connect an ARCore-capable Android device (or emulator with ARCore support).
2. Run the `app` configuration.
3. Grant camera permission when prompted for AR.

Recommended flow:

Home → Create New Plan → place assets → Save → Visualize in AR → detect ground → Set Planning Origin → Capture

## AR requirements

- Device must support Google ARCore (optional install; 2D planning still works without AR).
- Camera permission required for AR Visualization.
- Horizontal plane detection (floors / ground).
- Good lighting and textured surfaces improve tracking.

## Asset system

`AssetType` → physical defaults (`AssetSpecification`) → procedural mesh (`AssetModelRegistry`).

Supported types: **Tent**, **Truck**, **Car**, **Antenna**, **Trench**.

Meshes are metric OpenGL placeholders with ground pivot at Y = 0. Real GLB/GLTF assets can replace registry entries later without changing planning data.

## Planning coordinate system

- Horizontal plane: **X** (lateral) and **Z** (depth).
- Origin: layout `Anchor` at **(0, 0)**.
- Units: **meters**.
- Rotation: degrees around the vertical axis.

## AR coordinate mapping

After the user sets the planning origin on a detected plane:

| Planning | AR (relative to planning origin) |
|----------|----------------------------------|
| +X | +X (lateral) |
| +Z | +Z (depth on ground) |
| height | +Y up from the plane |
| scale | **1 planning meter = 1 real meter** |

Session ARCore anchors are **not** written into saved layouts. Re-open AR → calibrate again.

## Persistence

- Layouts: app files dir `dhruvar_layouts/<id>.json` (atomic temp-file writes).
- Captures: MediaStore `Pictures/SurakshaAR/` with timestamped filenames.
- No network APIs; no `INTERNET` permission.

## Project structure

```
app/src/main/java/com/example/dhruvar/
├── MainActivity.kt
├── data/
│   ├── model/LayoutJsonSerializer.kt
│   └── repository/
├── domain/
│   ├── model/
│   └── spatial/
├── navigation/
├── ui/
│   ├── ar/
│   │   ├── capture/
│   │   └── render/
│   ├── components/
│   ├── screens/
│   └── theme/
└── viewmodel/
```

## Known limitations

- AR placement accuracy depends on device tracking and surface detection.
- Physical dimensions use asset defaults unless customized in the planner.
- 3D assets are procedural placeholders (not final production GLB models).
- AR visualization is approximate — not a surveying or measurement instrument.
- Tracking can degrade in low light or low-feature environments.
- Package id remains `com.example.dhruvar` (legacy module name).
- No cloud sync, sharing, or multi-user collaboration.

## License / status

Internal planning prototype. Parts 1–14 complete: 2D planning, persistence, AR calibration, visualization, capture, and UI polish.
