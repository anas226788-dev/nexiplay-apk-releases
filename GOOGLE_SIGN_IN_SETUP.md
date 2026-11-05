# Google sign-in setup

The app uses Android Credential Manager and exchanges Google's ID token with the same Supabase Auth Google provider as the website. The Web OAuth client ID visible in the Supabase provider configuration is already set in the ignored root `local.properties` file; it is passed to Credential Manager as the server client ID.

1. The website already uses Supabase Google OAuth, so keep using that same Web client ID and Supabase provider. Do not replace the configured provider secret.
2. Credential Manager uses the Web client ID as its server client ID. If you also configure a separate Android OAuth client in Google Cloud, include it in Supabase's comma-separated Google client IDs list after the Web client ID.
3. The shared Web client ID is set in the ignored root `local.properties` file. If setting it up on another machine, use:

   ```properties
   GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
   ```

   Alternatively, provide it as the Gradle property `-PGOOGLE_WEB_CLIENT_ID=...` or the `GOOGLE_WEB_CLIENT_ID` environment variable.
4. The checked-in Google Services JSON has no OAuth clients, but it is not the source for this Credential Manager flow. The app reads the shared Web client ID from `local.properties`.

Never place the Web client secret in the Android app. Only the public client ID belongs in app configuration.
