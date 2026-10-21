package com.craftflowtechnologies.meetingmind.feature.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.feature.circles2.InitialsAvatar
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** Pick one of my circles to send a card into its chat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareToCircleSheet(onPick: (Circle) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Circles2.repository(context) }
    val state by repo.circles().collectAsState(initial = CirclesListState.Loading)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Send to a circle", style = MM.type.title.copy(fontWeight = FontWeight.Bold), color = MM.colors.ink)
            Text("The card lands in that circle's chat.", style = MM.type.secondary, color = MM.colors.inkSecondary)
            when (val s = state) {
                is CirclesListState.Ready -> if (s.circles.isEmpty()) {
                    Text("You're not in a circle yet.", style = MM.type.body, color = MM.colors.inkSecondary, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(s.circles, key = { it.id }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                    .clickable { onPick(c) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                InitialsAvatar(c.name, size = 40.dp)
                                Text(c.name, style = MM.type.bodyStrong, color = MM.colors.ink)
                            }
                        }
                    }
                }
                CirclesListState.Loading -> Text("Loading your circles…", style = MM.type.body, color = MM.colors.inkSecondary, modifier = Modifier.padding(vertical = 16.dp))
                CirclesListState.NotConnected -> Text("Circles isn't connected yet.", style = MM.type.body, color = MM.colors.inkSecondary, modifier = Modifier.padding(vertical = 16.dp))
            }
        }
    }
}
