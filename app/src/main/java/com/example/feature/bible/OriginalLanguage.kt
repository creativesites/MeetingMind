package com.example.feature.bible

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.core.originals.LexiconEntry
import com.example.core.originals.MorphDecoder
import com.example.core.originals.OriginalWord
import com.example.core.originals.OriginalsDownloadWorker
import com.example.core.originals.OriginalsPack
import com.example.core.originals.OriginalsStore
import com.example.core.scripture.BibleBooks
import com.example.core.scripture.ScriptureReference
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.FaithGold
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.SurfaceRaised
import com.example.ui.theme.SurfaceSunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The verse in Hebrew or Greek, word by word, straight from STEPBible's tagged text: the word,
 * how it's said, its short English, and — on tap — its dictionary entry and grammar. Nothing here
 * is written or interpreted by the app or an AI.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OriginalLanguageSection(reference: ScriptureReference) {
    val context = LocalContext.current
    val hebrew = BibleBooks.all.indexOf(reference.book) < 39
    val pack = if (hebrew) OriginalsPack.HEBREW_OT else OriginalsPack.GREEK_NT
    val store = remember { OriginalsStore.get(context) }
    // Bumped when a download finishes, so the section re-reads the store.
    var version by remember { mutableStateOf(0) }
    val installed by produceState(false, version) { value = withContext(Dispatchers.IO) { pack in store.installed() } }
    val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(OriginalsDownloadWorker.name(pack)) }.collectAsStateSafe()
    val running = work?.firstOrNull()?.takeIf { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    val finished = work?.firstOrNull()?.state == WorkInfo.State.SUCCEEDED
    androidx.compose.runtime.LaunchedEffect(finished) { if (finished) version++ }

    Text("IN ${pack.language.uppercase()}", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = FaithGold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    when {
        installed -> {
            val from = reference.verseStart ?: 1
            val to = reference.verseEnd ?: reference.verseStart ?: 200
            val words by produceState<List<OriginalWord>?>(null, reference) { value = withContext(Dispatchers.IO) { store.verses(reference.usfm, reference.chapter, from, to) } }
            var picked by remember(reference) { mutableStateOf<OriginalWord?>(null) }
            val w = words
            when {
                w == null -> CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.padding(8.dp))
                w.isEmpty() -> Text("This verse isn't in the ${pack.language} text (numbering can differ from English, notably in the Psalms).", fontSize = 14.sp, color = InkSecondary)
                else -> {
                    // Hebrew reads right to left; the words are laid out from the right.
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides if (hebrew) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag("original_words")) {
                            w.forEach { word ->
                                val on = picked == word
                                Surface(
                                    onClick = { picked = if (on) null else word }, shape = RoundedCornerShape(14.dp),
                                    color = if (on) AccentWash else SurfaceRaised, border = BorderStroke(if (on) 1.5.dp else 1.dp, if (on) Accent else Line)
                                ) {
                                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp).widthIn(min = 44.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                        Text(word.surface.replace("/", ""), fontSize = 20.sp, fontFamily = FontFamily.Serif, color = Ink, textAlign = TextAlign.Center)
                                        Text(word.translit, fontSize = 11.sp, color = InkMuted, textAlign = TextAlign.Center)
                                        Text(word.gloss, fontSize = 12.sp, color = InkSecondary, textAlign = TextAlign.Center, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                    picked?.let { WordDetail(it, hebrew, store) }
                    Text("Tap a word for its dictionary entry and grammar.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        running != null -> {
            val done = running.progress.getInt(OriginalsDownloadWorker.KEY_DONE, 0)
            val total = running.progress.getInt(OriginalsDownloadWorker.KEY_TOTAL, 0)
            Text(if (running.state == WorkInfo.State.ENQUEUED) "Waiting for Wi-Fi…" else "Downloading ${pack.label}… ${running.progress.getString(OriginalsDownloadWorker.KEY_LABEL).orEmpty()}", fontSize = 14.sp, color = InkSecondary)
            if (total > 0) LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Accent)
        }
        else -> Surface(shape = RoundedCornerShape(14.dp), color = SurfaceSunk, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("See the ${pack.language} behind this verse", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("Each word with its transliteration, grammar and a dictionary entry — from STEPBible's tagged text, free to keep on your phone (about ${pack.approxMb} MB, downloaded once, on Wi-Fi).", fontSize = 13.sp, lineHeight = 19.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp))
                if (work?.firstOrNull()?.state == WorkInfo.State.FAILED) Text(work?.firstOrNull()?.outputData?.getString(OriginalsDownloadWorker.KEY_ERROR) ?: "The last download stopped.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { OriginalsDownloadWorker.enqueue(context, pack, wifiOnly = true) }, modifier = Modifier.testTag("original_download")) { Text("Download on Wi-Fi", color = Accent, fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = { OriginalsDownloadWorker.enqueue(context, pack, wifiOnly = false) }) { Text("Any network", color = InkSecondary) }
                }
            }
        }
    }
    Text(OriginalsPack.CREDIT, fontSize = 10.5.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun <T> kotlinx.coroutines.flow.Flow<T>.collectAsStateSafe(): androidx.compose.runtime.State<T?> =
    androidx.compose.runtime.produceState<T?>(null, this) { collect { value = it } }

@Composable
private fun WordDetail(word: OriginalWord, hebrew: Boolean, store: OriginalsStore) {
    val entries by produceState<List<LexiconEntry>>(emptyList(), word) {
        // A written word can be several parts (a prefix and a noun): each has its own entry.
        value = withContext(Dispatchers.IO) { word.strongsAll.mapNotNull { store.entry(it) }.distinctBy { it.strongs } }
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).background(SurfaceSunk, RoundedCornerShape(14.dp)).padding(14.dp).testTag("original_detail")) {
        Text(word.morph.let { if (hebrew) MorphDecoder.hebrew(it) else MorphDecoder.greek(it) }.ifBlank { word.morph }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
        Text("${word.strongs}  ·  ${word.morph}", fontSize = 11.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
        if (entries.isEmpty()) Text("No dictionary entry found for this word.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 8.dp))
        entries.forEach { e ->
            Text(e.lemma + "  " + e.translit, fontSize = 17.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 10.dp))
            Text(listOf(e.partOfSpeech, e.gloss).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.5.sp, color = InkSecondary)
            if (e.meaning.isNotBlank()) Text(e.meaning, fontSize = 13.5.sp, lineHeight = 20.sp, color = Ink, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Settings: one row per original-language pack — get it, or remove it to free the space. */
@Composable
fun OriginalsPackRow(pack: OriginalsPack, row: @Composable (title: String, subtitle: String, onClick: () -> Unit) -> Unit) {
    val context = LocalContext.current
    val store = remember { OriginalsStore.get(context) }
    var version by remember { mutableStateOf(0) }
    val installed by produceState(false, version) { value = withContext(Dispatchers.IO) { pack in store.installed() } }
    val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(OriginalsDownloadWorker.name(pack)) }.collectAsStateSafe()
    val state = work?.firstOrNull()?.state
    androidx.compose.runtime.LaunchedEffect(state) { if (state == WorkInfo.State.SUCCEEDED) version++ }
    when {
        installed -> row("${pack.label} — on this phone", "Tap to remove it and free about ${pack.approxMb} MB") {
            Thread { store.remove(pack); WorkManager.getInstance(context).cancelUniqueWork(OriginalsDownloadWorker.name(pack)) }.start()
            version++
        }
        state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED -> row("${pack.label} — downloading…", "Tap to stop") { WorkManager.getInstance(context).cancelUniqueWork(OriginalsDownloadWorker.name(pack)) }
        else -> row("${pack.label}", "Word by word with grammar and a dictionary — about ${pack.approxMb} MB, on Wi-Fi") { OriginalsDownloadWorker.enqueue(context, pack, wifiOnly = true) }
    }
}
