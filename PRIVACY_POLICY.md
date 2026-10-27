# Privacy Policy

**App:** WLED BLE Wear
**Developer:** Jamie Churchouse (JPChurchouse)
**Last updated:** October 2026

---

## Overview

WLED BLE Wear is a Wear OS application that communicates over Bluetooth Low Energy (BLE) with the [WLED BLE Control usermod](https://github.com/JPChurchouse/WLED-BLE-Control-Usermod) for [WLED](https://kno.wled.ge/) to allow control of WLED-powered devices directly from a smartwatch.

---

## Data Collection

**This app does not collect, store, or transmit any personal data.**

Specifically:

- No account registration or login is required
- No personal information is collected
- No usage analytics or telemetry are gathered
- No crash reporting data is sent to any server
- The only data the app stores is kept on your watch and never leaves it (see *Data Stored on Your Watch* below)

---

## Data Stored on Your Watch

To work without a phone or network, the app keeps a small amount of data in its private storage on the watch:

- The names and Bluetooth addresses of the WLED devices you have saved
- Each device's last known state (power, brightness, active preset, sleep timer) and its preset names, so the tile and complications can show them when the light is out of range

Wear OS also stores the Bluetooth pairing (bond) keys created when you pair the watch with a device; these are managed by the operating system.

This data is never sent anywhere. Using "Forget device" in the app, clearing the app's storage, or uninstalling the app deletes it. Pairing keys can be removed in the watch's Bluetooth settings.

---

## Bluetooth

The app uses Bluetooth Low Energy (BLE) to communicate with a local BLE peripheral device running the WLED BLE Control usermod. This communication is:

- **Local only**, directly between your watch and a nearby BLE device
- **Encrypted**, using a Bluetooth bond protected by a passkey
- **Not transmitted over the internet**
- **Not logged or recorded** by this app

The Bluetooth permissions are used solely to discover, pair with and communicate with your WLED devices. On older Wear OS versions Android requires the location permission for Bluetooth scanning. The app does not use your location.

---

## Network Access

This app does **not** make any network requests. No data is sent to any remote server, cloud service, or third party.

---

## Third-Party Services

This app does not integrate with any third-party SDKs, analytics platforms, advertising networks, or external services.

---

## Children's Privacy

This app does not collect data from any users, including children. It is compliant with COPPA and equivalent regulations by virtue of collecting no data whatsoever.

---

## Changes to This Policy

If the app is updated in a way that changes its data handling, this policy will be updated accordingly and the **Last updated** date above will reflect that change.

---

## Contact

If you have any questions about this privacy policy, please open an issue on the [GitHub repository](https://github.com/JPChurchouse/WLED-BLE-Control-WearOS-App) or contact Jamie Churchouse directly via GitHub.
