# Play Store Listing — WledBleWear

---

## Short description (80 chars max)

Control your WLED lights from your wrist over Bluetooth. No phone or Wi-Fi.

---

## Full description

WLED BLE Wear controls your WLED lights straight from your Wear OS watch over
Bluetooth Low Energy. It needs no Wi-Fi, no phone and no cloud account. Your
watch talks directly to the light.

Version 2 is a rewrite for the WLED BLE Control usermod. It adds secure pairing,
brightness, a sleep timer, favourites, and a much more capable tile and set of
complications.

⚠️ Version 2 needs the WLED BLE Control usermod on WLED 16 or later. It does not
work with the older WLED BLE plugin used by version 1. See Requirements below.

────────────────────────────────────────
FEATURES
────────────────────────────────────────

● Saved devices: pair once and reconnect with a single tap, with no scanning  
● Secure pairing: an encrypted Bluetooth bond protected by a 6-digit passkey  
● Power on and off, with live state updates from the light  
● Brightness control with a clear level bar  
● Sleep timer: fade out after 15, 30 or 60 minutes, with a live countdown  
● All your WLED presets and playlists, with the active one highlighted  
● Favourites: presets with a WLED quick-load label appear first, emoji and all  
● Previous and next preset buttons  
● Clear guidance when something goes wrong, such as pairing not enabled, a
  wrong passkey or a forgotten bond, with a one-tap fix  
● Works completely offline  

────────────────────────────────────────
TILE
────────────────────────────────────────

Add the WLED Control tile to your carousel for one-swipe control:

● Device name and connection status, with the power button lit when the light is on  
● Power toggle, previous and next preset  
● Two favourite preset buttons, labelled with their quick-load emoji  
● Shows the last known state when the light is out of range  

Tile buttons work in the background. The watch connects, sends the command and
disconnects again in a second or two, so the app never has to open and no
connection is held open to drain your battery.

────────────────────────────────────────
COMPLICATIONS
────────────────────────────────────────

Put your lights on your watch face:

● Power: shows On or Off, and toggles power when tapped  
● Status: device name and active preset  
● Preset: the current preset name  
● Next preset: step through your presets with a tap  
● Off & disconnect: one tap turns the lights off  
● Sleep timer: a countdown ring for the time left  
● Brightness: a gauge showing the current level  
● Device and App icon: quick launchers into the control screen  

────────────────────────────────────────
REQUIREMENTS
────────────────────────────────────────

● A Wear OS 3 or later smartwatch  
● A WLED device (ESP32) running WLED 16 or later with the WLED BLE Control
  usermod:
  https://github.com/JPChurchouse/WLED-BLE-Control-Usermod  

To pair, open WLED → Info and press "BLE pairing: Enable". Then pick the
device on your watch and enter the passkey shown in WLED → Config → Usermods →
BLEControl. Each light accepts up to 3 paired watches.

────────────────────────────────────────
PRIVACY
────────────────────────────────────────

This app collects no data, needs no account and makes no network requests.
Everything happens directly between your watch and your light over Bluetooth.
Nothing leaves your home.

---

## Release notes (v2.0.0)

Version 2 is a full rewrite for the WLED BLE Control usermod (WLED 16+).  
It is not compatible with the old WLED BLE plugin.  
• Secure passkey pairing and saved devices  
• Brightness, sleep timer, favourites, previous/next preset  
• New tile with background power, preset and favourite buttons  
• Five new complications: preset, next preset, off, sleep timer, brightness  
