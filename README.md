# A02 Draw

Production-oriented Android starter using Kotlin, XML layouts, ViewBinding, Hilt and a clean multi-module dependency graph.

## Requirements

- Android Studio 2025.3 with support for AGP 9.1.1, or newer
- Android SDK 36 and Build Tools 36.0.0
- JDK 17 or newer (Android Studio's bundled JDK 21 is supported)

Create an untracked `local.properties` when building outside Android Studio:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

## Modules

```mermaid
graph TD
    app --> data
    app --> home[feature:home]
    app --> ui[core:ui]
    home --> domain
    home --> ui
    data --> domain
    data --> common[core:common]
    domain --> common
    ui --> common
```

- `app`: application shell, theme and Navigation host.
- `core:common`: result/error and coroutine abstractions.
- `core:ui`: lifecycle-safe ViewBinding base classes and UI extensions.
- `domain`: Android-free models, repository contracts and use cases.
- `data`: Room, DataStore, Retrofit/OkHttp, mappers and Hilt bindings.
- `feature:home`: runnable XML/Fragment/ViewModel example.
- `build-logic`: convention plugins shared by Android and Kotlin modules.

The Home screen reads a seeded Room database and never requires network access. Set a real backend URL with `-PAPI_BASE_URL=https://api.example.com/`; the committed default is `https://example.com/` and is not called automatically.

## Commands

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew bundleRelease
./gradlew connectedDebugAndroidTest # requires a device or emulator
```

Room schemas are exported to `data/schemas` and must be committed with database changes. Add explicit migrations before increasing the database version; destructive fallback is intentionally disabled.

## Release signing

Unsigned release bundles build without local credentials. To sign a release, provide all four environment variables:

```text
A02_RELEASE_STORE_FILE
A02_RELEASE_STORE_PASSWORD
A02_RELEASE_KEY_ALIAS
A02_RELEASE_KEY_PASSWORD
```

Never commit keystores or credentials. The repository ignores common signing and secrets files.

## Adding a feature

1. Create `feature:<name>` using the `a02.android.library` and `a02.android.hilt` plugins.
2. Depend on `domain` and `core:ui`, never directly on `data`.
3. Add the Fragment destination to the app navigation graph.
4. Put repository interfaces/use cases in `domain` and implementations/mappers in `data`.
