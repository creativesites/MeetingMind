# Sign-in registrations for Gmail and Outlook (W13)

The owner has created these registrations. Agents building W13 **use them as written** and don't
create or change registrations.

**These are identifiers, not secrets.** Installed apps use public clients: no client secret exists,
and the client ids appear in every APK. They're safe to commit. **A client secret, refresh token or
access token must never be committed or pasted into any file.** If a console offers a secret, it is
the wrong client type: stop and ask.

## Which client goes with which build

| Build | Package | Signed with | Google client | Microsoft redirect |
| --- | --- | --- | --- | --- |
| **Test and debug builds** (`dist/` APKs; `./gradlew assembleDebug`) | `com.craftflowtechnologies.meetingmind.dev` | `app/dev-debug.keystore`, committed | **Dev client** | `msauth://com.craftflowtechnologies.meetingmind.dev/5wWRPGuPtxCEmMlzxlXOVAoSszg%3D` |
| **Release / Play Store** | `com.craftflowtechnologies.meetingmind` | The Play key (kept by the owner, not in the repo) | **Play client** | `msauth://com.craftflowtechnologies.meetingmind/FESXSvN0NJAEHo4M4ZnxNON6Mz0%3D` |

The build type chooses the client. Expose it as a `BuildConfig` field per build type, so no code
branches on package names.

## Google (Gmail)

- **Cloud project:** `easter-eggs-xbntpk`. Its consent screen must read "MeetingMind".
- **Scope:** `https://www.googleapis.com/auth/gmail.readonly` only. Send stays a draft handoff, so
  no send or compose scope.
- **Type:** Android clients, so there is no secret. Sign-in uses the Google Identity Services /
  Credential Manager authorization flow for Android, not a browser redirect with a secret.

| | Dev client | Play client |
| --- | --- | --- |
| Client id | `785434731634-p56sa9uf14ivlnfh2hubtn1j6nrhta7u.apps.googleusercontent.com` | `785434731634-k9t9e8c1altm4boq43j5f5t4p06ksdhk.apps.googleusercontent.com` |
| Package | `com.craftflowtechnologies.meetingmind.dev` | `com.craftflowtechnologies.meetingmind` |
| SHA-1 | `E7:05:91:3C:6B:8F:B7:10:84:98:C9:73:C6:55:CE:54:0A:12:B3:38` | `14:44:97:4A:F3:74:34:90:04:1E:8E:0C:E1:99:F1:34:E3:7A:33:3D` (from Play Console) |

- **The app is in Testing mode**, so only the owner's listed test users can sign in, and sign-ins
  expire after 7 days. `gmail.readonly` is a restricted scope: real users need Google's app
  verification and a security assessment. **Don't** treat a sign-in failure for another account as a
  bug.
- **To confirm with the owner:** the Play SHA-1 must be the **App signing key**, not the upload
  key, or Play installs fail sign-in.

## Microsoft (Outlook)

- **App registration:** "MeetingMind"
- **Application (client) id:** `658f22a9-30c8-40e6-8400-992ace977d57`
- **Directory (tenant) id:** `5ae3f3e3-6074-451d-8f4d-94638aec6b5f`. **Don't use it as the
  authority.** The app is multi-tenant, so use the `common` authority.
- **Delegated scopes:** `Mail.Read`, `offline_access` (and `User.Read` by default). No send scope.
- **Type:** public client, so no secret. Use MSAL for Android or an AppAuth flow with the redirect
  above, and PKCE.

**Owner's to-do in the Entra portal:**
1. Set Supported account types to "any organizational directory and personal Microsoft accounts".
   It was "Personal Microsoft account users" only, which blocks work inboxes.
2. Add the two Android redirects in the table above.
3. Add the two API permissions.

**Until step 1 and 2 are done, Outlook sign-in will fail.** Build against the ids anyway.

## Rules for W13 (from the brief, repeated because they matter)

- The integration ships **off by default** behind a build-config flag.
- Reads are scoped to messages involving people and domains MeetingMind already knows.
- **Disabled for the Clinical and Legal profiles** unless the person explicitly turns it on, with a
  clear warning.
- Nothing read from mail goes to a cloud model when `WorkPrivacy.mustStayOnDevice` applies.
- Tokens are stored in the Android Keystore-backed encrypted storage, never in plain preferences.
- Add a fake provider for tests. **No test may call Google or Microsoft.**

## What agents can't verify

An agent can't sign in to either service from a build machine. Build everything around the flows,
test with the fake provider, and list what needs a real device and a real account in the handoff.
