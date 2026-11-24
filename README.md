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
  - **Export** — save all items as a human-readable `.csv` file via the system file picker.
  - **Import** — replace all current items from a previously exported `.csv` file (with confirmation and validation).
  - **About** — show the application name and version.
- Supports system dark/light mode.

## Export format

Exports are UTF-8 CSV files named `timesince-YYYY-MM-DD.csv` by default:

```csv
title,startDate
Anniversary,2020-06-15
"Moved house, new city",2024-03-01
Coffee,2026-11-24
```

- Header: `title,startDate`
- Dates: ISO `YYYY-MM-DD`
- Rows are ordered oldest start date first
- Titles containing commas, quotes, or line breaks are CSV-escaped
- Import replaces all existing items in one transaction; invalid files are rejected without changing data

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
  util/                 # Elapsed time formatting and CSV serialization
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

## License

Released into the public domain under [The Unlicense](./LICENSE).
