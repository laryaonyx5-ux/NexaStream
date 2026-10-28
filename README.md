# NexaStream

NexaStream is a native Android / Android TV interface for discovering and watching Twitch streams. The navigation and feature pages are built inside the app; ordinary menu actions do not open Twitch in a WebView.

## Current scope

- Native French home, discovery, search, followed channels, notifications and settings screens.
- Focusable cards and remote-friendly navigation for Android TV.
- Honest empty states: live listings, channel follows and notifications are not fabricated.
- Twitch Device Code OAuth handling and a Helix client are implemented for top live streams, channel search, followed channels (online and offline) and user identity. The app now lets the user save the public Twitch Client ID on-device in Settings, without rebuilding the APK. A real Client ID and the exact redirect URI `nexastream://auth` registered in the Twitch Developer Console are still required.
- Search, discovery and followed-live screens display API-backed data after login; they show honest empty/error states rather than fabricated streams when authentication or network calls fail.
- Android WorkManager periodically checks followed live channels in the background (Android controls exact timing). Notifications require permission on Android 13+ and a valid OAuth token; expired tokens require reconnecting.
- The current ad-filter setting is groundwork only. General ad/tracker filtering can be improved for NexaStream-owned requests, but this is not a system-wide content blocker and cannot guarantee removal of Twitch in-stream ads, especially ads delivered with the stream itself.

## Twitch setup

1. Create an application in the Twitch Developer Console and copy its public Client ID.
2. In NexaStream Settings → Client ID Twitch, paste that Client ID. It is stored on-device and does not require another build. Alternatively, configure the Gradle property `TWITCH_CLIENT_ID` as a build default. Never put a Client Secret into the APK.
3. In Settings, choose Twitch login. NexaStream uses Twitch’s official Device Code flow: open Twitch’s activation page and enter the temporary code shown by the app. NexaStream never asks for your Twitch password.

## Important implementation note

Ordinary app menus are native. The official Twitch player and chat are embedded together inside the live screen; the app does not extract or invent direct stream URLs. Helix metadata and account screens require a valid Client ID and user OAuth. The ad filter blocks a list of known generic ad/tracker domains in the embedded WebView, but cannot guarantee removal of Twitch ads served from Twitch’s own video infrastructure. Twitch credentials should never be hardcoded into source or ask users for their Twitch password.

## Build

The GitHub Actions workflow builds the debug APK on pushes to patch-1 and main.
