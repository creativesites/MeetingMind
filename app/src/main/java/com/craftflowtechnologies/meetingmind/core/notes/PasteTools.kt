package com.craftflowtechnologies.meetingmind.core.notes

/**
 * Named, reversible transforms for a pasted AI answer (or any Markdown block). Each one is a
 * clear instruction; the result can be previewed, accepted, stepped back or discarded. Presets
 * chain several into one pass so they cost one request.
 */
enum class PasteTool(val label: String, val hint: String, val group: Group, val instruction: String, val output: Output = Output.REPLACE) {
    // Presets
    PRESET_TIDY("Tidy", "Clean up and drop the chatter", Group.PRESET,
        "Remove the conversational filler (greetings, 'Great question', 'Let me explain', 'Here's the plan', sign-offs, offers of more help) and repeated points. Keep every piece of substance, every heading, list, table and code block."),
    PRESET_PLAN("Plan", "A phased plan with a checklist", Group.PRESET,
        "Turn this into a clear, phased plan: a short goal line, then phases as '## Phase n: name' headings, each with concrete numbered steps. Remove chatter and first-person voice ('I would', 'you should' become plain imperatives). Keep code blocks that the steps need. End with '## Action items' as a Markdown checklist ('- [ ] ...')."),
    PRESET_NOTES("Study notes", "Outline + key points", Group.PRESET,
        "Turn this into study notes: a 2-sentence summary at the top, then a clean outline with headings and short bullet points, then '## Key takeaways' with at most 5 bullets. Keep definitions, numbers and names exactly."),
    PRESET_CODE("Just the code", "Keep code, drop the prose", Group.PRESET,
        "Keep only the code, commands and configuration, each in a fenced code block with its language, each preceded by one short line saying what it is. Drop everything else."),

    // Clean up
    DE_CHAT("De-chat", "Remove 'Great question', 'Let me explain'…", Group.CLEAN,
        "Remove conversational chatter and meta commentary (greetings, praise of the question, 'I went through', 'The important thing is', 'Let me', 'Here's', offers of more help). Change nothing else: keep all substance, structure, code and wording."),
    DE_PERSONALIZE("Neutral voice", "'I would…', 'you should…' → plain statements", Group.CLEAN,
        "Rewrite first- and second-person phrasing ('I would', 'I think', 'you should', 'we want') as neutral statements or plain imperatives. Keep meaning, structure, code and detail exactly."),
    DE_HEDGE("Remove hedging", "Drop 'probably', 'it might be worth'", Group.CLEAN,
        "Remove hedging ('probably', 'I think', 'it might be worth', 'perhaps consider') so statements are direct. Do not add claims that were not there. Keep structure and code."),

    // Structure
    OUTLINE("Outline", "Headings and bullets", Group.STRUCTURE,
        "Restructure into a clean outline: short headings, nested bullets, no long paragraphs. Keep every point and all code blocks."),
    TABULARIZE("Make tables", "Comparisons become real tables", Group.STRUCTURE,
        "Wherever the text compares items across attributes (including space-aligned pseudo-tables), present it as a Markdown table. Leave everything else as it is."),
    CHECKLIST("Checklist", "Everything to do as tasks", Group.STRUCTURE,
        "Turn this into a Markdown checklist of concrete tasks ('- [ ] ...'), grouped under short headings where useful. Drop explanation that is not a task."),

    // Summarise
    TLDR("TL;DR", "One or two sentences", Group.SUMMARY, "Summarise in one or two sentences."),
    SUMMARY_PARAGRAPH("One paragraph", "The gist in a paragraph", Group.SUMMARY, "Summarise in one clear paragraph."),
    SUMMARY_BULLETS("5 bullets", "The main points", Group.SUMMARY, "Summarise as at most 5 bullet points, most important first."),

    // Rewrite as
    AS_EMAIL("As an email", "Ready to send", Group.REWRITE, "Rewrite as a clear, friendly email with a subject line at the top ('Subject: ...')."),
    AS_SPEC("As a spec", "Goal, scope, requirements", Group.REWRITE,
        "Rewrite as a short product/technical spec with sections: Goal, Scope, Requirements (numbered), Open questions. Keep code blocks."),
    EXPLAIN_SIMPLE("Explain simply", "Plain words for anyone", Group.REWRITE, "Rewrite so anyone can follow it: plain words, short sentences, keep the key points."),

    // Extract (added below the block, the original stays)
    EXTRACT_ACTIONS("Action items", "Tasks to do, as a checklist", Group.EXTRACT,
        "List every action item as a Markdown checklist under '## Action items'. Include an owner or date in the item when the text gives one. Only items the text actually contains.", Output.BELOW),
    EXTRACT_DECISIONS("Decisions", "What was decided and why", Group.EXTRACT,
        "List every decision under '## Decisions' as bullets: the decision, then ' — ' and the reason when given. Only decisions the text actually contains.", Output.BELOW),
    EXTRACT_QUESTIONS("Open questions", "What's still unanswered", Group.EXTRACT,
        "List every open question or unresolved point under '## Open questions' as bullets. Only what the text actually leaves open.", Output.BELOW);

    enum class Group(val title: String) { PRESET("Presets"), CLEAN("Clean up"), STRUCTURE("Structure"), SUMMARY("Summarise"), REWRITE("Rewrite as"), EXTRACT("Pull out") }
    enum class Output { REPLACE, BELOW }

    companion object {
        const val SYSTEM =
            "You transform text a person pasted into their notes app, usually an answer from an AI assistant. " +
                "Follow the instruction exactly. Reply with the result only, in GitHub-flavoured Markdown: no preamble, no closing remarks, " +
                "no surrounding code fence. Copy code blocks, numbers, names and quotations exactly. Never invent facts."

        /** Long text goes in pieces this big, split at headings or paragraphs, for tools that rewrite it all. */
        const val CHUNK_CHARS = 12_000

        fun prompt(tool: PasteTool, text: String) = "Instruction: ${tool.instruction}\n\nText:\n$text"

        /** Whether a tool keeps the text's length, so long input is done piece by piece. */
        fun rewritesWhole(tool: PasteTool) = tool.group == Group.CLEAN || tool == TABULARIZE || tool == PRESET_TIDY

        /** Splits Markdown into pieces under [max] characters, at headings where possible, never inside a code fence. */
        fun chunks(markdown: String, max: Int = CHUNK_CHARS): List<String> {
            if (markdown.length <= max) return listOf(markdown)
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var fenced = false
            for (line in markdown.lines()) {
                if (line.trim().startsWith("```")) fenced = !fenced
                val boundary = !fenced && (line.startsWith("#") || line.isBlank())
                if (boundary && current.length >= max * 0.6 || current.length >= max && !fenced) {
                    parts += current.toString().trim(); current.clear()
                }
                current.append(line).append('\n')
            }
            if (current.isNotBlank()) parts += current.toString().trim()
            return parts
        }

        /** The model's answer without a stray outer fence or preamble line. */
        fun clean(answer: String): String {
            var t = answer.trim()
            if (t.startsWith("```markdown") || t.startsWith("```md")) t = t.substringAfter('\n').removeSuffix("```").trim()
            return t
        }
    }
}
