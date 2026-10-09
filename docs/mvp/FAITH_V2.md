# Faith v2: Fellowship, Circles, Spark and guided practices (V-0)

Status: **draft for founder review.** Builds on `MVP_PLAN.md` decisions D4, D5 and D8, and on the founder's answers
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
| **Prayer request** | Text (and optional verse). "Share anonymously within the circle" option. Others tap **🙏 I prayed**, which counts and can notify the requester once a day. The author posts **updates**, then marks it **Answered 🎉**, which offers to turn it into a Testimony. | The core loop: request → support → update → answered → testimony. |
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
  `role == member`. This is done by a Cloud Function `joinCircle(code)` so uses and caps are counted atomically.
- **Roles.** Only the owner or an admin changes roles or removes members.
- **Posts.**
  - Authors create posts with `authorUid == auth.uid`.
  - Only the author edits a post or marks it Answered.
  - Admins can soft-delete any post.
- **Anonymous posts.** The author is stored but hidden by the client. Admins can see who posted, and the UI says so
  at posting time. This keeps abuse traceable.
- **Caps.** 50 members on the free tier. The church tier raises the cap.

### 2.5 Notifications

This needs **Firebase Cloud Messaging plus Cloud Functions**, which means the Firebase **Blaze** pay-as-you-go plan.
The cost is negligible at this scale.

**Functions:**

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

**Watermark:** a small "Made with MeetingMind" mark, on by default and removable. Whether removing it is a Pro
feature is the founder's call.

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
| V-0b1 | Firebase project setup: Blaze plan, FCM, the Functions project in `server/functions`, rules plus the emulator test suite | Founder (console) + Sonnet. Rules are tested with the Firestore emulator before any UI work |
| V-0b2 | Data layer: repositories, Room cache for offline reading, the invite/join flow with result types | Sonnet |
| V-0b3 | Circles UI: list, circle home (feed by post type), composer per type, the prayer loop, comments, settings, invites with QR | Sonnet, on the design system |
| V-0b4 | Notifications and digests | Sonnet |
| V-0b5 | Security review of the rules and functions, plus abuse cases | **Opus** |
| V-0b6 | Remove the old Circles code and the `mindcircle` deep link, and migrate (or drop) the old circle tables with a Room migration | Haiku |
| V-0a | Create studio (Spark v2) with persistence and its own sub-tasks | Sonnet; Haiku for templates and backgrounds |
| V-0c | Guided Prayer and Bible study, and the sermon → devotional action | Sonnet |

## 6. Open questions for the founder

1. **E2EE.** Is the recommendation to drop custom end-to-end encryption in favour of member-only Firestore rules (plus
   honest copy) acceptable? It is what makes moderation and church admin possible.
2. **Blaze plan.** Can the Firebase project move to Blaze for Cloud Functions and push notifications?
3. **Anonymous posting.** Is "anonymous to members, visible to admins" right?
4. **Free cap.** 50 members per circle, and how many circles per user (e.g. 5) on free?
5. **Watermark.** Is removing "Made with MeetingMind" a Pro feature?
6. **Existing testers' circles.** Can the old circles be retired, with testers asked to recreate them? Migrating
   end-to-end-encrypted data isn't possible server-side.
