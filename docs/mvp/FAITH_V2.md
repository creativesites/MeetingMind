# Faith v2: Fellowship, Circles, Spark and guided practices (V-0)

Status: **decisions confirmed (§6); backend design in §7.** Builds on `MVP_PLAN.md` decisions D4, D5 and D8, and on the founder's answers
of 2026-10-09:

- Anyone can create a circle.
- Firebase is the backend; anonymous auth is already enabled.
- Circles are generalised but personalisable across denominations: testimonies, achievements and shared prayer
  requests matter, along with everything else.
- Spark makes both WhatsApp status and Instagram story formats (the user chooses), and is generalised beyond faith
  to funny, motivational and non-Christian content.

---

## 1. Why the current version can't be kept

Full detail is in AUDIT §6.

- **Security.** Roles and authorship live inside the encrypted payload, so any member can forge an ADMIN role. The
  rules let *any* signed-in user read *any* circle's events if they know the ID. The invite key is stored in plaintext
  in Room, so it ends up in backups.
- **Crashes.** Pasting a circle code crashes the app: `createCircle`/join paths call `getOrThrow()` without a catch,
  and anonymous-auth fallbacks are rejected by the rules.
- **Missing data operations.** There is no edit, delete or member cap, and "leave" never reaches other devices.
- **No notifications.** There is no Firebase Cloud Messaging and no Cloud Functions, so a circle is silent unless the
  app is open. A fellowship feature without notifications won't keep people coming back.

## 2. Circles v2: the concept

> **A Circle is a small, private space where people walk in faith together**: they pray for each other, celebrate
> what God is doing, and grow together. It shapes itself to how *their* group does things.

### 2.1 Personalisable, not prescriptive

Different traditions call these groups different things and run them differently. So every circle has a
**template** that sets sensible defaults, and the creator can change everything later.

| Template | Default post types | Default name for the group |
|---|---|---|
| Small group / Cell | Prayer, Testimony, Study, Encouragement | "Cell group" / "Small group" / "Home fellowship" / "Life group" (pick or type) |
| Prayer partners (2–6) | Prayer, Encouragement | "Prayer partners" |
| Bible study | Study, Reading plan, Prayer | "Bible study" |
| Family | Prayer, Testimony, Encouragement, Achievement | "Family" |
| Youth / Campus | All, plus Spark cards | "Youth" |
| Ministry team | Prayer, Announcement, Study | "Team" |
| Custom | The creator picks | Typed by the creator |

**Per-circle settings (simple, with the rest under Advanced):**

- Name, photo or colour, and the word for the group.
- Which post types are on.
- Reactions: 🙏 Praying · Amen · ❤️ · 🎉. The creator can rename or limit them; some traditions prefer only "Praying".
- Who can invite: admins only, or every member.
- Join approval: on or off.
- Translation and default Bible version: none is imposed. Each person reads verses in their own chosen version.

### 2.2 What people post (day one)

| Post type | What it does | Why it matters |
|---|---|---|
| **Prayer request** | Text (and optional verse). Visible to all members. **"Post anonymously"**: nobody, the admin included, sees who asked. Admin approval is on by default, and auto-approve can be turned on. Others tap **🙏 I prayed**, which counts and can notify the requester once a day. The author posts **updates**, then marks it **Answered 🎉**, which offers to turn it into a Testimony. | The core loop: request → support → update → answered → testimony. |
| **Testimony** | A story of what God did. Can start from an answered prayer or from the existing Testimonies feed. | The founder's priority. Gives the group its emotional peak. |
| **Achievement** | A **suggested** share when the app sees a milestone: finished a reading plan, a 30-day devotional streak, first sermon notes, a memorised verse. Nothing is ever auto-posted. | Celebration, encouragement, and a reason to come back. |
| **Study / sermon share** | Share a sermon note excerpt, a study guide or a question for discussion. Others can comment. | Ties Circles to the app's core (notes). |
| **Encouragement** | A short message, optionally with a verse or a Spark card. | Lightweight connection without a full chat app. |
| **Reading plan together** | The circle follows a plan. Members see who's on track (opt-in) and comment per day. | Daily habit and accountability. |

**Comments** sit under every post: threaded one level deep. **No open group chat at MVP.** Chat is a different
product and moderation-heavy, and posts plus comments cover the need. Events can come in v2.1.

### 2.3 Joining that never crashes

