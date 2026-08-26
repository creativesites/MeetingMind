package com.example.core.model

/** Honestly-unknown default when nothing has categorized a learned term yet — never guessed. */
enum class VocabularyTermType { PERSON_NAME, ORGANIZATION, PRODUCT_OR_JARGON, ACRONYM, OTHER }

/** Where a learned correction came from — every source here is a real, explicit user action;
 * there is no "inferred automatically" value because nothing in this system infers one. */
enum class VocabularySource { REPLACE_ALL, SEGMENT_EDIT }

/**
 * One learned correction — see [com.example.core.database.VocabularyEntity] for why this exists
 * and what it is not (not model fine-tuning). Domain-layer mirror of the Room entity, same shape,
 * decoded enums instead of raw strings.
 */
data class VocabularyEntry(
    val id: String,
    val surfaceForm: String,
    val canonicalForm: String,
    val type: VocabularyTermType,
    val confidence: Float,
    val source: VocabularySource,
    val frequency: Int,
    val lastConfirmedAt: Long
)
