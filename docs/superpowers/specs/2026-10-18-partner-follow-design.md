# Partner follow mode — design

**Status:** approved in brainstorming on 2026-10-18; waiting for the owner's review of this written spec.

## Goal
My partner (Android) can open Wt-tracker on their own phone any time and see my progress, so they can keep me accountable. They see everything I see, read-only, and it updates on its own. Success means they open the app on any day and see my latest Today, Trend, Runs, Body and Plan without asking me, and without either of us tapping anything after a one-time setup.

## Decisions
- **Transport:** Google Drive, with one file in my Drive that anyone with the link can view. My partner's app reads it through the link and never signs in.
  - Rejected: sending the file by WhatsApp, because it isn't automatic.
  - Rejected: sharing with the partner's Google account, because it needs Drive's restricted read permission. Without Google's security assessment that only works in Testing mode, where the partner would have to sign in again every 7 days.
- **Permission:** only `drive.file` (files the app creates), which is non-sensitive. The consent screen can be published "In production" with no Google verification, and sign-in doesn't expire weekly. It's free, with no billing account.
- **Privacy:**
  - Nothing leaves the phone until I turn sharing on.
  - The link is long and random; anyone who has it can read the file.
  - "New link" and "Turn off" end access immediately.
  - The file is visible in, and deletable from, my own Drive.
