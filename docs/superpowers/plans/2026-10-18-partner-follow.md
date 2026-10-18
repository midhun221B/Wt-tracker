# Partner Follow Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** My partner's phone runs Wt-tracker in a read-only "follow" mode that shows my data, read from a Drive file my phone keeps up to date and shares by link.

**Architecture:** A share snapshot is a backup with an owner name and an `updatedAt` time (core, tested on the JVM).
- **Partner side:** follow mode downloads the snapshot by its Drive link, writes it into the app's own Room database through the existing restore path, and turns on a `LocalReadOnly` flag that hides every edit control.
- **Owner side:** Play Services authorization with the `drive.file` scope, a small Drive REST client and a WorkManager job keep one public-by-link file current.

**Tech Stack:** Kotlin, kotlinx.serialization (core), Jetpack Compose, Room 2.6.1, WorkManager 2.9.1, `com.google.android.gms:play-services-auth`, `HttpURLConnection`, Robolectric screenshots, adb emulator smoke test.

**Spec:** `docs/superpowers/specs/2026-10-18-partner-follow-design.md`

## Global Constraints
- **Copy:** sentence case everywhere, no ALL-CAPS, no wide letter spacing. Touch targets at least 44 dp. "Estimates, not medical advice" stays on every screen that shows advice, including in follow mode.
- **Owner permission:** only `https://www.googleapis.com/auth/drive.file`.
- **The Drive file:**
  - name: `Wt-tracker share.json`;
  - permission: `{"role":"reader","type":"anyone"}`;
  - link: `https://drive.google.com/file/d/<id>/view?usp=sharing`;
  - partner download: `https://drive.google.com/uc?export=download&id=<id>`.
- **Snapshot JSON:** `{"format":"wt-tracker-share","version":1,"ownerName":…,"updatedAt":…,"backup":{…}}`; `updatedAt` is ISO time in Asia/Tokyo (`ZonedDateTime.now(TOKYO).withNano(0)`).
- **Room:** no schema change. Follow mode and sharing state live in `SharedPreferences("share")`.
- **Network:** INTERNET is used only by sharing and follow. Nothing is uploaded until the owner taps Turn on.
- **Partner phone:** no notifications in follow mode, so the weigh-in reminder is cancelled.
- **Upload timing:** about 60 s after any data change, as unique work `"share-upload"` with `ExistingWorkPolicy.REPLACE`, `NetworkType.CONNECTED`, exponential backoff.
- **Copy strings:**
  - "Follow a partner"
  - "Following {name} · updated {age}"
  - "This isn't a Wt-tracker share link"
  - "This link no longer works. Ask for a new one."
  - "Couldn't read the latest update"
  - "Stop following"
  - "Partner sharing", "Turn on", "Send link to partner", "Copy link", "New link", "Turn off"
  - "Last update {age}", "Waiting for a connection"
  - "Sign in again to keep sharing"
  - "The shared file was removed"
  - share text: "Open this in Wt-tracker to follow my progress: {link}"
- **Deviations from the spec, decided here** (tell the owner):
  - **Opening the link from WhatsApp:** Android 12+ won't let an app open `drive.google.com` links it doesn't own, so there's no intent filter. The partner pastes the link instead (a field plus a **Paste** button). The pasted text may contain the whole WhatsApp message.
  - **Pull-to-refresh** becomes a **Refresh** button on the follow bar.
- **Shipping:** separate PRs:
  - PR 1 = Task 1;
  - PR 2 = Tasks 2–4, follow mode, version unchanged;
  - PR 3 = Tasks 5–8, owner sharing, version 0.8.0 and the release.

  Merge each only when the owner asks. Update HANDOVER in each PR.

## Review Focus
1. **The partner pastes the whole WhatsApp message**, e.g. "Open this in Wt-tracker to follow my progress: https://drive.google.com/file/d/ID/view?usp=sharing". `driveFileId` must find the ID inside the text. (Test in Task 1.)
2. **The partner sets up while offline, or the owner turned sharing off.** Setup shows the error and stays on the step; it never creates an empty profile. After setup, the follow bar shows "This link no longer works. Ask for a new one." and keeps showing the last copy. (Tests in Tasks 2 and 3.)
3. **The owner's app is a newer version** (snapshot version 2): the partner sees "Update Wt-tracker to read this" instead of a crash, and keeps the last copy. (Test in Task 1.)
4. **The owner makes several quick edits** (weigh-in, then edit, then a run): one upload about 60 s after the last edit, not three. (Test in Task 6: the request has a 60 s initial delay and the scheduler uses REPLACE.)
5. **The owner's sign-in lapses or the Drive file is deleted by hand:** the worker stops retrying and the card shows "Sign in again to keep sharing" or "The shared file was removed" instead of failing silently. (Tests in Tasks 5 and 6.)

