# TimeSince — App Specification

**Status:** v1.1 — confirmed · **Date:** 2026-11-24

## 1. Summary

A small, offline Android app that stores a list of things with a start date and shows, for each one, how much time has passed since that date. No accounts, no sync, no notifications — just a list, a date-aware "time since" label, and manual backup/restore via a database file.

## 2. Goals

- Add, view, edit and remove items with a **title** and a **start date**.
- See elapsed time per item in **years / months / days**, not just a raw day count.
- Work entirely on-device, including offline.
- Back up and restore all items by exporting/importing the local database as a file.
- Be simple enough to build and maintain as a first small Android app.

### Non-goals

- Cloud sync, automatic backup, sharing services or accounts.
- Categories, tags, sorting controls, search, reminders or widgets.
- Time-of-day precision; all dates are calendar dates only.

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

### 5.2 Export

- Tapping **Export** opens the Android system file picker to choose a destination (Storage Access Framework `ACTION_CREATE_DOCUMENT`).
- Suggested filename: `timesince-YYYY-MM-DD.db`, with `.db` as the default extension.
- The app writes a consistent snapshot of the Room/SQLite database to the chosen destination as a single plain `.db` file.
- No storage permission is required; the user chooses where the file is saved.
- Cancelling the picker leaves the app unchanged.
- On success, show a short confirmation such as "Database exported".
- On failure (e.g. cannot write file), show an error message; the local database remains unchanged.

### 5.3 Import

- Tapping **Import** opens the Android system file picker to choose a `.db` file (Storage Access Framework `ACTION_OPEN_DOCUMENT`).
- Import is a **restore/replace operation**: the selected file becomes the app's database (proposed; alternative would be merge/upsert).
- Before replacing data, show a confirmation warning that current items will be replaced by the imported file. Cancelling leaves the app unchanged.
- The selected file is validated before replacement:
  - it must be a readable SQLite database,
  - it must have the expected TimeSince schema/version,
  - it must not be corrupt.
- If validation fails, show an error and leave the current database untouched.
- On success, replace the local database, refresh the list, and show a short confirmation such as "Database imported".
- Importing a database from a future/incompatible app version is rejected; v1 does not include schema migrations for imported files.

### 5.4 About dialog

- Tapping **About** opens a simple dialog containing:
  - application name: **TimeSince**
  - application version: the package `versionName` (currently **1.0**)
- The dialog has a single **Close** button and dismisses on Back/outside tap.

## 6. Data model

```kotlin
data class Item(
    val id: Long = 0,            // generated by the database
    val title: String,           // required, trimmed
    val startDate: LocalDate     // calendar date, no time zone
)
```

- Stored locally in a single table (Room/SQLite).
- One-off, automatic migration not needed for v1; schema is fixed.
- State survives rotation and process death.
- The database file is the unit of import/export; exported files contain the whole database, not individual items.

## 7. Elapsed-time rules

- "Today" is the device's current local calendar date.
- Elapsed time is **calendar-based**, not a fixed 365-day year: whole years, then whole months, then whole days are calculated between the start date and today (equivalent to `Period.between(startDate, today)`).
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
- Export of an empty database produces a valid file with zero items.
- Import/export picker cancellation → no data change.
- Corrupt, non-SQLite, wrong-schema or incompatible-version import file → error message; current data unchanged.
- Import replacement confirmation dismissed → no data change.
- External storage unavailable or write failure → export fails gracefully with an error; local data unchanged.
- The database must be closed/checkpointed before export and cleanly reopened after import so the app never continues against a stale file handle.

## 9. Technical approach (proposed)

- **Language / UI:** Kotlin + Jetpack Compose with Material 3, single Activity, no fragments.
- **Persistence:** Room (one `Item` entity, DAO, database). No network layer.
- **State:** `ViewModel` exposing a `StateFlow` of items; Compose collects it and renders the list.
- **Dates:** `java.time.LocalDate` (minSdk 26, or enable core library desugaring if supporting older devices).
- **File access:** Android Storage Access Framework (`ACTION_CREATE_DOCUMENT` for export, `ACTION_OPEN_DOCUMENT` for import); no broad storage permission.
- **Import/export implementation:** export a consistent SQLite snapshot; for import, validate the selected file and schema before replacing `timesince.db`, then close/reopen Room and refresh all observed state.
- **Structure:** single module, roughly:
  - `data/` — entity, DAO, database
  - `ui/` — list screen, item row, editor popup, About dialog
  - `util/ElapsedFormatter.kt` — pure date-to-label logic
- **Dependencies:** AndroidX only (Compose, Activity Compose, Lifecycle, Room, Material Icons).

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
11. **Given** the menu, **when** I tap **Export** and choose a destination, **then** a `.db` file containing the current database is written and a success message is shown; the current list is unchanged.
12. **Given** the export file picker, **when** I cancel, **then** no file is written and no data changes.
13. **Given** the menu, **when** I tap **Import**, choose a valid compatible `.db` file and confirm replacement, **then** the imported items are shown and remain after restarting the app.
14. **Given** a corrupt, non-SQLite, wrong-schema or incompatible-version import file, **when** I attempt to import it, **then** an error is shown and existing data remains unchanged.
15. **Given** the import confirmation or file picker, **when** I cancel, **then** no data changes.
16. **Given** the menu, **when** I tap **About**, **then** a dialog shows the application name **TimeSince** and version **1.0** with a **Close** button.

## 11. Confirmed decisions

1. **Saving model:** autosave in the edit view; `[Close]` commits a pending title edit. No `[Save]` button.
2. **New-item popup:** `[Cancel]` / `[Add]`.
3. **Future dates:** rejected; the date field defaults to today.
4. **Elapsed display:** compact `6y 5m 8d` plus exact total days.
5. **Deletion:** confirmation dialog, no undo snackbar.
6. **Minimum Android version:** minSdk 26 (as proposed in §9), unless changed later.

7. **Import semantics:** replace the current database with the imported file; no merge/upsert in v1.
8. **Export filename:** `timesince-YYYY-MM-DD.db` by default.

## 12. Out of scope for v1

- Sorting options, search, filters, categories or colours per item.
- Notifications, widgets, shortcuts, cloud sync or automatic backup.
- Merging imported data with existing data, encrypted/password-protected exports, or non-`.db` export formats.
- Editing the app's theme beyond Material 3 defaults; languages beyond system-provided ones.
