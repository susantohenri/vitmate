Build a production-ready Android application named:

**Vitmate: video saver**

The app is a video/audio downloader based on `yt-dlp`, designed for Android and intended for Google Play Store distribution. Implement the complete app, UI, functionality, local persistence, download management, AdMob integration, Google UMP consent flow, bilingual localization, legal pages, and store assets described below.

## 1. Core user journey

### Single download

```text
Open app
↓
Home / URL form
↓
User enters URL
↓
User taps "Watch Ad & Continue"
↓
Rewarded ad is shown
↓
Reward is received
↓
Check URL against remote whitelist
↓
Process URL / fetch metadata
↓
Show thumbnail + title + estimated file size
↓
User selects MP4/MP3 + quality
↓
First-download acknowledgement if required
↓
User taps Download
↓
Download starts
↓
Background download + notification
↓
Download completed
↓
Downloads screen
↓
User taps Play
↓
Video/audio plays locally on the device
```

### Multiple downloads

After a video has been processed and added to the download queue, the user can return to Home and enter another URL without waiting for the previous download to finish.

Each new URL follows:

```text
Enter URL
↓
Watch Ad & Continue
↓
Reward received
↓
Whitelist check
↓
Metadata
↓
Format / quality
↓
Add to download queue
```

Downloads continue in the background.

The Downloads screen must show multiple items with their individual status and progress.

---

# 2. URL input

Home must contain a URL input form.

Support:

* manual URL entry
* paste URL
* detecting a URL supplied through Android Share Sheet

When the app receives a shared URL through Android Share Sheet, automatically populate the URL form.

Do not continuously poll the clipboard.

---

# 3. Rewarded Ad flow

Use Google AdMob rewarded ads.

The user must explicitly initiate the rewarded ad by pressing a button such as:

**Watch Ad & Continue**

Do NOT automatically show a rewarded ad merely because a URL was entered.

The flow is:

```text
User enters URL
↓
User presses Watch Ad & Continue
↓
Rewarded ad
↓
Reward received
↓
Continue processing
```

The whitelist check occurs **after the rewarded ad has been completed/reward received**, according to the agreed user journey.

If the rewarded ad cannot be loaded or completed, do not pretend that the reward was granted.

---

# 4. Remote whitelist

Load the whitelist from:

`https://raw.githubusercontent.com/susantohenri/admob-remote-configs/refs/heads/main/vitmate/whitelist.json`

The whitelist contains allowed domains.

After the rewarded ad has been completed:

1. Extract the hostname from the submitted URL.
2. Check the hostname against the whitelist.
3. If the domain is not allowed, stop processing and show an appropriate error.
4. If allowed, continue with metadata processing.

Support proper hostname matching rather than insecure substring matching.

Example:

`example.com` should not accidentally allow:

`notexample.com`

or another unrelated hostname containing the same text.

---

# 5. Metadata

After the URL passes the whitelist check, use `yt-dlp` to process the URL and obtain metadata.

Display before download:

* thumbnail
* title
* estimated file size
* available formats
* available qualities
* filename derived from metadata

Filename must be sanitized for Android filesystem compatibility.

Do not blindly use the raw title as a filename if it contains characters that are invalid or problematic for the filesystem.

---

# 6. Format and quality

Support:

* MP4 video
* MP3 audio

For video:

* expose available quality options dynamically
* do not display quality options that are unavailable for the selected source

For audio:

* provide MP3 output

The user must be able to select the desired format and quality before downloading.

---

# 7. First-download acknowledgement

Before the user's first download, display:

**I confirm that I have the right or permission to download this content.**

Use a checkbox.

The Download button must remain disabled until the checkbox is checked.

Persist the acknowledgement locally using DataStore or SharedPreferences.

Store the acknowledgement against a Terms of Use version, so the app can request acknowledgement again if the Terms version changes.

---

# 8. Downloads

Create a dedicated Downloads screen.

Each download item should show:

* thumbnail
* title / filename
* status
* progress
* selected format
* selected quality when applicable

Statuses should include at least:

* Queued
* Downloading
* Completed
* Failed

Support:

* background downloads
* download progress
* download again
* completed notification
* failed download notification/status
* multiple queued downloads

The user must be able to add another URL while existing downloads continue.

Example:

```text
Downloads

Video A
Completed

Video B
Downloading 70%

Video C
Queued

Video D
Queued
```

---

# 9. Completed download actions

For completed files provide:

* Play
* Share
* Download Again

Play the local media file on the device using an appropriate Android media playback implementation.

Share the downloaded file using the Android Share Sheet.

---

# 10. Android Share Sheet

Support receiving URLs from other Android applications through Android's Share Sheet.

Example:

```text
Browser
↓
Share
↓
Vitmate
↓
Vitmate URL form populated
```

Also support sharing completed downloaded media files from Vitmate to other Android applications.

---

# 11. App navigation

Main navigation:

### Home

URL input and download/process form.

### Downloads

Download list, queue, progress, completed files and actions.

### Settings

Application preferences and legal/information settings.

---

# 12. Settings