---

### Task 1: Core share snapshot and link parsing (PR 1)

**Files:**
- Create: `core/src/main/kotlin/wt/core/io/ShareSnapshot.kt`
- Create: `core/src/test/kotlin/wt/core/ShareSnapshotTest.kt`
- Create: `core/src/test/resources/share-sample.json`, a fixture also served to the emulator in Task 4

**Interfaces:**
- Consumes: `encodeBackup(data: AppData, exportedAt: String): String`, `decodeBackup(text: String): AppData` (`core/io/Backup.kt`).
- Produces:
  - `data class ShareSnapshot(val ownerName: String, val updatedAt: String, val data: AppData)`
  - `fun encodeShare(s: ShareSnapshot): String`
  - `fun decodeShare(text: String): ShareSnapshot`, which throws `IllegalArgumentException`. A newer version gives the message "Update Wt-tracker to read this".
  - `fun driveFileId(text: String): String?`
  - `fun driveLink(id: String): String`
  - `fun driveDownloadUrl(id: String): String`

- [ ] **Step 1: Write the failing tests** in `ShareSnapshotTest`:
  - `roundTrip`: `decodeShare(encodeShare(ShareSnapshot("Midhun", "2026-10-18T09:00+09:00", sample)))` equals the input. Reuse the `sample` AppData from `IoAndDashboardTest`, copied into this file.
  - `rejectsOtherFiles`: a backup JSON (`encodeBackup(...)`), `"{}"` and `"not json"` all throw `IllegalArgumentException`.
  - `newerVersionAsksToUpdate`: replacing `"version": 1` with `"version": 2` throws with a message containing "Update Wt-tracker".
  - `unusablePlanIsRefused`: an active plan with one checkpoint throws.
  - `fileIdFromLinks`, each with ID `1AbCdEfGhIjKlMnOpQrStUvWxYz012345`:
    - `https://drive.google.com/file/d/ID/view?usp=sharing` → `ID`;
    - `https://drive.google.com/open?id=ID` → `ID`;
    - `https://drive.google.com/uc?id=ID&export=download` → `ID`;
    - `"Open this in Wt-tracker to follow my progress: https://drive.google.com/file/d/ID/view?usp=sharing"` → `ID`;
    - a bare `ID` → `ID`;
    - `"  ID\n"` → `ID`.
  - `notALink`: `"https://example.com/file/d/ID"`, `"hello"`, `""` and a 10-character bare string give `null`.
  - `linksFromId`: `driveLink("ID") == "https://drive.google.com/file/d/ID/view?usp=sharing"` and `driveDownloadUrl("ID") == "https://drive.google.com/uc?export=download&id=ID"`.
  - `fixtureDecodes`: `decodeShare` of resource `/share-sample.json` gives `ownerName == "Midhun"` and a non-empty `data.weights`.
- [ ] **Step 2: Run the tests and check they fail.** Run `./gradlew :core:test --tests '*ShareSnapshotTest*'`. Expected: compilation fails with `encodeShare` unresolved.
- [ ] **Step 3: Implement `ShareSnapshot.kt`.**
  - A `@Serializable` wrapper holds `format`, `version`, `ownerName`, `updatedAt` and `backup: JsonElement`.
  - Encoding parses `encodeBackup(data, updatedAt)` into a `JsonElement`. Decoding checks the format, then `version <= 1` (otherwise "Update Wt-tracker to read this"), then passes `backup.toString()` to `decodeBackup`.
  - `driveFileId`:
    - first `/file/d/([A-Za-z0-9_-]{20,})` or `[?&]id=([A-Za-z0-9_-]{20,})`, only when the text contains `drive.google.com`;
    - otherwise the trimmed text if it matches `^[A-Za-z0-9_-]{25,}$`;
    - otherwise null.
