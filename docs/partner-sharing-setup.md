# Partner sharing: one-time Google Cloud setup

Partner sharing keeps one file in your Google Drive up to date, and your partner's phone reads it through a link.
For the app to write to your Drive, Google needs a small one-time setup. It's free: no billing account or card.
Allow about 15 minutes.

You only do this once, on a computer or your phone's browser. Your partner doesn't need any of it.

## 0. Turn on the app's web pages
Google wants a home page and a privacy policy before it lets you publish. They're in this repo (`docs/index.html`,
`docs/privacy.html`) and GitHub serves them for free.
1. On GitHub, open the repo's **Settings → Pages**.
2. Source: **Deploy from a branch**. Branch: `main`, folder `/docs` (until partner sharing is merged, pick the
   `claude/fervent-ptolemy-ho7r2a` branch instead). **Save**.
3. After a minute, <https://midhun221b.github.io/Wt-tracker/> and
   <https://midhun221b.github.io/Wt-tracker/privacy.html> open.

## 1. Create a project
1. Open <https://console.cloud.google.com> and sign in with the Google account whose Drive will hold the file.
2. At the top, click the project picker, then **New project**.
3. Name it `Wt-tracker` and click **Create**. Make sure it's selected in the project picker afterwards.

## 2. Turn on the Drive API
1. Open **APIs & Services → Library**.
2. Search for **Google Drive API**, open it and click **Enable**.

## 3. Consent screen
Newer consoles call this **Google Auth Platform**, with Branding, Audience and Data access in the left menu.
1. **Branding**:
   - App name `Wt-tracker`; user support email and developer contact: your email.
   - **Leave the app logo empty.** A logo makes Google ask for a verification that takes weeks.
   - Application home page: `https://midhun221b.github.io/Wt-tracker/`
   - Application privacy policy link: `https://midhun221b.github.io/Wt-tracker/privacy.html`
   - Authorised domains: **Add domain** `midhun221b.github.io`.
   - **Save**.
2. **Audience**: user type **External**.
3. **Data access**: **Add or remove scopes**, then add only `https://www.googleapis.com/auth/drive.file` ("See,
   edit, create and delete only the specific Google Drive files you use with this app"). Save.
4. **Audience**: reload the page, then **Publish app** and confirm ("In production"). This scope needs no Google
   verification, and a published app doesn't make you sign in again every week.

## 4. Android client
1. Open **APIs & Services → Credentials → Create credentials → OAuth client ID** (newer consoles: **Google Auth
   Platform → Clients → Create client**).
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
- **"Publish app" is greyed out**: Branding is missing something (usually the home page or privacy policy link, see
  step 3.1). Save Branding, then reload the Audience page.
- **Want to try it before publishing?** On Audience, **Add users** and add your own email as a test user. It works the
  same, but Google asks you to sign in again about once a week until you publish.
- **"Couldn't sign in with Google (code 10)"**: the package name or SHA-1 in step 4 doesn't match. Check both.
- **"Access blocked" / "This app isn't verified"**: the app isn't published (step 3.4) and you're not a test user, or
  you added a scope other than `drive.file`, or uploaded a logo.
- **"Couldn't sign in with Google (code 7)" or "Waiting for a connection"**: no internet; it retries on its own.
- **Your partner sees "This link no longer works"**: you made a new link or turned sharing off. Send the new link.
- To stop at any time: **Turn off** deletes the file and withdraws the app's access. You can also delete
  "Wt-tracker share.json" in Drive yourself.
