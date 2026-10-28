# NexaStream

NexaStream is a native Android / Android TV interface for discovering and watching Twitch streams. The navigation and feature pages are built inside the app; ordinary menu actions do not open Twitch in a WebView.

## Current scope

- Native French home, discovery, search, followed channels, notifications and settings screens.
- Focusable cards and remote-friendly navigation for Android TV.
- Honest empty states: live listings, channel follows and notifications are not fabricated.
- Twitch account integration is prepared at the UI level, but official OAuth and Helix API data still require a registered Twitch developer application, its Client ID and a configured redirect URI.
- The current ad-filter setting is groundwork only. General ad/tracker filtering can be improved for NexaStream-owned requests, but this is not a system-wide content blocker and cannot guarantee removal of Twitch in-stream ads, especially ads delivered with the stream itself.

## Important implementation note

No Twitch pages are embedded in the app in this build. The screens are native, but live metadata, thumbnails, viewer counts, playback and real account notifications still need the official Twitch API/authentication integration. Twitch credentials should never be hardcoded into the source or ask users for their Twitch password.

## Build

The GitHub Actions workflow builds the debug APK on pushes to patch-1 and main.