- **Invites.** A short human code (e.g. `GRACE-7K2Q`), a link (`https://…/c/GRACE-7K2Q`) and a QR code.
  - The code is looked up in `invites/{code}` → `circleId`.
  - The code expires or can be revoked, and has optional max uses.
- **Validation.**
  - Pasting accepts the whole share message: the agent extracts the code with a regex.
  - Every failure is an inline message ("That code has expired — ask for a new one"), never a crash.
  - Join flows are wrapped in result types, with no `getOrThrow()` on UI paths.
- **Identity.**
  - Start anonymous; that's set up already.
  - **Offer "Keep your circles on any phone"**, which links the anonymous account to Google sign-in via
    `linkWithCredential` (Credential Manager is already in the app).
  - Without linking, a reinstall loses membership, and the UI says so.
- **Profile.** Display name plus an optional photo, per user. The same profile shows in every circle.

### 2.4 Privacy and security model

**Recommendation: drop the custom end-to-end encryption for v2.**

Use Firestore with strict rules: data is encrypted in transit and at rest by Google, and is readable only by the
circle's members. The current end-to-end encryption is the source of the forged roles, the key-in-the-link problem
and the crashes. It also makes moderation and the church tier impossible, because an admin can't remove abusive
content they can't see server-side. Copy will be **honest**: "Only members of this circle can see what's shared
here." We won't promise "completely private" or "encrypted end-to-end".

**Data model:**

```
circles/{circleId}                 name, template, vocab, settings, ownerUid, memberCount, createdAt
circles/{circleId}/members/{uid}   role: owner|admin|member, displayName, joinedAt, muted
circles/{circleId}/posts/{postId}  type, authorUid, body, verseRef?, anonymous, status (open|answered),
                                   counts {prayed, reactions…}, createdAt, editedAt, deleted
circles/{circleId}/posts/{postId}/comments/{commentId}
circles/{circleId}/posts/{postId}/reactions/{uid}   (one per user; counts kept by a Function)
invites/{code}                     circleId, createdBy, expiresAt, maxUses, uses, revoked
users/{uid}                        displayName, photo, fcmTokens[]
reports/{reportId}                 circleId, postId, reporterUid, reason
```

**Rules (enforced server-side, never trusted from the client):**

- **Read.** A user reads a circle, its posts and comments only if `members/{auth.uid}` exists.
- **Join.** A user creates their own `members/{uid}` doc only with a valid, unexpired invite, and only with
  `role == member`. This is done by the Worker's `join(code)` (§7) so uses and caps are counted atomically.
- **Roles.** Only the owner or an admin changes roles or removes members.
- **Posts.**
  - Authors create posts with `authorUid == auth.uid`.
  - Only the author edits a post or marks it Answered.
  - Admins can soft-delete any post.
- **Anonymous posts.** The author is never stored on the post (§7). Abuse is handled by approval, rejection, reports and per-user rate limits.
- **Caps.** 50 members per circle and 10 circles per user on the free tier. The church tier raises both.

### 2.5 Notifications

FCM is sent from the Cloudflare Worker (§7), so no Blaze plan is needed. **Worker jobs:**

- `joinCircle`
- `onPostCreated` (notify members, respecting mute and quiet hours)
- `onPrayed` (a daily digest to the requester: "5 people prayed for you today")
- `onAnswered` (notify everyone who prayed)
- reaction and comment counters
- `cleanupDeleted`

### 2.6 Church / enterprise tier (designed for, built later)

`organizations/{orgId}` owns many circles. It has org admins, branding, member directory sync, announcements to all
circles, and higher caps. MVP data carries an optional `orgId` on circles, so nothing needs migrating later.

### 2.7 Moderation (day one, minimal)

- Report a post.
- Admins remove posts and members.
- Members block a person, which hides their posts for that member.
- A circle-level "Community guidelines" text the creator can edit, with a kind default.

## 3. Spark v2: "Create" (generalised)

> **Turn any moment into something beautiful to share.** It lives inside Faith, but it is a general app-wide studio.

**Entry points** (one name, *Create*, everywhere):

- "Create" in Spaces, and a **Create card** action on any selected text (F-7 selection menu).
- From a devotional, verse, sermon note, prayer, testimony, achievement, Work decision or study concept.
- A free prompt: "Something funny about Monday meetings".

**The studio flow:**

1. **What:** pick the source, or type.
2. **Vibe:**
   - Faith: Encouraging, Reflective, Celebration
   - Motivational
   - Funny
   - Wisdom
   - Love
   - Custom: the user types the vibe
