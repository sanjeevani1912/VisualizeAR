# Baseline — 2026-11-06 (phase P0)

Recorded on `main` at tag `demo-safe` (commit `00186aa`, built on `8879061`).

| Check | Command | Result |
|---|---|---|
| Android debug APK | `gradlew assembleDebug` | Pass (task was up to date, so no source changed since the last successful build) |
| Android unit tests | `gradlew testDebugUnitTest` | Pass (BUILD SUCCESSFUL, 1m 53s; test sources were recompiled and run) |
| Server tests | `npm test` in `server/` | Pass, 8 of 8 |
| Server typecheck | `npm run typecheck` | Pass, no errors |
| Web build | `npm run build` in `web/` | Pass; warning: main JS chunk is about 1 MB (295 KB gzip) |
| Django tests | `.venv/Scripts/python manage.py test` in `DhruvAR-django/server-django` | Pass, 14 of 14 |

Not run in P0: `test:api` end-to-end tests (needs the server running), a device install, the foss flavor
(this `main` has no flavors; they exist only in the friend's branch).

Notes
- JDK used for Gradle: Android Studio's bundled JBR (`C:\Program Files\Android\Android Studio\jbr`); `java` is not on PATH.
- Commits made in P0: `54cadd2` (Django scaffold, branch `django-backend`), `00186aa` (docs, branch `main`). Nothing pushed.