- [ ] **Step 4: Create the fixture** `share-sample.json`: `encodeShare(ShareSnapshot("Midhun", "2026-11-05T08:00+09:00", <the Robolectric sampleState data: weights 8 Oct–5 Nov, runs, 3 body measurements, Defaults plan>))`. Generate it once with a throwaway main or test print, and check it in.
- [ ] **Step 5: Run the tests and check they pass.** Run `./gradlew :core:test`. Expected: BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 6: Commit** with the message "Core: share snapshot format and Drive link parsing" and the trailers.

### Task 2: Follow data path (PR 2)

**Files:**
- Create: `app/src/main/java/wt/app/share/SharePrefs.kt`
- Create: `app/src/main/java/wt/app/share/Follow.kt`
- Modify:
  - `app/src/main/java/wt/app/data/BackupStore.kt`: add `clearAll()`;
  - `app/src/main/java/wt/app/ui/AppViewModel.kt`: follow, refresh, stop;
  - `app/src/main/java/wt/app/MainActivity.kt`: refresh on resume;
  - `app/src/main/AndroidManifest.xml`: INTERNET permission and `android:networkSecurityConfig`.
- Create:
  - `app/src/main/res/xml/network_security_config.xml`: no cleartext;
  - `app/src/debug/res/xml/network_security_config.xml`: cleartext allowed only for `10.0.2.2`, for the emulator test.
- Test: `app/src/test/java/wt/app/FollowFetchTest.kt`

**Interfaces:**
- Consumes: `decodeShare`, `driveFileId`, `driveDownloadUrl` (Task 1); `AppDatabase.replaceAll(data)`; `Reminder.schedule(context, enabled=false, …)`.
- Produces:
  - `class SharePrefs(context)` with these properties:
    - `mode: Mode` (`enum Mode { Owner, Follow }`, default Owner);
    - `followUrl: String?`, `ownerName: String?`;
    - `lastUpdatedAt: String?`, the snapshot's `updatedAt`;
    - `fileId: String?`, `lastUploadMillis: Long`;
    - `error: ShareError?` (`enum ShareError { NeedsSignIn, FileRemoved, Offline }`);
    - `followError: FollowError?`.
  - `sealed interface FetchResult { data class Ok(val snapshot: ShareSnapshot); data class Failed(val error: FollowError) }` and `enum class FollowError { NotALink, LinkDead, Unreadable, NewerVersion, Offline }`.
  - `fun followSourceUrl(text: String, debug: Boolean = BuildConfig.DEBUG): String?`: `driveFileId(text)?.let(::driveDownloadUrl)`, or the trimmed text itself when `debug` and it starts with `http://10.0.2.2:`.
  - `suspend fun fetchSnapshot(url: String): FetchResult` (IO dispatcher, 15 s timeouts). The mapping:
    - 200 decoded → Ok;
    - 403, 404 or 410 → LinkDead;
    - `IOException` → Offline;
    - decode failure → Unreadable, or NewerVersion when the message contains "Update Wt-tracker".
  - `suspend fun AppDatabase.clearAll()`: deletes every table including the profile.
  - In `AppViewModel`:
    - `fun follow(text: String)`;
    - `fun refreshFollow()`;
    - `fun stopFollowing()`;
    - `val follow: StateFlow<FollowState>`, where `FollowState(val following: Boolean, val ownerName: String?, val updatedAt: String?, val error: FollowError?, val busy: Boolean)`.

- [ ] **Step 1: Write the failing tests** `FollowFetchTest` (plain JUnit, using the JDK `com.sun.net.httpserver.HttpServer` on port 0):
  - `okDecodes`: serve the Task 1 fixture → `Ok` with owner "Midhun".
  - `missingIsLinkDead`: 404 → `Failed(LinkDead)`.
  - `garbageIsUnreadable`: 200 with `"<html>"` → `Failed(Unreadable)`.
  - `newerVersion`: 200 with the fixture's version set to 2 → `Failed(NewerVersion)`.
  - `noServerIsOffline`: an unused closed port → `Failed(Offline)`.
  - `sourceUrl`:
    - `followSourceUrl("…drive link…", debug=false)` gives the download URL;
    - `followSourceUrl("http://10.0.2.2:8765/share.json", debug=false) == null`;
    - with `debug=true` it gives the same string back;
    - `followSourceUrl("hello", true) == null`.

  The fixture is read from the core test resources: add `sourceSets["test"].resources.srcDir("../core/src/test/resources")` in `app/build.gradle.kts`.
