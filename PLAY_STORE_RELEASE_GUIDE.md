# Google Play Store — Wear OS App Release Guide
### WLED BLE Wear — JPChurchouse

---

## Part 0 — Deleting Previous App Attempts

Do this first to avoid package name conflicts.

1. Go to [play.google.com/console](https://play.google.com/console) and select the app you want to delete
2. In the left sidebar go to **Setup → Advanced settings**
3. Scroll to the very bottom of the page
4. Click **Delete app**
5. Confirm when prompted
6. Repeat for any other failed attempts

> ⚠️ Deletion takes up to 24 hours to fully propagate. If you try to create a new app with the same package name (`com.jpchurchouse.wledble`) too soon, Play Console may still reject it as a duplicate. Wait for the deletion to clear, or simply proceed — it will usually work immediately for new app creation even if the old listing is still visible.

---

## Part 1 — Before You Touch Play Console

Get everything ready locally first. Do not log into Play Console until all of these are done.

### 1.1 — Build the Signed AAB

```powershell
cd C:\Users\Jamie\Git\WLED-BLE-Plugin-WearOS-App\WledBleWear
./gradlew clean bundleRelease
```

The output file will be at:
```
app\build\outputs\bundle\release\app-release.aab
```

### 1.2 — Verify the AAB is Signed

Run this command and confirm it returns certificate information (not an error):

```powershell
keytool -printcert -jarfile "app\build\outputs\bundle\release\app-release.aab"
```

Expected output will include lines like:
```
Owner: CN=Jamie Churchouse, OU=R&D, O=JPChurchouse ...
SHA1: AA:A9:B3:36:...
```

If it says "not a signed jar file" — stop. Go back and fix your signing config before proceeding.

### 1.3 — Verify the Keystore Fingerprint Matches

```powershell
keytool -list -v -keystore "C:\Users\Jamie\keystores\wledble-release.jks" -alias wledble
```

The SHA1 in this output must match the SHA1 from step 1.2. If they don't match, you built with the wrong key.

### 1.4 — Prepare Required Assets

Have the following ready before starting Play Console setup:

| Asset | Requirement |
|---|---|
| App icon | 512×512 PNG, no transparency in corners |
| Feature graphic | 1024×500 PNG (required for store listing) |
| Wear OS screenshots | At least 2, square format (e.g. 384×384 or 400×400) |
| Privacy policy URL | A publicly accessible URL (e.g. raw GitHub link) |
| Short description | Max 80 characters |
| Full description | Max 4000 characters |

> Screenshots can be taken from the Android Studio Wear OS emulator if you don't have a physical device. In Android Studio: Tools → Device Manager → create a Wear OS device → launch → take screenshots with the camera button in the emulator toolbar.

---

## Part 2 — Create the App in Play Console

### 2.1 — Create New App

1. Go to [play.google.com/console](https://play.google.com/console)
2. Click **Create app** (top right)
3. Fill in:
   - **App name:** WLED BLE Controller
   - **Default language:** English (United Kingdom) or your preference
   - **App or game:** App
   - **Free or paid:** Free
4. Tick both declaration checkboxes
5. Click **Create app**

---

## Part 3 — Store Listing

In the left sidebar go to **Grow users → Store presence → Main store listing**

### 3.1 — App Details

| Field | Content |
|---|---|
| App name | WLED BLE Controller |
| Short description | Control your WLED lights directly from your wrist via Bluetooth. |
| Full description | *(See PLAY_STORE_LISTING.md in this repo)* |

### 3.2 — Graphics

Upload in this order:

1. **App icon** — 512×512 PNG
2. **Feature graphic** — 1024×500 PNG
3. **Phone screenshots** — This section is optional for Wear OS only apps. Skip it or use a placeholder image if Play Console forces you to add one.
4. **Wear OS screenshots** — Click the Wear OS tab and upload at least 2 square screenshots here

> ⚠️ Always check you are on the **Wear OS** tab when uploading watch screenshots. The Phone tab is a different section entirely.

Click **Save** at the bottom of the page.

---

## Part 4 — App Content Setup

Work through each of these in the left sidebar under **Policy**.

### 4.1 — Privacy Policy

1. Go to **Policy → App content**
2. Click **Privacy policy**
3. Enter your privacy policy URL:
   ```
   https://raw.githubusercontent.com/jpchurchouse/WLED-BLE-Plugin-WearOS-App/main/PRIVACY_POLICY.md
   ```
4. Click **Save**

### 4.2 — App Access

1. Still in **App content**, click **App access**
2. Select **All functionality is available without special access**
3. Click **Save**

### 4.3 — Ads

1. Click **Ads**
2. Select **No, my app does not contain ads**
3. Click **Save**

### 4.4 — Content Rating

1. Click **Content rating**
2. Click **Start questionnaire**
3. Enter your email address
4. Select category: **Utility**
5. Answer all questions — for this app every answer is **No**
6. Click **Save questionnaire**, then **Calculate rating**, then **Apply rating**

### 4.5 — Target Audience

1. Click **Target audience and content**
2. Select age group: **18 and over**
3. Click **Next**, then **Save**

### 4.6 — Data Safety

1. Click **Data safety**
2. Click **Start**
3. Answer each section:

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | No |
| Does your app use encryption? | Yes (Bluetooth uses encryption) |
| Does your app comply with the Families Policy? | No (not targeting children) |

4. Click **Save**

### 4.7 — Advertising ID

1. Click **Advertising ID** (may appear as a separate section)
2. Select **No** — this app does not use the advertising ID
3. Click **Save**

---

## Part 5 — Distribution Setup

### 5.1 — Select Countries (Production Track)

1. In the left sidebar, click **Production**
2. **CRITICAL:** Check the filter button in the top-right corner of the page. It must say **"Wear OS only"** — not "Phones, Tablets, Chrome OS...". Click it and switch to Wear OS only if needed. You must do this every time you navigate to a release section.
3. Click the **Countries/regions** tab
4. Click **Add countries/regions**
5. Click **Select all** (or choose specific countries)
6. Click **Save**

---

## Part 6 — Create and Submit the Release

### 6.1 — Create the Production Release

1. Still in **Production**, confirm the filter still says **Wear OS only** (top right)
2. Click **Create new release**
3. If prompted about Google Play app signing — click **Continue** to use Google-managed signing. This means Google holds the distribution key and you can never lose it.
4. Click **Upload** and select your AAB:
   ```
   app\build\outputs\bundle\release\app-release.aab
   ```
5. Wait for the upload to process — you should see version code 1 (1.0) appear
6. In the **Release notes** box, enter:
   ```
   Initial release. Connect to your WLED BLE peripheral and control your lights directly from your Wear OS watch.
   ```
7. Click **Next**

### 6.2 — Review and Confirm

1. Review the release summary — check:
   - Version code is correct (1)
   - No errors shown (warnings about debug symbols are fine to ignore)
   - Countries are listed
2. Click **Save**, then **Submit for review**

---

## Part 7 — After Submission

### What happens next

| Timeframe | Status |
|---|---|
| Immediately | Status shows "In review" on Dashboard |
| 1–3 days (usually) | Google completes review — approved or rejected with reason |
| After approval | Status changes — allow up to 24 hours for the app to go live on Play Store |

### Where to find your app once live

Wear OS apps are **not** prominently surfaced on the web Play Store (`play.google.com`). Find it via:

- The Play Store app on your Wear OS watch directly
- The Google Play app on your paired Android phone → search by name or package name
- Direct link: `https://play.google.com/store/apps/details?id=com.jpchurchouse.wledble`

---

## Part 8 — Future Updates

For every future update:

1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`
2. Run `./gradlew clean bundleRelease`
3. Verify signing with `keytool -printcert -jarfile ...`
4. In Play Console → Production → **Wear OS only filter** → Create new release
5. Upload the new AAB and write release notes describing what changed
6. Submit for review

> ⚠️ Never reuse a versionCode. Play Store permanently rejects AABs with a versionCode it has seen before, even if the previous release was deleted.

---

## Reference — Key Files

| File | Purpose |
|---|---|
| `C:\Users\Jamie\keystores\wledble-release.jks` | Release keystore — back this up somewhere safe |
| `keystore.properties` | Keystore credentials — never commit to Git |
| `app\build\outputs\bundle\release\app-release.aab` | Signed release bundle for upload |
| `PRIVACY_POLICY.md` | Privacy policy hosted on GitHub |
| `PLAY_STORE_LISTING.md` | Store listing copy |

---

## Common Errors Quick Reference

| Error | Cause | Fix |
|---|---|---|
| "App bundle is not signed" | Signing config missing or broken | Check `build.gradle.kts` signing config and `keystore.properties` path |
| "Wrong key fingerprint" | Different keystore used than first upload | Use the original keystore |
| "Requires android.hardware.type.watch" | Uploading to phone track | Switch filter to **Wear OS only** before creating release |
| "No countries selected" | Distribution countries not set | Go to Countries/regions tab in the correct (Wear OS) track |
| "Unavailable on Google Play" | In a testing track, not Production | Promote release to Production, or create release directly in Production |
| "versionCode already used" | Reused an old version code | Increment versionCode in build.gradle.kts |
