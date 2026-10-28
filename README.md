# NexaStream

NexaStream is a native Android / Android TV interface for discovering and watching Twitch streams. The navigation and feature pages are built inside the app; ordinary menu actions do not open Twitch in a WebView.

## Current scope

- Native French home, discovery, search, followed channels, notifications and settings screens.
- Focusable cards and remote-friendly navigation for Android TV.
- Honest empty states: live listings, channel follows and notifications are not fabricated.
- Twitch OAuth callback handling and a small Helix client are implemented for top live streams, channel search, followed live streams and user identity. Live API calls stay disabled until a real Twitch Client ID is configured and the developer application has the exact redirect URI `nexastream://auth` registered.
- Search, discovery and followed-live screens display API-backed data after login; they show honest empty/error states rather than fabricated streams when authentication or network calls fail.
- Android notifications can check for newly live followed channels when NexaStream is opened. Continuous notifications while the app is closed still need a reliable background/EventSub service and are not claimed as finished.
- The current ad-filter setting is groundwork only. General ad/tracker filtering can be improved for NexaStream-owned requests, but this is not a system-wide content blocker and cannot guarantee removal of Twitch in-stream ads, especially ads delivered with the stream itself.

## Twitch setup

1. Register NexaStream in the Twitch Developer Console.
2. Register the redirect URI exactly as `nexastream://auth`.
3. Configure the Gradle project property `TWITCH_CLIENT_ID` for the build (the client ID is not a password; never put a client secret in the APK).
4. Build and install the APK, then use Settings → Twitch login. NexaStream opens Twitch’s own authorization page; it does not ask for the Twitch password inside the app.

## Important implementation note

Ordinary app menus are native. The official Twitch chat is the only intended WebView surface and is opened from a live screen, not from a separate navigation tab. Helix metadata and account screens require OAuth setup. A native Twitch video player is not included yet: the Helix API does not itself return a playable stream URL, and the app must use a playback method permitted by Twitch. Twitch credentials should never be hardcoded into the source or ask users for their Twitch password.

## Build

The GitHub Actions workflow builds the debug APK on pushes to patch-1 and main.
