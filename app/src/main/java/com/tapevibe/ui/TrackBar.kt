package com.tapevibe.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapevibe.model.Track
import com.tapevibe.ui.components.PeakMeter
import com.tapevibe.ui.components.ToggleChip
import com.tapevibe.ui.theme.Tv

interface TrackActions {
    fun select(id: Long)
    fun mute(id: Long)
    fun solo(id: Long)
    fun rename(id: Long)
    fun duplicate(id: Long)
    fun move(id: Long, delta: Int)
    fun recolor(id: Long)
    fun delete(id: Long)
    fun add()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackBar(tracks: List<Track>, selectedId: Long?, live: LiveState, actions: TrackActions) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedId, tracks.size) {
        val idx = tracks.indexOfFirst { it.id == selectedId }
        if (idx >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == idx && it.offset >= 0 &&
                it.offset + it.size <= listState.layoutInfo.viewportEndOffset }
        ) listState.animateScrollToItem(idx)
    }
    LazyRow(
        Modifier.fillMaxWidth().height(64.dp),
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { slot, t ->
            var menu by remember { mutableStateOf(false) }
            val color = Tv.trackColor(t.colorIndex)
            val selected = t.id == selectedId
            Box {
                Row(
                    Modifier
                        .width(128.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Tv.SurfaceHigh else Tv.Surface)
                        .border(1.5.dp, if (selected) color else Tv.Outline, RoundedCornerShape(10.dp))
                        .combinedClickable(onClick = { actions.select(t.id) }, onLongClick = { menu = true }),
                ) {
                    Box(Modifier.width(5.dp).fillMaxHeight().background(color))
                    Column(Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp)) {
                        Text(t.name, fontSize = 13.sp, color = Tv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ToggleChip("M", t.mute, Tv.Accent, width = 24.dp) { actions.mute(t.id) }
                            ToggleChip("S", t.solo, Tv.Yellow, width = 24.dp) { actions.solo(t.id) }
                            Text(t.type.label, fontSize = 9.sp, color = Tv.TextDim, maxLines = 1)
                        }
                        PeakMeter(
                            { live.trackLevel(slot, false) }, { live.trackLevel(slot, true) },
                            Modifier.fillMaxWidth().height(5.dp).padding(top = 1.dp), vertical = false,
                        )
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf<Pair<String, () -> Unit>>(
                        "Rename" to { actions.rename(t.id) },
                        "Duplicate" to { actions.duplicate(t.id) },
                        "Move left" to { actions.move(t.id, -1) },
                        "Move right" to { actions.move(t.id, 1) },
                        "Change colour" to { actions.recolor(t.id) },
                        "Delete" to { actions.delete(t.id) },
                    ).forEach { (label, f) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; f() })
                    }
                }
            }
        }
        item {
            Box(
                Modifier
                    .size(56.dp, 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Tv.Outline, RoundedCornerShape(10.dp))
                    .combinedClickable(onClick = actions::add),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, "Add track", tint = Tv.Accent)
            }
        }
    }
}
