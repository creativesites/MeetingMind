package com.craftflowtechnologies.meetingmind.core.circles2

/**
 * Circle covers. A cover is one of three things:
 *  - a picture from the app's own image pack (assets/backgrounds), stored on the circle as `pack:<photo id>`, so it
 *    costs no upload and anyone, member or not, can draw it;
 *  - a photo an admin uploaded, stored as its media path (`circles/<id>/media/<uuid>`), drawn through the Worker;
 *  - nothing, in which case the circle shows the pack picture that fits its type.
 * Pure: no Android, so it is tested directly.
 */
object CircleCovers {
    const val PACK_PREFIX = "pack:"
    private val PACK_ID = Regex("^photo_[0-9]{1,6}$")

    /** What to draw for a circle. */
    sealed interface Source {
        data class Pack(val photoId: String) : Source
        data class Uploaded(val path: String) : Source
    }

    /** The fitting pack picture for each circle type. Every id here is in assets/backgrounds/index.json. */
    private val byTemplate: Map<String, String> = mapOf(
        // Faith
        "small_group" to "photo_206",      // Orchard sunrise
        "prayer_partners" to "photo_416",  // Misty sunrise
        "bible_study" to "photo_510",      // Mountain chapel
        "family" to "photo_468",           // Meadow tree
        "youth" to "photo_492",            // Dandelions
        "ministry_team" to "photo_508",    // Cathedral
        "custom" to "photo_991",           // Golden morning
        "cell_group" to "photo_206",
        // Community
        "friends_group" to "photo_480",    // Meadow path
        "community_club" to "photo_1039",  // Green valley
        "book_club" to "photo_324",        // Woodland path
        "custom_general" to "photo_459",   // Sun in the grass
        // Work
        "project_team" to "photo_987",     // Above the clouds
        "client_engagement" to "photo_434",// Mountain lake
        "exec_board" to "photo_450",       // Glacier lake
        "department" to "photo_1056",      // Sky reflection
        "custom_work" to "photo_385",      // Calm water
        // Learning
        "study_group" to "photo_476",      // Forest light
        "course_cohort" to "photo_502",    // Green forest
        "exam_prep_room" to "photo_1000",  // Snowfield light
        "custom_learning" to "photo_477"   // Lone tree
    )

    private val bySpace: Map<String, String> = mapOf(
        "faith" to "photo_994",    // Still sea at dawn
        "work" to "photo_987",
        "learning" to "photo_476",
        "general" to "photo_480"
    )

    /** Pictures that suit a space, offered first when choosing a cover. */
    private val suitedToSpace: Map<String, List<String>> = mapOf(
        "faith" to listOf("photo_206", "photo_416", "photo_510", "photo_508", "photo_991", "photo_994", "photo_468", "photo_987"),
        "work" to listOf("photo_987", "photo_434", "photo_450", "photo_1056", "photo_385", "photo_481", "photo_1000", "photo_213"),
        "learning" to listOf("photo_476", "photo_502", "photo_1000", "photo_477", "photo_324", "photo_1039", "photo_110", "photo_484"),
        "general" to listOf("photo_480", "photo_1039", "photo_324", "photo_459", "photo_492", "photo_468", "photo_509", "photo_484")
    )

    /** Every picture in the pack, in the order index.json lists them. Kept here so choices work without assets in tests. */
    val packIds: List<String> = listOf(
        "photo_1000", "photo_508", "photo_510", "photo_991", "photo_994", "photo_987", "photo_206", "photo_476", "photo_502",
        "photo_477", "photo_434", "photo_468", "photo_416", "photo_481", "photo_509", "photo_1039", "photo_480", "photo_492",
        "photo_385", "photo_110", "photo_213", "photo_450", "photo_484", "photo_324", "photo_459", "photo_1056"
    )

    fun packRef(photoId: String): String = PACK_PREFIX + photoId

    /** The photo id in a `pack:` reference, or null if [path] is not one (or names something outside the pack). */
    fun packId(path: String?): String? =
        path?.takeIf { it.startsWith(PACK_PREFIX) }?.removePrefix(PACK_PREFIX)?.takeIf { PACK_ID.matches(it) }

    fun isPack(path: String?): Boolean = packId(path) != null

    /** The pack picture for a circle with no cover of its own. Never null: unknown types fall back to their space. */
    fun defaultFor(template: String?, space: String?): String =
        template?.let(byTemplate::get) ?: bySpace[space?.lowercase()] ?: bySpace.getValue("faith")

    /** What to draw: the stored cover when it is usable, else the fitting default. */
    fun resolve(coverPath: String?, template: String?, space: String?): Source {
        packId(coverPath)?.let { return Source.Pack(it) }
        val uploaded = coverPath?.takeIf { it.isNotBlank() && !it.startsWith(PACK_PREFIX) }
        return if (uploaded != null) Source.Uploaded(uploaded) else Source.Pack(defaultFor(template, space))
    }

    fun resolve(circle: Circle): Source = resolve(circle.coverPath, circle.template, circle.space)

    /** The pack offered when choosing a cover: the type's default first, then pictures that suit the space, then the rest. */
    fun choices(template: String?, space: String?): List<String> {
        val first = defaultFor(template, space)
        val suited = suitedToSpace[space?.lowercase()].orEmpty()
        return (listOf(first) + suited + packIds).distinct()
    }
}

/** A cover someone chose: a pack picture (nothing to upload) or their own photo, already a JPEG. */
sealed interface CoverChoice {
    data class Pack(val photoId: String) : CoverChoice
    class Photo(val bytes: ByteArray, val mime: String, val name: String?) : CoverChoice
}

/** The calm line for a cover that didn't save. The circle and its old cover are untouched. */
fun coverFailureLine(f: CirclesFailure): String = when {
    f.kind == FailureKind.Offline || f.kind == FailureKind.NotConnected -> "The cover didn't save because you're offline. Try again when you're connected."
    f.code == "too_large" -> "That photo is too large. Choose a smaller one."
    f.code == "bad_mime" || f.code == "bad_image" -> "That photo couldn't be used. Try another, or pick one of ours."
    f.code == "forbidden" -> "Only the circle's admins can change the cover."
    // An older Worker that doesn't know pack pictures yet answers with a bad request.
    f.code == "bad_request" -> "That cover couldn't be saved yet. Try again later."
    else -> "The cover didn't save. Your circle is fine. Try again in a moment."
}
