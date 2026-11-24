# TimeSince — App Specification

**Status:** v1.2 — confirmed · **Date:** 2026-11-24

## 1. Summary

A small, offline Android app that stores a list of things with a start date and shows, for each one, how much time has passed since that date. No accounts, no sync, no notifications — just a list, a date-aware "time since" label, and manual backup/restore via a human-readable CSV file.

## 2. Goals

- Add, view, edit and remove items with a **title** and a **start date**.
- See elapsed time per item in **years / months / days**, not just a raw day count.
- Work entirely on-device, including offline.
- Back up and restore all items by exporting/importing a **CSV file**.
- Be simple enough to build and maintain as a first small Android app.

### Non-goals

- Cloud sync, automatic backup, sharing services or accounts.
- Categories, tags, sorting controls, search, reminders or widgets.
- Time-of-day precision; all dates are calendar dates only.
- Binary database exports, encrypted/password-protected files, and non-CSV formats.

## 3. Main screen

- Top app bar showing the app name, "TimeSince".
- Top-left **three-dot overflow menu** (`...`) with the items:
  1. **Import**
  2. **Export**
  3. **About**
- A vertically scrolling list, one row per item, each row showing:
  1. **Title** (primary text, truncated with ellipsis if long).
  2. **Start date** (e.g. "15 Jun 2020", formatted for the device locale).
  3. **Elapsed time** (e.g. "6y 5m 8d" and "2,352 days total").
- Default order: **oldest start date first** (longest-running item at the top).
- A circular **[+]** floating action button at the **bottom right**. Tapping it opens the editor in *new item* mode.
- Empty state when there are no items: a short message such as "Nothing here yet — tap + to add your first item."
- Tapping anywhere on a row opens the editor popup for that item.
- The elapsed values refresh on app start, when the app returns to the foreground, and at local midnight when the date rolls over.

## 4. Editor popup

The same popup is used for creating and editing, with two modes:

| Mode | Opened by | Footer buttons |
| --- | --- | --- |
| New | [+] FAB | **[Cancel]** · **[Add]** (confirmed) |
| Edit | tapping a list row | **[Close]** · **[Remove]** (confirmed) |

Contents:

- **Title** — editable text field, pre-filled with the existing title in edit mode, empty in new mode.
- **Start date** — date field that opens a date picker; pre-filled with the item's date in edit mode, with **today** in new mode.
- Footer buttons anchored at the bottom of the popup, in the order shown above.
- Validation: title must be non-blank after trimming. A blank title disables **[Add]** / shows an inline error; existing titles are never allowed to become blank.
- **Save behaviour (confirmed):** autosave — edits apply as they are made; **[Close]** commits any pending title edit and dismisses. **[Cancel]** in new mode discards everything.
- **[Remove]** deletes the item after a short confirmation ("Remove this item?") (confirmed). Dismissing the confirmation leaves the item untouched.
- Dismissing the popup by tapping outside or pressing Back behaves like **[Close]** / **[Cancel]** respectively.

## 5. Import, export and About

### 5.1 Overflow menu

- The top-left three-dot menu opens a dropdown/popup menu with **Import**, **Export** and **About**, in that order.
- Dismissing the menu by tapping outside or pressing Back performs no action.
- Import/Export menu items are disabled while an import or export is running.

### 5.2 Export

- Tapping **Export** opens the Android system file picker to choose a destination (Storage Access Framework `ACTION_CREATE_DOCUMENT`).
- Suggested filename: `timesince-YYYY-MM-DD.csv`, MIME type `text/csv`.
- The app writes a single **UTF-8 CSV** file containing all items, in the same order as the list: oldest start date first.
- CSV format:
  - Header row: `title,startDate`.
  - One row per item.
  - `startDate` uses ISO format `YYYY-MM-DD`.
  - Fields containing commas, quotes, or line breaks are quoted and escaped using standard CSV rules.
  - Internal database IDs are not exported; they are regenerated on import.
- Example:

  ```csv
  title,startDate
  Anniversary,2020-06-15
  "Moved house, new city",2024-03-01
  Coffee,2026-11-24
  ```