Create a Settings screen containing:

## Preferences

### Language

* Bahasa Indonesia
* English

Default automatically based on the Android operating system language.

Allow the user to manually switch between:

* Bahasa Indonesia
* English

### Appearance

* Light mode
* Dark mode

Persist the selected preference.

---

## Legal & Info

### About

Show:

* application name
* current application version
* Privacy Policy button
* Terms of Use button

Privacy Policy URL:

`https://tokiocv.blogspot.com/2026/07/privacy-policy.html`

Open the Privacy Policy externally when the user taps the button.

### Terms of Use

Create an in-app Terms of Use page at:

`/terms`

The Terms must clearly state that the application must not be used for copyright infringement and that users are responsible for having the necessary rights or permissions for content they download.

Provide access to Terms of Use from:

**Settings → About → Terms of Use**

---

# 13. Disclaimer

On the URL input page, display a clear disclaimer such as:

**Only download content you own or have permission to download.**

Provide both English and Indonesian versions.

---

# 14. AdMob

Integrate:

`com.google.android.gms:play-services-ads`

Use Google AdMob rewarded ads.

For development/testing use the official sample App ID:

`ca-app-pub-3940256099942544~3347511713`

Use proper test ads during development and do not accidentally generate invalid production ad traffic.

---

# 15. Remote AdMob configuration

Load:

`ads_config.json`

from:

`https://raw.githubusercontent.com/susantohenri/admob-remote-configs/refs/heads/main/vitmate/ads_config.json`

Implement the configuration according to the structure contained in that remote configuration.

Do not hard-code values that are intended to be controlled by the remote configuration.

Handle network failure gracefully.

---

# 16. Google UMP

Integrate Google User Messaging Platform (UMP) SDK for AdMob privacy consent.

Implement the standard consent flow:

* update consent information
* show consent form when required
* respect the user's consent status
* only request ads when permitted by the UMP consent state
* provide the appropriate Privacy Options entry point when required

Do not request personalized ads before the required consent flow has been handled.

---

# 17. Localization

The application must be fully bilingual:

* English
* Bahasa Indonesia

Automatically select the initial language based on the Android operating system language.

All user-visible strings must be localized, including:

* navigation
* buttons
* dialogs
* errors
* download statuses
* notifications
* settings
* legal pages
* Terms of Use
* acknowledgement
* disclaimers
* rewarded-ad related UI

Do not hard-code user-visible strings directly into Kotlin source code.

---

# 18. Android / Google Play requirements

Use:

* `compileSdk = 36`
* `targetSdk = 36`

Follow current Android and Google Play requirements for API 36.

Request only permissions actually required by the application.

Implement notifications appropriately for modern Android versions.

Handle Android storage using modern Android storage APIs and app-specific/public media storage as appropriate for downloaded media.

---

# 19. UI / UX

Create a clean modern Android UI.

Prioritize:

* simple URL input
* obvious primary action
* clear download status
* readable progress
* thumbnail + title preview
* easy format/quality selection
* simple Downloads management
* Light/Dark mode
* English/Indonesian localization

The app should work well on different Android screen sizes.

Do not create unnecessary screens or features outside the requirements above.

---

# 20. Error handling

Provide clear localized errors for at least:

* invalid URL
* unsupported/non-whitelisted domain
* whitelist unavailable
* metadata extraction failure
* unavailable media format
* unavailable quality
* file-size estimation unavailable
* rewarded ad unavailable
* rewarded ad not completed
* download failure
* network failure
* insufficient storage
* playback failure

Errors must be understandable to normal users and should not expose raw stack traces.

---

# 21. Store assets

Create the following assets in:

`./store-assets`

Include:

* application icon
* Play Store feature graphic/banner

Use the application icon in the Android application.

Required feature graphic size:

`1024 × 500`

Required application icon size:

`512 × 512`

---

# 22. Code quality

Implement the application as a complete working Android project.

Requirements:

* Kotlin
* modern Android architecture
* clean separation between UI, data, download management, metadata processing, configuration, and persistence
* lifecycle-safe components
* proper error handling
* no placeholder functionality for core features
* no fake download progress
* no mock metadata in the production implementation

Make sure the project builds successfully and the final APK/AAB can be generated from the project.

Before finishing, verify that:

1. Home works.
2. URL input works.
3. Android Share Sheet URL receiving works.
4. Rewarded ad flow works.
5. Remote whitelist is loaded and enforced.
6. Metadata is displayed.
7. MP4/MP3 selection works.
8. Quality selection works.
9. First-download acknowledgement works.
10. Downloads can run in the background.
11. Multiple downloads can be queued.
12. Notifications work.
13. Completed files can be played.
14. Completed files can be shared.
15. Download Again works.
16. Settings work.
17. English/Indonesian localization works.
18. Light/Dark mode works.
19. Privacy Policy opens correctly.
20. Terms of Use works.
21. UMP consent flow works.
22. Remote `ads_config.json` works.
23. Store icon is included in the application.
24. The project builds with `compileSdk 36` and `targetSdk 36`.
