# Releasing Downhill and submitting it to the stores

Downhill is free software under the GPL-3.0 licence (`LICENSE`). Releases are signed with a private key that lives only
in your safe place and in the repository's GitHub secrets. CI never signs with it unless the four secrets exist.

## 1. One-time: make the release key (on your own computer)
You need a JDK (`keytool`). In a terminal, in the repo folder:

```
bash tools/make-release-key.sh
```

It asks for a password (12+ characters), writes `downhill-release.jks` and prints four secrets. **Back the .jks file
and the password up somewhere safe** (a password manager plus a copy on a USB stick). If they are lost, the app can never
be updated under the same signature. Do not commit them.

Windows without bash: run the `keytool -genkeypair …` line from the script in PowerShell, then
`[Convert]::ToBase64String([IO.File]::ReadAllBytes("downhill-release.jks"))` for the base64 text.

## 2. One-time: add the four secrets on GitHub
Repo → Settings → Secrets and variables → Actions → New repository secret:

| Name | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the long base64 text |
| `RELEASE_KEYSTORE_PASSWORD` | the password |
| `RELEASE_KEY_PASSWORD` | the same password |
| `RELEASE_KEY_ALIAS` | `downhill` |

Then re-run the latest `main` workflow (Actions → Build → Re-run all jobs). The `publish` job then creates the
`v0.8.2` release with `wt-tracker-v0.8.2.apk`, signed with your key. Until the secrets exist it publishes nothing under a
version tag (the `debug-latest` link keeps serving debug-signed test builds).

## 3. One-time: move your phones to the release key
A new signing key can't update over the debug-signed app, so once, on each phone:
1. Downhill → Settings → Your data → **Save backup**.
2. Uninstall Downhill. Install `wt-tracker-v0.8.2.apk` from the release page.
3. Settings → **Restore from backup**, pick the file.

After this, every build from `debug-latest` and every release is signed with the same key and installs over the last
one. (A partner who follows you has no data to lose; they can just update.) Check a build's signer any time:
`apksigner verify --print-certs wt-tracker-v0.8.2.apk` prints the same SHA-256 every release.

## 4. Each new release
Bump `versionCode` and `versionName` in `app/build.gradle.kts`, add
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 bytes max), merge to `main`. CI tags `v<versionName>`
and attaches the signed APK. The stores pick it up from GitHub.

## 5. Submit to the stores (you, once)
**IzzyOnDroid**: open a new issue at <https://gitlab.com/IzzyOnDroid/repo/-/issues> (project "repo", the "Add app" request),
or follow the current instructions at <https://gitlab.com/IzzyOnDroid/repo/-/blob/master/README.md>:

> Title: Add Downhill (io.github.midhun.wttracker)
> Repository: https://github.com/midhun221B/Wt-tracker
> Licence: GPL-3.0-only. Release APKs are attached to GitHub releases (`wt-tracker-v<version>.apk`), signed with the
> project's own key. Fastlane metadata is in `fastlane/metadata/android/en-US/`.
> Description: Personal weight-loss tracker: plan vs a realistic forecast from weekly weigh-ins and runs. No account, no
> ads, no analytics; all data stays on the phone.
> Known non-free parts: optional Google sign-in (play-services-auth, only for Partner sharing via the user's own Drive)
> and Google ML Kit on-device text recognition (reading a weight or run from a screenshot).

**Orion Store**: <https://github.com/RookieEnough/Orion-Store> keeps its catalogue in
<https://github.com/RookieEnough/Orion-Data>. Open an issue or pull request there following its README (check the current
instructions first); give the GitHub repo URL, the package name `io.github.midhun.wttracker`, a one-line description and
the APK file pattern `wt-tracker-v*.apk`.

**Not (yet) F-Droid's main repo**: it refuses Google Play services and ML Kit. A `foss` build without Drive sharing and
screenshot reading would be needed first.
