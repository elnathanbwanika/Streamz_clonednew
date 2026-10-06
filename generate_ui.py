movies_screen_kt = """package com.linkstreamz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun MoviesScreen(repository: MoviesRepository, onWatch: (Any) -> Unit) {
    val movies = repository.movies
    val series = repository.series

    LazyColumn(
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SectionTitle("Trending Movies")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(movies.take(6)) { movie ->
                    MovieCard(movie = movie, onClick = { onWatch(movie) })
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle("Trending Series")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(series) { s ->
                    SeriesCard(series = s, onClick = { onWatch(s) })
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = LinkColors.PrimaryText,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun MovieCard(movie: Movie, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .background(LinkColors.CardBg)
        ) {
            AsyncImage(
                model = movie.thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(movie.title, color = LinkColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${movie.genre} · ${movie.year}", color = LinkColors.MutedText, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun SeriesCard(series: Series, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .background(LinkColors.CardBg)
        ) {
            AsyncImage(
                model = series.thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(series.title, color = LinkColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${series.genre} · ${series.year}", color = LinkColors.MutedText, fontSize = 12.sp, maxLines = 1)
    }
}
"""

channels_screen_kt = """package com.linkstreamz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChannelsScreen(repository: ChannelsRepository, onWatch: (Channel) -> Unit) {
    val channels = repository.channels

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(channels) { channel ->
            ChannelCard(channel = channel, onClick = { onWatch(channel) })
        }
    }
}

@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(LinkColors.CardBg)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(channel.title, color = LinkColors.PrimaryText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(channel.program, color = LinkColors.MutedText, fontSize = 12.sp)
        }
    }
}
"""

with open('app/src/main/java/com/linkstreamz/app/MoviesScreen.kt', 'w') as f: f.write(movies_screen_kt)
with open('app/src/main/java/com/linkstreamz/app/ChannelsScreen.kt', 'w') as f: f.write(channels_screen_kt)
