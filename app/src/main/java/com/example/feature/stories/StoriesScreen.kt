package com.example.feature.stories

import android.app.Application
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.core.datastore.UserPreferencesManager
import com.example.core.share.BackgroundPack
import com.example.core.share.BackgroundSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class StoriesViewModel(app: Application) : AndroidViewModel(app) {
    private val _stories = MutableStateFlow<List<Story>?>(null)
    val stories: StateFlow<List<Story>?> = _stories.asStateFlow()

    init {
        viewModelScope.launch {
            val identity = UserPreferencesManager(app).preferencesFlow.first().identity
            _stories.value = runCatching { StoryBuilder(app).build(identity) }.getOrDefault(emptyList())
        }
    }

    fun seen(story: Story) = StoriesSeen.mark(getApplication(), story.kind)
}

private const val STORY_MS = 8_000

/** Today's stories, full screen (PLAN_V2 F4). Tap to move, hold to pause, swipe down to close. */
@Composable
fun StoriesScreen(
    viewModel: StoriesViewModel,
    startAt: StoryKind?,
    onClose: () -> Unit,
    onOpen: (StoryOpen) -> Unit,
    onShare: (Story) -> Unit
) {
    val stories by viewModel.stories.collectAsState()
    val list = stories
    when {
        list == null -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        list.isEmpty() -> Box(Modifier.fillMaxSize().background(Color(0xFF111827)).padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No stories yet today", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Stories come from your devotional, the Verse of the Day, your calendar and your recordings.", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Surface(onClick = onClose, shape = RoundedCornerShape(50), color = Color.White, modifier = Modifier.padding(top = 20.dp)) {
                    Text("Close", color = Color.Black, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        }
        else -> StoryPager(list, startAt, onClose, onOpen, onShare, viewModel::seen)
    }
}

@Composable
fun StoryPager(
    stories: List<Story>,
    startAt: StoryKind?,
    onClose: () -> Unit,
    onOpen: (StoryOpen) -> Unit,
    onShare: (Story) -> Unit,
    onSeen: (Story) -> Unit = {},
    autoAdvance: Boolean = true
) {
    var index by remember { mutableIntStateOf(stories.indexOfFirst { it.kind == startAt }.coerceAtLeast(0)) }
    var paused by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val progress = remember(index) { Animatable(0f) }
    val story = stories[index]

    LaunchedEffect(index) { onSeen(story) }
    LaunchedEffect(index, paused) {
        if (!autoAdvance || paused) { progress.stop(); return@LaunchedEffect }
        progress.animateTo(1f, tween(((1f - progress.value) * STORY_MS).toInt(), easing = LinearEasing))
        if (index < stories.size - 1) index++ else onClose()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .offset { IntOffset(0, dragY.coerceAtLeast(0f).roundToInt()) }
            .pointerInput(stories.size) {
                detectTapGestures(
                    onPress = { paused = true; tryAwaitRelease(); paused = false },
                    onTap = { o -> if (o.x < size.width / 3f) { if (index > 0) index-- } else { if (index < stories.size - 1) index++ else onClose() } }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        when {
                            dragY > 220f -> onClose()
                            dragY < -180f -> story.open?.let(onOpen)
                        }
                        dragY = 0f
                    },
                    onVerticalDrag = { _, d -> dragY += d }
                )
            }
            .testTag("stories")
    ) {
        StoryBackground(story.background)
        StoryBody(story)
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                stories.indices.forEach { i ->
                    val fill = when { i < index -> 1f; i == index -> progress.value; else -> 0f }
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxWidth(fill).height(3.dp).background(Color.White))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(story.eyebrow, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 6.dp))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            story.open?.let { o ->
                Surface(onClick = { onOpen(o) }, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
                    Text("${story.openLabel ?: "Open"}  ↑", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            if (story.share != null) Surface(onClick = { onShare(story) }, shape = RoundedCornerShape(50), color = Color.White) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun StoryBackground(bg: BackgroundSpec) {
    when (bg) {
        is BackgroundSpec.Photo -> Box(Modifier.fillMaxSize()) {
            AsyncImage(model = File(bg.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.7f)))))
        }
        is BackgroundSpec.Solid -> Box(Modifier.fillMaxSize().background(Color(bg.color)))
        is BackgroundSpec.Pack -> Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { BackgroundPack.draw(it.nativeCanvas, BackgroundPack.byId(bg.id), size.width.toInt(), size.height.toInt()) }
            }
            // Keep the top bar and the buttons readable on light skies.
            if (BackgroundPack.byId(bg.id).dark) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.3f), 0.2f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f)
            )))
        }
    }
}

@Composable
private fun StoryBody(story: Story) {
    val dark = (story.background as? BackgroundSpec.Pack)?.let { BackgroundPack.byId(it.id).dark } ?: true
    val ink = if (dark) Color.White else Color(0xFF0F172A)
    val accent = if (dark) Color(0xFFF6D365) else Color(0xFFB7791F)
    Column(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 120.dp), verticalArrangement = Arrangement.Center) {
        story.title?.let { Text(it, color = accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp)) }
        if (story.body.isNotBlank()) Text(
            if (story.quoted) "“${story.body.trim('“', '”')}”" else story.body,
            color = ink, fontSize = if (story.body.length < 160) 30.sp else if (story.body.length < 320) 25.sp else 21.sp,
            lineHeight = if (story.body.length < 160) 40.sp else 33.sp, fontFamily = FontFamily.Serif
        )
        story.lines.forEach { l ->
            Text(l, color = ink, fontSize = 20.sp, lineHeight = 26.sp, fontFamily = FontFamily.Serif, modifier = Modifier.padding(vertical = 6.dp))
        }
        story.footer?.let { Text(it, color = ink.copy(alpha = 0.65f), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 22.dp)) }
    }
}