- No storage permission is required; the user chooses where the file is saved.
- Cancelling the picker leaves the app unchanged.
- On success, show a short confirmation such as "CSV exported".
- On failure (e.g. cannot write file), show an error message; the local data remains unchanged.

### 5.3 Import

- Tapping **Import** opens the Android system file picker to choose a CSV file (Storage Access Framework `ACTION_OPEN_DOCUMENT`, filtered to text files).
- Import is a **restore/replace operation**: the imported rows replace all current items (confirmed). There is no merge/upsert in v1.
- Before replacing data, show a confirmation warning that current items will be replaced by the imported file. Cancelling leaves the app unchanged.
- The selected file is validated before replacement:
  - it must be readable UTF-8 text,
  - its first row must be the expected header `title,startDate` (case-insensitive),
  - each data row must have exactly two columns,
  - every title must be non-blank after trimming,
  - every date must be a valid ISO `YYYY-MM-DD` calendar date,
  - no date may be in the future.
- If validation fails, show an error and leave the current items untouched.
- On success, replace all items in a single Room transaction, refresh the list, and show a short confirmation such as "CSV imported".
- Importing a file with an unknown/incompatible header is rejected; v1 does not include CSV format migrations.

### 5.4 About dialog

- Tapping **About** opens a simple dialog containing:
  - application name: **TimeSince**
  - application version: the package `versionName` (currently **1.0**)
- The dialog has a single **Close** button and dismisses on Back/outside tap.

## 6. Data model

```kotlin
data class Item(
    val id: Long = 0,                  // generated by the database
    val title: String,                 // required, trimmed
    val startDateEpochDay: Long,       // stored as days since 1970-01-01
)
```

- Stored locally in a single Room/SQLite table.
- State survives rotation and process death.
- The CSV file is the unit of import/export; it contains item data, not the raw SQLite database.
- Database IDs are internal and are not part of the CSV format.

## 7. Elapsed-time rules

- "Today" is the device's current local calendar date.
- Elapsed time is **calendar-based**, not a fixed 365-day year: whole years, then whole months, then whole days are calculated between the start date and today (equivalent to `Period.between(start, today)`).
- Display format (confirmed):
  - Primary: compact units, omitting zeros — `6y 5m 8d`, `3m 2d`, `10d`, `0d` (same day: `Today`).
  - Secondary: exact total day count — `2,352 days total`.
- Dates in the future (confirmed): rejected with an inline message; the date field defaults to today in new-item mode. No countdown behaviour in v1.

## 8. Edge cases

- Very long titles → single line with ellipsis; full title still visible in the editor.
- Leap years and month-length differences → handled by calendar arithmetic, not `days / 365`.
- Device clock/time zone changes → elapsed values recalculated from the current local date whenever the list is shown.
- Locale → date and number formatting follow device settings; the compact `y/m/d` abbreviations stay language-neutral in v1.
- Rapid taps / duplicate submissions → a new item is created only once per `[Add]` tap.
- Export of an empty list produces a valid header-only CSV file.
- Import/export picker cancellation → no data change.
- Titles containing commas, quotes, or line breaks are escaped on export and restored on import.
- Malformed CSV, wrong header, wrong column count, blank titles, invalid dates, or future dates → error message; current data unchanged.
- Import replacement confirmation dismissed → no data change.
- External storage unavailable or write failure → export fails gracefully with an error; local data unchanged.
- Import replaces all items in one Room transaction; no stale database file handles are involved.

## 9. Technical approach

- **Language / UI:** Kotlin + Jetpack Compose with Material 3, single Activity, no fragments.
- **Persistence:** Room (one `Item` entity, DAO, database). No network layer.
- **State:** `ViewModel` exposing a `StateFlow` of items; Compose collects it and renders the list.
- **Dates:** `java.time.LocalDate` (minSdk 26).
- **File access:** Android Storage Access Framework (`ACTION_CREATE_DOCUMENT` for export, `ACTION_OPEN_DOCUMENT` for import); no broad storage permission.
- **Import/export implementation:**
  - A pure `CsvSerializer` encodes/decodes `title,startDate` CSV with ISO dates.
  - Export reads a one-shot DAO snapshot, serializes it, and writes UTF-8 text to the chosen URI.
  - Import copies the selected URI to a temporary file, validates and parses it before any mutation, then replaces all rows in a single Room transaction.
