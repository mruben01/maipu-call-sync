# Maipú Call Sync

Proyecto Android nativo de prueba de concepto para detectar y reproducir
grabaciones `.m4a` de Samsung Phone a través de `MediaStore`.

## Run & Operate

- `pnpm --filter @workspace/api-server run dev` — run the API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)
- Required env: `DATABASE_URL` — Postgres connection string
- Android app: open `android-app/` in Android Studio. This environment does
  not include Java, Gradle, Android SDK or ADB, so APK compilation must happen
  in Android Studio or another Android-capable machine.

## Stack

- pnpm workspaces, Node.js 24, TypeScript 5.9
- API: Express 5
- DB: PostgreSQL + Drizzle ORM
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)

## Where things live

- `android-app/` — Kotlin/Compose Android POC, including MediaStore query,
  permission handling, filename parser, playback and diagnostics.
- `android-app/README.md` — build, install and Galaxy A04e validation procedure.
- `.github/workflows/build-android.yml` — GitHub Actions workflow that
  installs the Android toolchain, runs the parser tests through the Gradle
  Wrapper and uploads a debug APK.
- `artifacts/api-server/` — existing shared API scaffold; not used by the
  first Android POC.
- `artifacts/mockup-sandbox/` — existing component preview scaffold; not used
  by the first Android POC.

## Architecture decisions

- The first phase is deliberately local-only: no backend, cloud sync,
  authentication, deletion or background worker.
- Audio discovery uses official `MediaStore.Audio` APIs and
  `RELATIVE_PATH`; it does not require an absolute filesystem path or
  `MANAGE_EXTERNAL_STORAGE`.
- Playback uses the URI returned by MediaStore directly through `MediaPlayer`.
- The Samsung filename suffix is documented and parsed as
  `YYMMDD_HHMMSS`; invalid names remain visible without inferred metadata.

## Product

The POC displays device and permission status, scans MediaStore for `.m4a`
recordings in `Recordings/`, shows metadata and diagnostics, and attempts
playback through each recording's `Content URI`.

## User preferences

- Keep the first phase limited to proving whether Samsung Phone recordings are
  visible and readable; do not add cloud synchronization before the nine-point
  device test succeeds.

## Gotchas

- The Android POC cannot be compiled or validated inside this workspace because
  the required Android toolchain is unavailable.
- Seeing a recording in Samsung's file browser does not prove it is exposed
  through MediaStore; use the in-app diagnostic counts to distinguish the cases.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
