package info.lwb.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import info.lwb.core.common.Result
import info.lwb.core.model.Article
import info.lwb.feature.reader.viewmodels.ReaderViewModel

@Composable
fun ReaderScreen(viewModel: ReaderViewModel = hiltViewModel()) {
    val articles by viewModel.articles.collectAsState()
    val articleContent by viewModel.articleContent.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { viewModel.refreshArticles() }) {
            Text("Refresh Articles")
        }

        when (articles) {
            is Result.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is Result.Success -> {
                val articleList = (articles as Result.Success<List<Article>>).data
                LazyColumn {
                    items(articleList) { article ->
                        Text(
                            text = article.title,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
            is Result.Error -> {
                Text("Error: ${(articles as Result.Error).throwable.message}")
            }
        }
    }
}