3. **Format:**
   - **Story 9:16** for WhatsApp status and Instagram stories
   - Square 1:1
   - Portrait 4:5
   - Text-only, to copy
4. **Design:** background (photos, gradients, the user's own photo), font pairs, colour, alignment, and an optional
   verse or attribution line.
5. **Share:** to WhatsApp or Instagram directly, or the system share sheet, or save to the gallery.

**Watermark:** a small "Made with MeetingMind" mark, **off by default**, opt-in for now. Later it will be on for free users and off for Pro.

**Rules:**

- **Faith content keeps the faith contract.** Scripture text always comes from the Bible provider, never from the
  model. A changed reference re-fetches the text. Quotes from notes are verbatim from the note.
- **Non-faith vibes** use a general content contract: no hateful, sexual or demeaning content, no impersonation of
  real people, and no medical, financial or legal claims.
- **Every creation is saved** to a "My creations" gallery, so it can be re-edited, re-shared or pinned. Today nothing
  is persisted.
- **Failures are visible.** If Gemini fails, the user sees "Couldn't generate — try again or write your own", not a
  silent swap to canned text. Offline, the editor works for the user's own text plus curated starters, clearly
  labelled.
- **Remix** works on the current card ("Shorter", "Warmer", "Funnier", "Add a verse"). Each remix is a new version
  that can be undone. No race conditions: generation and remix are serialised per card.

## 4. Guided practices behind every prominent button (D8)

Rule: a big button labelled *Devotional*, *Prayer* or *Bible study* opens a **guided experience**, never a blank
template.

| Button | Guided experience | Ends with |
|---|---|---|
| **Devotional** | Today's devotional (it already works well), read aloud, a reflection question, then "Write your reflection". It saves as a note linked to the passage. | A saved reflection, plus an optional share card or circle post |
| **Prayer** | Choose a guide: **Free**, **ACTS** (Adoration, Confession, Thanksgiving, Supplication), **Lord's Prayer walk-through**, or **Pray for my list**. Each step gives a prompt, space to write or speak, and a timer. Read-aloud or Gemini voice is optional. | A saved prayer entry. Requests can be added to the prayer list or shared to a circle |
| **Bible study** | Choose a method: **SOAP** (Scripture, Observation, Application, Prayer), **Inductive** (Observe, Interpret, Apply), **Verse by verse**, or **Topic study**. Pick a passage; it opens beside the study steps, with cross-references, original-language word taps (already built) and optional AI questions (not answers). | A study note in the Bible-study notebook. Can be shared to a circle as a discussion guide |
| **Sermon** | Record → Faith Note (exists) → **"Make a devotional from this"** (new) → share a moment (Create) → discuss in a circle | A connected trail from sermon to devotional to share to circle |

Methods and labels are neutral across traditions. Users can hide guides they don't use.

## 5. Build plan

| Task | Model | Notes |
|---|---|---|
| V-0b1 | Backend setup: `server/circles-api` Cloudflare Worker (token verification, join, posts, approval, counters, FCM), Firestore rules plus the emulator test suite, FCM in the app | Founder (console, about 20 min) + Sonnet. Rules and Worker are tested before any UI work |
| V-0b2 | Data layer: repositories, Room cache for offline reading, the invite/join flow with result types | Sonnet |
| V-0b3 | Circles UI: list, circle home (feed by post type), composer per type, the prayer loop, comments, settings, invites with QR | Sonnet, on the design system |
| V-0b4 | Notifications and digests | Sonnet |
| V-0b5 | Security review of the rules and functions, plus abuse cases | **Opus** |
| V-0b6 | Remove the old Circles code and the `mindcircle` deep link, and migrate (or drop) the old circle tables with a Room migration | Haiku |
| V-0a | Create studio (Spark v2) with persistence and its own sub-tasks | Sonnet; Haiku for templates and backgrounds |
| V-0c | Guided Prayer and Bible study, and the sermon → devotional action | Sonnet |

## 6. Decisions (founder, 2026-10-09)

| # | Decision |
|---|---|
| 1 ☑ | **Drop the custom E2EE.** Member-only access rules plus honest copy. |
| 2 ☑ | **Start free: Firebase Spark (free plan) + a Cloudflare Worker**, not Blaze and not Supabase (§7). |
| 3 ☑ | **Anonymous means anonymous to everyone, the admin included.** Prayer requests are visible to all members. Every circle has **admin approval of prayer requests on by default**, with an **auto-approve** option. |
| 4 ☑ | Up to **50 members per circle** and **10 circles per user** on free. |
| 5 ☑ | **Watermark off by default** with an opt-in toggle. Later: on for free users, off for Pro. |
| 6 ☑ | The old circles existed only on dev APKs, so **retire them with no migration**. Delete the old tables with a Room migration in V-0b6. |

## 7. Backend: start free

**Recommendation:** keep Firebase on the **free Spark plan** for Auth (anonymous, already set up), Firestore and FCM
(FCM itself is free). Put every *trusted* operation in a small **Cloudflare Worker**, `server/circles-api`, which is
free up to 100k requests/day. This is the same pattern as the existing `server/deepseek-proxy`. The total cost is $0,
and no card is needed.

**What the Worker does.** It verifies the caller's Firebase ID token, checking it against Google's public keys. Then,
with a service account, it writes to Firestore and sends FCM pushes through the REST APIs. It handles:

- **`join(code)`:** validates the invite, applies caps (50 members, 10 circles), creates the membership atomically.
- **`createPost`:** stores the post. For an **anonymous** post, the author's uid is **not written to the post**. It
  goes to `circles/{id}/postAuthors/{postId}` instead, a collection whose rules deny all client reads. Only the Worker
  reads it, to let the real author edit, post updates or mark the request answered. This makes anonymity real:
  Firestore can't hide one field of a readable document, so the field must not be there at all.
- **Prayer approval:**
  - When approval is on, a new prayer request goes to `pending/{postId}`, which admins can read but which carries no
    author.
  - The admin taps Approve or Reject. The Worker publishes or deletes the request and notifies the author privately.
  - Auto-approve publishes immediately.
- **Counters** ("12 prayed"), **notifications** and the **daily prayer digest**. The digest runs on a Cloudflare Cron
  Trigger, which is free.
- **Rate limits** per user (anti-spam), stored in Workers KV.

**What the client does directly** under the Firestore rules: read circles it belongs to, write non-anonymous comments
and reactions on its own behalf, and update its own profile.

**Why not Supabase:**

- Postgres row-level security is excellent for roles. But moving would mean re-doing auth (anonymous users already
  exist in Firebase) and giving up Firestore's offline cache.
- The free tier **pauses a project after 7 days of inactivity** and has no backups. That's a real risk for a
  community feature people rely on.
- Revisit it when the church tier needs relational reporting.

**Why not Blaze now:** Blaze includes a free quota, so it would likely cost $0, but it needs a card and you asked to
start free. The Worker design is portable: the same endpoints could later move to Cloud Functions or Supabase Edge
Functions without client changes.

**Founder actions** (about 20 minutes, guided when we get there): create a Firebase service account key, put it in a
Worker secret, add the Firebase Android app's FCM setup, and deploy the Worker with `wrangler`.

### Devotional delivery

How a devotional gets to the phone on time, and what is said when it doesn't.

1. **Write ahead.** An alarm 90 minutes before the chosen time (`DailyAlarms`, exact when the person allows it, otherwise inexact allow-while-idle; the lead absorbs Doze) starts an expedited `DevotionalWorker`. It needs a network, retries with exponential backoff (30s, doubling) until 30 minutes after delivery time, and never files a classic in place of a failed AI: if AI writers are set up and all fail, it retries. With no AI set up at all, the labelled classic still stands in.
2. **Re-armed.** Each alarm sets the next day's before it works. Boot, app update, clock and time-zone changes, exact-alarm permission changes and every app start set them again.
3. **Delivery time.** The notify alarm checks. Ready: the "ready" notification. Missing: one last try right then (no network requirement, so the phone's own model can serve), then either "ready" or an honest "Today's devotional isn't ready yet" with the reason (offline, AI unavailable, still writing) and two buttons: "Write it now" (expedited write; the notification is replaced with the result) and "Read a classic instead". A late write-ahead that succeeds replaces the notice with "ready".
4. **On the page.** A quiet line says when it was written and by what (Gemini, DeepSeek, on this phone, or a classic). A failed write shows its reason with Try again and Read a classic instead.

**Optional future: cloud pre-generation.** The existing Cloudflare Worker could write the day's devotional before the phone wakes and send it by an FCM data push, which wakes the app even when its alarms were lost. Not built. Privacy trade-off: the server would need the person's devotional profile and, to personalise, some of what the phone now keeps to itself (prayer requests, journal lines). It should therefore use only the profile (tradition, tone, topics) and general signals, never private lines, be opt-in, store nothing after delivery, and the phone must still fall back to on-device writing when no push arrives.

