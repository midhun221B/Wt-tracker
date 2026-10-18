# Partner sharing: one-time Google Cloud setup

Partner sharing keeps one file in your Google Drive up to date, and your partner's phone reads it through a link.
For the app to write to your Drive, Google needs a small one-time setup. It's free: no billing account or card.
Allow about 15 minutes.

You only do this once, on a computer or your phone's browser. Your partner doesn't need any of it.

## 1. Create a project
1. Open <https://console.cloud.google.com> and sign in with the Google account whose Drive will hold the file.
2. At the top, click the project picker, then **New project**.
3. Name it `Wt-tracker` and click **Create**. Make sure it's selected in the project picker afterwards.

## 2. Turn on the Drive API
1. Open **APIs & Services → Library**.
2. Search for **Google Drive API**, open it and click **Enable**.

## 3. Consent screen
1. Open **APIs & Services → OAuth consent screen** (on newer consoles: **Google Auth Platform → Branding / Audience / Data access**).
2. User type: **External**. App name: `Wt-tracker`. User support email and developer contact: your email.
3. Scopes (**Data access**): **Add or remove scopes**, then add only
   `https://www.googleapis.com/auth/drive.file` ("See, edit, create and delete only the specific Google Drive files
   you use with this app"). Save.
4. **Audience / Publishing status: Publish app** ("In production"). This scope needs no Google verification, and a
   published app doesn't make you sign in again every week.

## 4. Android client
1. Open **APIs & Services → Credentials → Create credentials → OAuth client ID**.
2. Application type: **Android**.
3. Package name: `io.github.midhun.wttracker`
4. SHA-1 certificate fingerprint (from the app's committed `app/debug.keystore`, which signs every build you install):
   `2D:08:2D:82:9E:FA:2A:1B:A0:DC:13:D0:C6:A0:D6:20:5B:69:0A:25`
5. Click **Create**. There's nothing to copy into the app: Google matches the package and fingerprint.

## 5. On your phone
1. Wt-tracker → **Settings → Partner sharing → Turn on**.
2. Type the name your partner should see, then pick your Google account and allow access.
3. **Send link to partner** opens the share menu: send it on WhatsApp.

Your partner installs the same app, chooses **Follow a partner** on the first screen, and pastes the message.

## If something goes wrong
- **"Couldn't sign in with Google (code 10)"**: the package name or SHA-1 in step 4 doesn't match. Check both.
- **"Access blocked" / "This app isn't verified"**: the app isn't published (step 3.4), or you added a scope other
  than `drive.file`.
- **"Couldn't sign in with Google (code 7)" or "Waiting for a connection"**: no internet; it retries on its own.
- **Your partner sees "This link no longer works"**: you made a new link or turned sharing off. Send the new link.
- To stop at any time: **Turn off** deletes the file and withdraws the app's access. You can also delete
  "Wt-tracker share.json" in Drive yourself.
