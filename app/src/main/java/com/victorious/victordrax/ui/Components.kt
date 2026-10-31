package com.victorious.victordrax.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GradientTitle(text: String, size: Int = 26) {
    Text(text, style = TextStyle(brush = NeonGradient, fontSize = size.sp, fontWeight = FontWeight.Bold))
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, body: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth().padding(vertical = 6.dp).animateContentSize()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            body()
        }
    }
}

/** Card with an animated neon gradient wash and gradient border. */
@Composable
fun NeonCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surface).background(animatedGradient())
            .border(1.dp, NeonGradient, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun QuickTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    NeonCard(modifier.height(88.dp), onClick = onClick) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AuthorizedBanner() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha = .18f))
        .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(8.dp))
        Text("AUTHORIZED USE ONLY — only on systems you own or have written permission to test.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun LinkButton(label: String, url: String) {
    val ctx = LocalContext.current
    TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) { Text(label) }
}
