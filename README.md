# TimeSince

A simple offline Android app that tracks how long it has been since a date.

Each item has a title and start date. The list shows the elapsed time in compact calendar units (years, months, days) plus the exact total number of days.

## Features

- Add items with a **+** floating action button.
- Each item shows:
  - title
  - start date
  - elapsed time (for example `6y 5m 8d` and `2,352 days total`)
- Tap an item to edit it in a popup.
  - Edits autosave.
  - **Close** dismisses the editor.
  - **Remove** deletes the item after confirmation.
- New items use **Cancel / Add**.
- Future start dates are rejected; new items default to today.
- Data is stored locally on device using Room.
- Top-left **`...`** menu:
  - **Export** — save the whole database as a `.db` file via the system file picker.
  - **Import** — replace the current data from a previously exported `.db` file (with confirmation).
  - **About** — show the application name and version.
- Supports system dark/light mode.

## Tech stack

- Kotlin
- Jetpack Compose + Material 3
- Room (SQLite)
- ViewModel + StateFlow
- `java.time.LocalDate` (`minSdk 26`)

## Project structure

```text
app/src/main/java/com/example/timesince/
  data/                 # Room entity, DAO, database
  ui/                   # Compose screens/dialogs
  util/                 # Elapsed time formatting
  ItemViewModel.kt
  MainActivity.kt
```

## Build

Requirements:

- JDK 17+
- Android SDK with API 37.1 platform installed
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` configured (or open in Android Studio)

Build debug APK:

```bash
./gradlew :app:assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install on a connected device/emulator

```bash
./gradlew :app:installDebug
adb shell am start -n com.example.timesince/.MainActivity
```

## Specification

See [SPEC.md](./SPEC.md) for the product behavior and acceptance criteria.
