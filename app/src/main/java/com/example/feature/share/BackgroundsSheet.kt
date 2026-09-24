package com.example.feature.share

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.share.BackgroundLibrary
import com.example.core.share.LibraryImage
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary

/**
 * Your backgrounds: the built-in photos and the ones you've added. Added ones are used for
 * stories, the devotional and share cards along with the built-ins; tap × to remove your own.
 * With [onPick], tapping a picture chooses it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundsSheet(onDismiss: () -> Unit, onPick: ((LibraryImage) -> Unit)? = null) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val images = remember(version) { BackgroundLibrary.all(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        uris.forEach { BackgroundLibrary.add(context, it) }
        if (uris.isNotEmpty()) version++
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text("Backgrounds", fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(horizontal = 22.dp))
            Text("Used for your stories, devotional and share cards. Add your own photos — they stay on your phone.", fontSize = 13.sp, lineHeight = 18.sp, color = InkSecondary, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().height(520.dp)
            ) {
                item {
                    Box(
                        Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFBF3E4)).border(1.dp, Color(0xFFE9D6B1), RoundedCornerShape(14.dp))
                            .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = Color(0xFFB7791F), modifier = Modifier.size(28.dp))
                            Text("Add yours", fontSize = 12.sp, color = Color(0xFF7A4E0F), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                items(images, key = { it.id }) { img ->
                    Box(Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp)).clickable(enabled = onPick != null) { onPick?.invoke(img) }) {
                        AsyncImage(model = img.thumb, contentDescription = img.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Text(img.name, fontSize = 10.5.sp, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1,
                            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 6.dp, vertical = 4.dp))
                        if (img.userAdded) Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                                .clickable { BackgroundLibrary.remove(img); version++ },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
                item(span = { GridItemSpan(3) }) {
                    Text("Built-in photos are from Unsplash and are used under the Unsplash licence. Photographers are credited in the app's notices.", fontSize = 11.sp, lineHeight = 15.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