- **Notifications:** none on the partner's phone; they see updates when they open the app.
- **Same app:** the same APK runs either as the owner (today's app) or in follow mode, chosen at first-run setup.
- **The app gains the INTERNET permission**, used only by sharing (upload) and follow mode (fetch).

## Owner side
- A new **Partner sharing** card in Settings.
  - **Off:** a short explanation and **Turn on**.
  - **Turn on:**
    1. Type the name the partner sees (e.g. "Midhun").
    2. Google sign-in via the Play Services authorization API with the `drive.file` scope.
    3. The app creates "Wt-tracker share.json" in my Drive and gives "anyone with the link" reader access.
    4. It uploads the first snapshot.
  - **On:**
    - **Send link to partner:** the share sheet with the link and "Open this in Wt-tracker to follow my progress";
    - **Copy link**;
    - "Last update 5 min ago" or "Waiting for a connection";
    - **New link:** deletes the file and creates a new one, then asks me to send the link again;
    - **Turn off:** deletes the file, clears the stored file ID and account, and signs out of the authorization.
- **Uploads:** about a minute after any change to weights, runs, rest days, body measurements, plans or the profile, a `ShareWorker` (WorkManager) runs.
  - It's unique work that replaces any pending one, so quick edits upload once.
  - It needs a network connection and retries with backoff.
  - It overwrites the file's content with a fresh snapshot.
- **Sign-in states:**
  - If the authorization has lapsed, the card says "Sign in again to keep sharing" with a button; uploads pause until then.
  - If the file was deleted in Drive, the worker shows "The shared file was removed" with **New link**.

## Partner side (follow mode)
- **Setup:** first-run setup gets **Follow a partner** next to "Set up my plan" and "Restore a backup".
  - The partner pastes the link, or opens it from WhatsApp: the app handles `drive.google.com` file links through an intent filter, and a pasted link works the same way.
  - The app fetches the file. On success it shows "Following Midhun" and opens the tabs.
  - A bad link shows "This isn't a Wt-tracker share link". A removed file or a link without access shows "This link no longer works. Ask for a new one."
- **Data:** the snapshot is decoded and written to the partner app's own local database with the existing `replaceAll` restore path. All screens and calculations run unchanged on it. Follow mode, the file ID and the name are kept in a small preferences store, so Room needs no schema change.
- **Freshness:**
  - It fetches on every app start and resume, and on pull-to-refresh.
  - Offline, it keeps the last copy.
  - A slim bar above the tabs says "Following Midhun · updated 2 h ago", from the snapshot's `updatedAt`, or "· offline, last update 2 h ago".
  - A file that fails to decode keeps the last good copy and shows "Couldn't read the latest update".
- **Read-only:** a single `readOnly` flag hides every add, edit and delete control:
  - Today: weigh-in card, ruler, save, Add run, rest-day switch, Edit, Delete;
  - Runs: Add run, editing a run;
  - Body: Add, editing a measurement, screenshot import;
  - Plan: checkpoint editor, re-baseline, energy editor (the rows show values but don't open);
  - Trend: the share icon.
  - The Today day switcher still works for looking at past days.
- **Settings in follow mode:**
  - "Following Midhun", updated time, the link;
  - **Stop following**, which clears the local data and preferences and returns to first-run setup;
  - About and the disclaimer;
  - no reminder, export, backup or partner sharing.
- **Reminders:** no weigh-in reminder notifications in follow mode.

## File format (core)
`core/io/ShareSnapshot.kt`:
- `data class ShareSnapshot(val ownerName: String, val updatedAt: String /* ISO, Asia/Tokyo */, val data: AppData)`.
- `encodeShare()` / `decodeShare()`: JSON `{"format":"wt-tracker-share","version":1,"ownerName":…,"updatedAt":…,"backup":{…BackupFile…}}`. It reuses `encodeBackup`/`decodeBackup`, so validation is shared (exactly one active plan with at least two checkpoints, valid dates). It throws `IllegalArgumentException` with a readable message on a wrong format or a newer version.
- `driveFileId(link: String): String?`: accepts `https://drive.google.com/file/d/<id>/view…`, `…/open?id=<id>`, `…/uc?id=<id>&…` and a bare ID (25 or more characters of `[A-Za-z0-9_-]`); anything else gives null.
- The download URL is built from the ID: `https://drive.google.com/uc?export=download&id=<id>`.

## App structure
- `app/.../share/DriveShare.kt`: authorization (Play Services `Identity.getAuthorizationClient`, scope `drive.file`) and Drive REST v3 over `HttpURLConnection`:
  - create the file (multipart upload);
  - update its content (`PATCH uploadType=media`);
  - set the permission (`role=reader, type=anyone`);
  - delete it.
- `app/.../share/ShareWorker.kt`: builds a snapshot from `loadAll()` and uploads it. `ShareScheduler.changed()` is called from the view model after every save or delete.
- `app/.../share/Follow.kt`: fetch by ID, `decodeShare`, then `replaceAll`, and store the updated time.
- `app/.../share/SharePrefs.kt`: mode (owner/follow), file ID, owner name, last upload or fetch time, last error.
- UI:
  - `PartnerSharingCard` in Settings;
  - the follow step in `Onboarding.kt`;
  - `FollowBar` above the tabs;
  - a `readOnly` parameter on the screens.
- One new dependency: `com.google.android.gms:play-services-auth`, from Google Maven, so it builds on CI only.

## Owner's one-time Google Cloud setup
`docs/partner-sharing-setup.md`, step by step with exact clicks:
1. Create a project.
2. Enable the Google Drive API.
3. OAuth consent screen: External, app name, my email; add only the `…/auth/drive.file` scope; then **Publish app** (no verification needed for this scope).
4. Credentials → OAuth client ID → Android: package `io.github.midhun.wttracker` (the application ID) and the SHA-1 of the committed `app/debug.keystore` (printed in the guide).

It's free, with no billing account.

## Error handling summary
- **Owner:**
  - no network → work waits;
  - authorization lapsed → card asks to sign in again;
  - 404 on update → "file was removed" plus New link;
  - other HTTP errors → retry with backoff, and the last error is shown on the card.
- **Partner:**
  - bad link → refused at setup;
  - 403/404 → "This link no longer works";
  - decode failure → keep the last good copy and show a note;
  - offline → last copy plus its age.

## Testing
- **Core (JVM):**
  - `ShareSnapshot` round trip;
  - rejecting other formats and newer versions;
  - an inner backup with fewer than two checkpoints is refused;
  - `driveFileId` on good and bad links.
- **Robolectric screenshots:**
  - follow mode: Today, Trend and Settings (read-only, with the follow bar);
  - the Partner sharing card: off, on, waiting for connection, needs sign-in;
  - the setup "Follow a partner" step and its error state.
- **Emulator smoke:** follow mode against a local HTTP server serving a snapshot (the download base URL can be overridden in debug builds). It checks that setup follows, the tabs show the data with no edit controls, offline shows the last copy, and Stop following returns to setup.
- **Not on CI:** real Google sign-in and Drive upload. The owner tests these on the phone after the Cloud setup.

## Build order (separate PRs)
1. Core `ShareSnapshot` and `driveFileId`, with tests.
2. Follow mode: setup step, fetch, read-only screens, follow bar, Settings, emulator smoke. This can be tested end to end with a hand-made link.
3. Owner sharing: Play Services authorization, Drive REST, `ShareWorker`, Settings card, and the setup guide.
4. Release once the owner has tested sign-in and upload on the phone.

## Out of scope
- Partner notifications.
- The partner logging anything.
- Two-way sharing or several partners.
- iOS.
- Sharing with a specific Google account only.
- Anything other than Google Drive.
