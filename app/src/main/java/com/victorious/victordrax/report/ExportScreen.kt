package com.victorious.victordrax.report

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.victorious.victordrax.R
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.NeonCyan
import com.victorious.victordrax.ui.NeonMagenta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

private suspend fun loadReport(ctx: Context, id: Long): EngagementReportData? {
    val db = AppDb.get(ctx)
    val e = db.engagements().get(id) ?: return null
    return EngagementReportData(e, db.findings().forEngagementOnce(id), db.toolRuns().forEngagementOnce(id), db.engagements().notesOnce(id))
}

private fun writeTo(ctx: Context, d: EngagementReportData, pdf: Boolean, out: OutputStream) {
    if (pdf) EngagementReport.writePdf(d, out, logo = ContextCompat.getDrawable(ctx, R.drawable.victordrax_logo))
    else out.write(EngagementReport.markdown(d).toByteArray())
}

/** Engagement report: neon preview, save via SAF (.md / .pdf), share via FileProvider. */
@Composable
fun ReportExportScreen(id: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<EngagementReportData?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) { data = loadReport(ctx, id) }
    val d = data ?: run { Text("Loading…", Modifier.padding(16.dp)); return }
    val md = remember(d) { EngagementReport.markdown(d) }

    fun save(pdf: Boolean, uri: android.net.Uri?) {
        uri ?: return
        scope.launch {
            msg = runCatching {
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { writeTo(ctx, d, pdf, it) } ?: error("cannot open") }
                if (pdf) "PDF saved" else "Markdown saved"
            }.getOrElse { "Export failed: ${it.message}" }
        }
    }
    val saveMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { save(false, it) }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { save(true, it) }
    fun share(pdf: Boolean) = scope.launch {
        msg = runCatching {
            val f = withContext(Dispatchers.IO) {
                val dir = File(ctx.cacheDir, "reports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                File(dir, EngagementReport.fileName(d.engagement, if (pdf) "pdf" else "md")).also { f -> f.outputStream().use { writeTo(ctx, d, pdf, it) } }
            }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".reports", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = if (pdf) "application/pdf" else "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Engagement report: ${d.engagement.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        }.getOrElse { "Share failed: ${it.message}" }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp)); GradientTitle("Report: ${d.engagement.name}")
        Text("${d.findings.size} findings · ${d.runs.size} tool/workflow runs", color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Button({ saveMd.launch(EngagementReport.fileName(d.engagement, "md")) }) { Text("Save .md") }
            Button({ savePdf.launch(EngagementReport.fileName(d.engagement, "pdf")) }) { Text("Save .pdf") }
            OutlinedButton({ share(false) }) { Text("Share .md") }
            OutlinedButton({ share(true) }) { Text("Share .pdf") }
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .border(1.dp, Brush.linearGradient(listOf(NeonCyan, NeonMagenta)), RoundedCornerShape(12.dp))
                .padding(10.dp).verticalScroll(rememberScrollState())
        ) {
            SelectionContainer { Text(md, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
        }
        Spacer(Modifier.height(12.dp))
    }
}
