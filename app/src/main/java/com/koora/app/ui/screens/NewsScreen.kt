package com.koora.app.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koora.app.R
import com.koora.app.ads.AdBanner
import com.koora.app.ui.components.SkeletonList
import com.koora.app.data.NewsItem
import com.koora.app.data.NewsRepository
import kotlinx.coroutines.launch

/*
 * Football news tab — trilingual (EN / FR / AR) via the source registry.
 * Headlines only: title + source + region + relative time. Since v1.4.2
 * tapping opens the IN-APP ArticleReader (stays in Koora, banner keeps
 * running) instead of the browser. One failing feed never breaks the tab.
 */
class NewsViewModel : ViewModel() {
    var items by mutableStateOf<List<NewsItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    fun load(lang: String, force: Boolean = false) {
        if (!force && items.isNotEmpty()) return
        loading = true
        viewModelScope.launch {
            items = try {
                NewsRepository.news(lang)
            } catch (_: Exception) {
                emptyList()
            }
            loading = false
        }
    }
}

/** Resolve the news language: in-app override first, else the system locale. */
@Composable
fun currentNewsLang(): String {
    val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    val tag = if (locales.isEmpty) {
        androidx.core.os.LocaleListCompat.getDefault().toLanguageTags()
    } else {
        locales.toLanguageTags()
    }
    return when {
        tag.startsWith("ar") -> "ar"
        tag.startsWith("fr") -> "fr"
        else -> "en"
    }
}

@Composable
fun NewsScreen(
    onArticleClick: (NewsItem) -> Unit,
    onOpenSettings: () -> Unit = {},
    vm: NewsViewModel = viewModel()
) {
    val lang = currentNewsLang()
    var reloadTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(lang, reloadTick) { vm.load(lang, force = true) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            // v1.9.0 (spec §1.1): Settings gear top-right on every top screen.
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tab_settings))
            }
            IconButton(onClick = { reloadTick++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.news_refresh))
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                vm.loading && vm.items.isEmpty() -> SkeletonList(rows = 7, modifier = Modifier.fillMaxSize())

                vm.items.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.news_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.items, key = { it.link.ifEmpty { it.title } }) { item ->
                        NewsRow(item) {
                            if (item.link.isNotBlank()) onArticleClick(item)
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        AdBanner()
    }
}

@Composable
private fun NewsRow(item: NewsItem, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            item.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceBadge(item.source)
            Spacer(Modifier.width(6.dp))
            SourceBadge(item.region, soft = true)
            Spacer(Modifier.width(8.dp))
            Text(
                if (item.date > 0L) {
                    DateUtils.getRelativeTimeSpanString(
                        item.date,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                } else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SourceBadge(text: String, soft: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (soft) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (soft) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