- **Structure:** single module, roughly:
  - `data/` — entity, DAO, database
  - `ui/` — list screen, item row, editor popup, About dialog
  - `util/ElapsedFormatter.kt` — pure date-to-label logic
  - `util/CsvSerializer.kt` — pure CSV encode/decode and validation
- **Dependencies:** AndroidX only (Compose, Activity Compose, Lifecycle, Room, Material Icons). No CSV library needed.

## 10. Acceptance criteria

1. **Given** an empty app, **when** it opens, **then** the empty state is shown and the [+] FAB is visible at the bottom right.
2. **Given** the app, **when** I tap [+], enter a title, pick a start date and tap [Add], **then** a new row appears with that title, date and the correct elapsed label; it persists after restarting the app.
3. **Given** the new-item popup, **when** it opens, **then** the start date is today; picking a date after today shows an inline error and prevents adding.
4. **Given** an item started 15 Jun 2020, **when** today is 23 Nov 2026, **then** the row shows `6y 5m 8d` and `2,352 days total`.
5. **Given** an item started today, **when** the list is shown, **then** the elapsed label reads `Today` / `0d`.
6. **Given** a populated list, **when** I tap a row, **then** a popup appears with the item's title and start date and `[Close]` / `[Remove]` at the bottom.
7. **Given** the popup, **when** I change the title or date, **then** the change is saved immediately and the row updates; tapping `[Close]` simply dismisses it.
8. **Given** the popup, **when** I tap `[Remove]` and confirm, **then** the item disappears and stays deleted after a restart; cancelling the confirmation leaves it in place.
9. **Given** the app is backgrounded across local midnight, **when** it resumes, **then** all elapsed labels are recalculated for the new date.
10. **Given** the main screen, **when** I tap the top-left `...` menu, **then** a menu opens with **Import**, **Export** and **About** in that order.
11. **Given** the menu, **when** I tap **Export** and choose a destination, **then** a `.csv` file with header `title,startDate` and one ISO-dated row per item is written, a success message is shown, and the current list is unchanged.
12. **Given** the export file picker, **when** I cancel, **then** no file is written and no data changes.
13. **Given** the menu, **when** I tap **Import**, choose a valid compatible `.csv` file and confirm replacement, **then** the imported items are shown and remain after restarting the app.
14. **Given** a malformed, wrong-header, invalid-date, or future-date import file, **when** I attempt to import it, **then** an error is shown and existing items remain unchanged.
15. **Given** the import confirmation or file picker, **when** I cancel, **then** no data changes.
16. **Given** the menu, **when** I tap **About**, **then** a dialog shows the application name **TimeSince** and version **1.0** with a **Close** button.

## 11. Confirmed decisions

1. **Saving model:** autosave in the edit view; `[Close]` commits a pending title edit. No `[Save]` button.
2. **New-item popup:** `[Cancel]` / `[Add]`.
3. **Future dates:** rejected; the date field defaults to today.
4. **Elapsed display:** compact `6y 5m 8d` plus exact total days.
5. **Deletion:** confirmation dialog, no undo snackbar.
6. **Minimum Android version:** minSdk 26.
7. **Import semantics:** replace all current items with the imported rows; no merge/upsert in v1.
8. **Export filename:** `timesince-YYYY-MM-DD.csv` by default.
9. **Export format:** human-readable CSV with header `title,startDate` and ISO `YYYY-MM-DD` dates.

## 12. Out of scope for v1

- Sorting options, search, filters, categories or colours per item.
- Notifications, widgets, shortcuts, cloud sync or automatic backup.
- Merging imported data with existing data.
- Binary database exports, encrypted/password-protected exports, XML/JSON exports, or other non-CSV formats.
- Editing the app's theme beyond Material 3 defaults; languages beyond system-provided ones.
