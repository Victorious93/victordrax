package com.victorious.victordrax.manual

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.victordrax.data.Settings
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.NeonCard

/** Loads the manual manifest from assets; empty list if it can't be read/parsed. */
@Composable
private fun rememberManual(): List<ManualTopic> {
    val ctx = LocalContext.current
    return remember {
        runCatching {
            Manual.parse(ctx.assets.open("manual/manifest.json").bufferedReader().readText()).topics
        }.getOrDefault(emptyList())
    }
}

@Composable
private fun rememberBody(topic: ManualTopic): String {
    val ctx = LocalContext.current
    return remember(topic.id) {
        runCatching { ctx.assets.open(topic.assetPath).bufferedReader().readText() }
            .getOrDefault("# ${topic.title}\n\n${topic.summary}")
    }
}

@Composable
fun ManualScreen(settings: Settings, go: (String) -> Unit) {
    val topics = rememberManual()
    var query by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }

    val open = openId?.let { id -> topics.firstOrNull { it.id == id } }
    if (open != null) {
        ManualTopicPage(open, onBack = { openId = null }, go = go)
        return
    }

    val results = remember(topics, query) { Manual.search(topics, query) }
    val sections = remember(results) { Manual.sections(results) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        GradientTitle("Manual / Help")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search the manual") },
        )
        Spacer(Modifier.height(10.dp))
        if (results.isEmpty()) {
            Text("No topics match \"$query\".", color = MaterialTheme.colorScheme.onSurface)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sections.forEach { section ->
                    item(key = "sec-$section") {
                        Text(
                            section,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                    }
                    items(results.filter { it.section == section }, key = { it.id }) { t ->
                        ManualTocRow(t) { openId = t.id }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualTocRow(topic: ManualTopic, onClick: () -> Unit) {
    NeonCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(topic.title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(topic.summary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ManualTopicPage(topic: ManualTopic, onBack: () -> Unit, go: (String) -> Unit) {
    val body = rememberBody(topic)
    val blocks = remember(body) { Markdown.parse(body) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.width(4.dp))
            Text(topic.section, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        blocks.forEach { MarkdownBlock(it) }
        if (topic.route != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { go(topic.route) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OpenInNew, null)
                Spacer(Modifier.width(8.dp))
                Text("Open this screen")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MarkdownBlock(block: Markdown.Block) {
    when (block) {
        is Markdown.Block.Heading -> {
            val size = when (block.level) { 1 -> 24; 2 -> 19; else -> 16 }
            Spacer(Modifier.height(if (block.level == 1) 4.dp else 12.dp))
            Text(
                block.text,
                color = if (block.level == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                fontSize = size.sp,
            )
            Spacer(Modifier.height(4.dp))
        }
        is Markdown.Block.Paragraph -> {
            InlineText(block.text)
            Spacer(Modifier.height(8.dp))
        }
        is Markdown.Block.Bullet -> {
            Row(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                Text("•  ", color = MaterialTheme.colorScheme.primary)
                InlineText(block.text)
            }
        }
        is Markdown.Block.Numbered -> {
            Row(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                Text("${block.number}.  ", color = MaterialTheme.colorScheme.primary)
                InlineText(block.text)
            }
        }
    }
}

@Composable
private fun InlineText(text: String) {
    val ctx = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val baseColor = MaterialTheme.colorScheme.onSurface
    val spans = remember(text) { Markdown.parseInline(text) }
    val annotated = buildAnnotatedString {
        spans.forEach { s ->
            when (s) {
                is Markdown.Span.Text -> withStyle(SpanStyle(color = baseColor)) { append(s.text) }
                is Markdown.Span.Bold -> withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.Bold)) { append(s.text) }
                is Markdown.Span.Link -> {
                    pushStringAnnotation("url", s.url)
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) { append(s.text) }
                    pop()
                }
            }
        }
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(color = baseColor),
    ) { offset ->
        annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let { ann ->
            runCatching {
                ctx.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ann.item)),
                )
            }
        }
    }
}