- [ ] **Step 2: Run the tests and check they fail.** They run only in CI (`:app:testDebugUnitTest`): push the tests together with a stub `Follow.kt` whose functions `TODO()`, and check that `FollowFetchTest` fails. Alternatively, write the tests and implementation together and check that both pass in CI. The spec doesn't need a red CI run.
- [ ] **Step 3: Implement** `SharePrefs`, `Follow.kt`, `clearAll`, the manifest and the network config.

  The view model:
  - **`follow(text)`:** `followSourceUrl(text)`, where null sets `NotALink`. Then `fetchSnapshot`. On `Ok`:
    - `db.replaceAll(snapshot.data)`;
    - `Reminder.schedule(ctx, false, 0, 0)`;
    - prefs: `mode = Follow`, `followUrl`, `ownerName`, `lastUpdatedAt`, clear `followError`.

    On `Failed`: set the error in the state only, and leave the DB and the mode untouched.
  - **`refreshFollow()`:** only in follow mode, and not more than once every 30 s. On `Ok`, replace the data and clear the error. On `Failed`, keep the data and set `followError`.
  - **`stopFollowing()`:** `db.clearAll()`, then clear the prefs, then `needsSetup` becomes true again because the profile is gone.

  `MainActivity.onResume` calls `vm.refreshFollow()`.
- [ ] **Step 4: Run the tests and check they pass.** Run `./gradlew :core:test` locally, then push and check CI: `FollowFetchTest` passes and the app builds.
- [ ] **Step 5: Commit** with the message "Follow mode data path: fetch, apply, stop" and the trailers.

### Task 3: Follow UI (PR 2)

**Files:**
- Create:
  - `app/src/main/java/wt/app/ui/ReadOnly.kt`: `val LocalReadOnly = staticCompositionLocalOf { false }`;
  - `app/src/main/java/wt/app/ui/FollowUi.kt`: `FollowBar`, `FollowSetupStep` and the `FollowSettings` card.
- Modify:
  - `Onboarding.kt`: a third Welcome button and the step;
  - `AppScaffold.kt`: provide `LocalReadOnly`, show `FollowBar`, hide the Trend share icon, choose the Settings content;
  - `LogScreen.kt`, `RunsScreen.kt`, `BodyScreen.kt`, `PlanScreen.kt`: hide controls when `LocalReadOnly.current`.
- Test: `app/src/test/java/wt/app/ScreenshotTest.kt`

**Interfaces:**
- Consumes: `AppViewModel.follow/refreshFollow/stopFollowing/follow` (Task 2).
- Produces:
  - `@Composable fun FollowBar(name: String, updatedAt: String?, error: FollowError?, busy: Boolean, onRefresh: () -> Unit, now: ZonedDateTime = ZonedDateTime.now(TOKYO))`;
  - `@Composable fun FollowSetupStep(error: FollowError?, busy: Boolean, onFollow: (String) -> Unit, onBack: () -> Unit)`;
  - `@Composable fun FollowSettings(name: String, updatedAt: String?, link: String, onStop: () -> Unit)`;
  - `fun ageText(updatedAt: String, now: ZonedDateTime): String`, which gives "just now", "5 min ago", "2 h ago", "3 days ago".

- [ ] **Step 1: Write the failing tests.**
  - `FormatTest.ageText`:
    - same minute → "just now";
    - 5 min → "5 min ago";
    - 2 h 10 min → "2 h ago";
    - 3 days → "3 days ago".
  - New screenshot tests in `ScreenshotTest`:
    - `11a-follow-setup`: the step, empty;
    - `11b-follow-setup-error`: `error = NotALink`;
    - `11c-follow-today`: `CompositionLocalProvider(LocalReadOnly provides true)` around `Column { FollowBar("Midhun", "2026-11-05T08:00+09:00", null, false, {}, now = 5 Nov 10:00 JST); LogScreen(sampleState(), …) }`;
    - `11d-follow-runs`;
    - `11e-follow-plan`;
    - `11f-follow-settings`;
    - `11g-follow-bar-dead`: `error = LinkDead`.
