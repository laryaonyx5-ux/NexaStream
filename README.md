# NexaStream

NexaStream is a new Android application project with a dark violet home screen, an integrated Twitch Web experience, and a toggleable lightweight web-request filter.

## Current scope

- No account required to open the app.
- Home screen in French.
- Ad-filter switch enabled by default.
- Twitch opens in an in-app Android WebView; Twitch sign-in is handled by Twitch when needed.
- The request filter blocks selected advertising/tracking domains. This is **not** equivalent to uBlock Origin and cannot guarantee blocking Twitch in-stream ads.

## Build

Open this repository in Android Studio or run `./gradlew assembleDebug` after generating/adding the Gradle wrapper for your environment.
