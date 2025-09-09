Android Google Services (Optional)

- The app integrates Firebase Auth and Google Sign-In. Locally, place your google-services.json under app/ to enable the Google Services Gradle plugin.
- In CI, we build without google-services.json. The build script conditionally applies the plugin only when the file exists.
- Env/properties still allow configuring GOOGLE_SERVER_CLIENT_ID, GOOGLE_ANDROID_CLIENT_ID for dev without the JSON.