- [ ] **Step 2: Run the tests and check they fail.** They run in CI: `ageText` and `FollowBar` are unresolved.
- [ ] **Step 3: Implement.**
  - **Welcome:** a third button, `BigButton("Follow a partner", primary = false)`, opens `FollowSetupStep`. The step has:
    - a short explanation;
    - an `OutlinedTextField` labelled "Link from your partner";
    - **Paste**, from `LocalClipboardManager`;
    - **Follow**, disabled while empty or busy;
    - the error text, mapped from `FollowError` with the Global Constraints copy. `Offline` → "No connection. Try again when you're online."; `NewerVersion` → "Update Wt-tracker to read this".
  - **FollowBar:** a slim row above the tabs, in the top bar's `Column` under the TopAppBar. It shows "Following Midhun · updated 2 h ago", or the error line in Warn colour, plus a 44 dp **Refresh** icon button (content description "Refresh").
  - **Read-only hides:**
    - Today: the weigh-in card, ruler, save, delete, "Weigh in"/"Edit", Add run, "+ Add another", the rest-day switch, and tapping a run;
    - Runs: the floating Add run button and tapping a run card;
    - Body: Add, screenshot import, and tapping a measurement;
    - Plan: rows and timeline become non-clickable (`ListRow(enabled = false)` keeps showing the values), and "Add checkpoint" is hidden;
    - Trend: the share icon.
  - **Settings in follow mode:** shows only `FollowSettings` (name, updated time, link, **Stop following** with a confirm dialog saying "Remove Midhun's data from this phone?") and the About card.
- [ ] **Step 4: Run the tests and check they pass.** Push, wait for CI to go green, download the `11*` screenshots from `debug-latest`, and check that no edit control is visible and the copy matches.
- [ ] **Step 5: Commit** with the message "Follow mode UI: setup step, follow bar, read-only screens" and the trailers.

### Task 4: Emulator smoke for follow mode (PR 2)

**Files:**
- Modify:
  - `.github/emulator/smoke.py`: a new final scenario;
  - `.github/workflows/android.yml`: start an HTTP server before the smoke script;
  - `HANDOVER.md`: a "Not merged" line for follow mode.

**Interfaces:**
- Consumes: the fixture `core/src/test/resources/share-sample.json` (Task 1); `followSourceUrl` debug rule (Task 2); the setup copy (Task 3).

- [ ] **Step 1: Write the scenario** at the end of `smoke.py`. Reinstall fresh, then:
  1. tap "Follow a partner";
  2. type `http://10.0.2.2:8765/share-sample.json` with `adb shell input text`;
  3. tap "Follow";
  4. wait for "Following Midhun"; screenshot `20-follow-today`;
  5. assert "Add run" isn't on screen;
  6. open Trend (`21-follow-trend`), then Runs, and assert there's no "Add run";
  7. open Settings and tap "Stop following", then confirm;
  8. wait for "Set up my plan".

  Fail on any crash, as the other scenarios do.
- [ ] **Step 2: Start the server in the emulator job's script.** Before the smoke script, run `python3 -m http.server 8765 --directory core/src/test/resources >/dev/null 2>&1 &`.
- [ ] **Step 3: Run it.** Push and wait for the `emulator` job. Expected: success, with `20-follow-today.png` in the `emulator-latest` release.
- [ ] **Step 4: Commit** with the message "Emulator smoke: follow mode end to end", then update HANDOVER and open PR 2 when the owner asks.

### Task 5: Drive REST client (PR 3)

**Files:**
- Create: `app/src/main/java/wt/app/share/DriveClient.kt`
- Test: `app/src/test/java/wt/app/DriveClientTest.kt`

**Interfaces:**
- Produces: `class DriveClient(private val token: String, private val api: String = "https://www.googleapis.com")`. All methods are `suspend` and run on IO:
  - `create(name: String, json: String): String`: a multipart POST to `$api/upload/drive/v3/files?uploadType=multipart` with metadata `{"name":name,"mimeType":"application/json"}`; returns the `id`.
  - `makePublic(id: String)`: a POST to `$api/drive/v3/files/$id/permissions` with `{"role":"reader","type":"anyone"}`.
  - `update(id: String, json: String)`: a POST to `$api/upload/drive/v3/files/$id?uploadType=media` with the header `X-HTTP-Method-Override: PATCH`, because `HttpURLConnection` can't send PATCH.
  - `delete(id: String)`: a DELETE to `$api/drive/v3/files/$id`; a 404 counts as success.
  - Errors: `class DriveException(val code: Int) : IOException()`. 401 means sign-in has lapsed, 404 means the file is gone; the caller maps these.

