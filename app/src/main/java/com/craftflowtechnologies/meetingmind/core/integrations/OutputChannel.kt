package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import java.io.File

/**
 * An output channel through which MeetingMind content (text, files, follow-ups) can be dispatched.
 */
interface OutputChannel : IntegrationProvider {
    override val category: IntegrationCategory
        get() = IntegrationCategory.OUTPUT_CHANNEL

    /** Send or hand off formatted text to the output channel. */
    fun shareText(context: Context, subject: String, text: String, title: String = "Share")

    /** Share a file (such as exported PDF/DOCX or audio) to the output channel. */
    fun shareFile(context: Context, file: File, title: String = "Share file")
}
