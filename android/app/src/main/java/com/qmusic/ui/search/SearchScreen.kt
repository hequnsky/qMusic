package com.qmusic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qmusic.data.MusicRepository
import com.qmusic.data.model.Song
import com.qmusic.ui.components.SectionTitle
import com.qmusic.ui.components.SongRow
import com.qmusic.ui.theme.GradientEnd
import com.qmusic.ui.theme.GradientStart
import com.qmusic.ui.theme.TextPrimary
import com.qmusic.ui.theme.TextSecondary
import com.qmusic.ui.theme.TextTertiary

@Composable
fun SearchScreen(
    repository: MusicRepository,
    onPlaySong: (List<Song>, Int) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SearchViewModel(repository) }
        },
    )
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val searching by viewModel.searching.collectAsState()

    Column(Modifier.fillMaxSize().background(Color.White)) {
        // 搜索框
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 44.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("搜索", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Spacer(Modifier.width(14.dp))
            Row(
                modifier = Modifier.weight(1f)
                    .clip(CircleShape)
                    .background(Color(0xFFF2F2F5))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text("搜索歌曲、歌手、歌单", fontSize = 13.sp, color = Color(0xFFA9A9B1))
                            }
                            innerTextField()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "清空",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp).clickable { viewModel.onQueryChange("") },
                    )
                }
            }
        }

        if (query.isBlank()) {
            SearchHome(viewModel = viewModel)
        } else {
            SearchResultList(
                results = results,
                searching = searching,
                onPlaySong = onPlaySong,
            )
        }
    }
}

@Composable
private fun SearchHome(viewModel: SearchViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            SectionTitle(title = "搜索历史")
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                listOf("海阔天空", "Beyond", "纯音乐").forEach { keyword ->
                    Text(
                        keyword,
                        fontSize = 12.5.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFFF2F2F5))
                            .clickable { viewModel.onQueryChange(keyword) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
        item {
            SectionTitle(title = "热门搜索")
        }
        itemsIndexed(viewModel.hotKeywords) { index, keyword ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { viewModel.onQueryChange(keyword) }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (index < 3) GradientStart else TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(24.dp),
                )
                Text(
                    keyword,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (index == 0) {
                    Text(
                        "热",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd)))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultList(
    results: List<Song>,
    searching: Boolean,
    onPlaySong: (List<Song>, Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (searching && results.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = GradientStart,
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
            item {
                Text(
                    "找到 ${results.size} 首相关歌曲",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            itemsIndexed(results, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { onPlaySong(results, index) },
                )
            }
            if (results.isEmpty() && !searching) {
                item {
                    Text(
                        "未找到相关歌曲，换个关键词试试～",
                        fontSize = 13.sp,
                        color = TextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                    )
                }
            }
        }
    }
}