- [ ] **Step 1: Write the failing tests** with a JDK `HttpServer` standing in for `api`. Each test checks the method, path, `Authorization: Bearer t` header and body:
  - `createReturnsId`: the server returns `{"id":"abc"}` → `"abc"`, and the body contains `"name":"Wt-tracker share.json"` and the JSON payload.
  - `makePublicPostsAnyoneReader`.
  - `updateUsesPatchOverride`: the header `X-HTTP-Method-Override` is `PATCH`.
  - `deleteIgnores404`.
  - `updateMissingFileThrows404`: `DriveException(404)`.
  - `expiredTokenThrows401`.
- [ ] **Step 2: Run the tests and check they fail** (in CI, or with a stub, as in Task 2).
- [ ] **Step 3: Implement `DriveClient`.** Multipart uses the boundary `wt-share-boundary` with two parts, metadata JSON then `application/json` content.
- [ ] **Step 4: Run the tests and check they pass** in CI.
- [ ] **Step 5: Commit** with the message "Drive REST client for partner sharing" and the trailers.

### Task 6: Authorization and upload worker (PR 3)

**Files:**
- Modify: `app/build.gradle.kts` (add `implementation("com.google.android.gms:play-services-auth:21.2.0")`)
- Create:
  - `app/src/main/java/wt/app/share/DriveAuth.kt`;
  - `app/src/main/java/wt/app/share/ShareWorker.kt`, which also holds `ShareScheduler`.
- Modify: `app/src/main/java/wt/app/ui/AppViewModel.kt`: call `ShareScheduler.changed(ctx)` after every save, delete, undo, plan change, profile change and restore, and add the owner sharing actions.
- Test: `app/src/test/java/wt/app/ShareSchedulerTest.kt`

**Interfaces:**
- Consumes: `DriveClient` (Task 5), `encodeShare`/`ShareSnapshot` (Task 1), `loadAll()`, `SharePrefs` (Task 2).
- Produces:
  - `object DriveAuth`:
    - `const val SCOPE = "https://www.googleapis.com/auth/drive.file"`;
    - `fun request(): AuthorizationRequest`;
    - `suspend fun silentToken(context): String?`, which is null when a resolution (user consent) is needed.
  - `object ShareScheduler`:
    - `fun request(): OneTimeWorkRequest`, with a 60 s initial delay, a CONNECTED constraint and exponential backoff;
    - `fun changed(context)`, which enqueues unique work `"share-upload"` with REPLACE only when `prefs.mode == Owner && prefs.fileId != null`.
  - `class ShareWorker : CoroutineWorker`:
    - no token → `prefs.error = NeedsSignIn` and `Result.failure()`;
    - `DriveException(401)` → NeedsSignIn and failure;
    - `DriveException(404)` → `FileRemoved` and failure;
    - other `IOException` → `prefs.error = Offline` and `Result.retry()`;
    - success → `lastUploadMillis = now`, error cleared.
  - In `AppViewModel`:
    - `fun startSharing(name: String, token: String)`: create, make public, upload the first snapshot, then set `prefs.fileId` and `ownerName`;
    - `fun newLink(token: String)`: delete the old file, then run start again with the same name;
    - `fun stopSharing(token: String?)`: delete the file if there's a token, revoke the token (POST `https://oauth2.googleapis.com/revoke?token=<token>`), clear the prefs and cancel the unique work;
    - `val sharing: StateFlow<SharingState>`, where `SharingState(val on: Boolean, val name: String?, val link: String?, val lastUploadMillis: Long, val error: ShareError?, val busy: Boolean)`.

- [ ] **Step 1: Write the failing test** `ShareSchedulerTest` (Robolectric):
  - `request().workSpec.initialDelay == 60_000`;
  - the constraints require `NetworkType.CONNECTED`;
  - `changed()` with `fileId == null` enqueues nothing. Check this with `WorkManagerTestInitHelper`, which adds `testImplementation("androidx.work:work-testing:2.9.1")`.
- [ ] **Step 2: Run the test and check it fails** (in CI).
- [ ] **Step 3: Implement.**
  - The view model gets its token from the Activity: Task 7's card launches `DriveAuth.request()` through `rememberLauncherForActivityResult(StartIntentSenderForResult)` when a resolution is needed, and reads the token with `Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data).accessToken`.
  - The worker uses `silentToken`.
  - Snapshot: `ShareSnapshot(prefs.ownerName!!, ZonedDateTime.now(TOKYO).withNano(0).toString(), db.loadAll())`.
- [ ] **Step 4: Run the test and check it passes** in CI. The app builds with play-services-auth.
- [ ] **Step 5: Commit** with the message "Partner sharing: authorization and upload worker" and the trailers.

### Task 7: Partner sharing card (PR 3)

**Files:**
- Create: `app/src/main/java/wt/app/ui/PartnerSharingCard.kt`
- Modify: `app/src/main/java/wt/app/ui/SettingsScreen.kt`, to put the card between Backup and About; `AppScaffold.kt` for the wiring and the authorization launcher.
- Test: `ScreenshotTest.kt`

**Interfaces:**
- Consumes: `SharingState`, `startSharing/newLink/stopSharing` (Task 6), `driveLink` (Task 1).
- Produces: `@Composable fun PartnerSharingCard(state: SharingState, onTurnOn: (name: String) -> Unit, onSendLink: () -> Unit, onCopyLink: () -> Unit, onNewLink: () -> Unit, onTurnOff: () -> Unit, onSignIn: () -> Unit, now: Long = System.currentTimeMillis())`.

- [ ] **Step 1: Write the failing screenshot tests:**
  - `12a-sharing-off`: the explanation and **Turn on**;
  - `12b-sharing-on`: "Last update 5 min ago", **Send link to partner**, **Copy link**, **New link**, **Turn off**;
  - `12c-sharing-waiting`: `error = Offline` → "Waiting for a connection";
  - `12d-sharing-sign-in`: `NeedsSignIn` → "Sign in again to keep sharing" and a **Sign in** button;
  - `12e-sharing-removed`: `FileRemoved` → "The shared file was removed" and **New link**.
- [ ] **Step 2: Run the tests and check they fail** (in CI).
- [ ] **Step 3: Implement.**
  - **Turn on** opens a dialog: a name field (pre-filled with the last name used), then authorization, then `startSharing`.
  - **Send link** fires `ACTION_SEND` text "Open this in Wt-tracker to follow my progress: {link}" in a chooser titled "Send link to partner".
  - **Copy link** uses the `ClipboardManager` and shows a snackbar "Link copied".
  - **New link** and **Turn off** each have a confirm dialog: "Your partner's current link will stop working." / "Delete the shared file and stop sharing?"
- [ ] **Step 4: Run the tests and check they pass.** Push, wait for CI to go green, and check the `12*` screenshots.
- [ ] **Step 5: Commit** with the message "Partner sharing card in Settings" and the trailers.

### Task 8: Setup guide, version and handover (PR 3)

**Files:**
- Create: `docs/partner-sharing-setup.md`
- Modify:
  - `app/build.gradle.kts`: versionCode 15, versionName "0.8.0";
  - `HANDOVER.md`: "Not merged", a product decision, and open item 4 marked built;
  - `README.md`: one line pointing to the setup guide.

- [ ] **Step 1: Write the guide** with numbered clicks:
  1. Go to console.cloud.google.com and create a project "Wt-tracker".
  2. APIs & Services → Library → Google Drive API → Enable.
  3. OAuth consent screen → External. App name "Wt-tracker", your email as support and developer contact. Scopes: add `.../auth/drive.file` only. Then **Publish app** (no verification needed for this scope).
  4. Credentials → Create credentials → OAuth client ID → Android:
     - package `io.github.midhun.wttracker`;
     - SHA-1 `2D:08:2D:82:9E:FA:2A:1B:A0:DC:13:D0:C6:A0:D6:20:5B:69:0A:25` (from the committed `app/debug.keystore`).
  5. It's free: no billing account or card. Then on the phone: Settings → Partner sharing → Turn on.

  Add a short troubleshooting section: "Sign-in fails with error 10" means the SHA-1 or package is wrong; "access blocked" means the app isn't published.
- [ ] **Step 2: Bump the version and update HANDOVER.** Run `./gradlew :core:test` and confirm it passes.
- [ ] **Step 3: Check CI.** Push and confirm CI is green, including the emulator. The owner then follows the guide and tests Turn on, Send link and follow on both phones before merging PR 3.
- [ ] **Step 4: Commit** with the message "Partner sharing setup guide, version 0.8.0" and the trailers.
