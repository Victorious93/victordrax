package com.victorious.victordrax.findings

import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.db.Finding
import com.victorious.victordrax.db.ToolRun
import kotlinx.coroutines.flow.Flow

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    data class Rejected(val reason: String) : SaveResult
}

/**
 * Findings + tool-run repository (manual DI: construct with [AppDb.get]). Enforces the authorization
 * model: findings can only be created or edited for an engagement with a complete authorization record.
 */
class FindingsRepo(private val db: AppDb, private val clock: () -> Long = System::currentTimeMillis) {
    fun observe(engagementId: Long): Flow<List<Finding>> = db.findings().forEngagement(engagementId)
    suspend fun list(engagementId: Long): List<Finding> = db.findings().forEngagementOnce(engagementId)
    fun runs(engagementId: Long): Flow<List<ToolRun>> = db.toolRuns().forEngagement(engagementId)

    suspend fun save(f: Finding): SaveResult {
        if (f.title.isBlank()) return SaveResult.Rejected("A finding needs a title.")
        val e = db.engagements().get(f.engagementId) ?: return SaveResult.Rejected("Engagement not found.")
        if (!e.isAuthorized) return SaveResult.Rejected("Engagement \"${e.name}\" has no complete authorization record (who, date, document).")
        val clean = f.copy(title = f.title.trim(), tags = FindingLogic.normalizeTags(f.tags), target = f.target.trim(),
            severity = f.sev.label, status = f.state.name)
        return if (clean.id == 0L) SaveResult.Saved(db.findings().insert(clean))
        else { db.findings().update(clean.copy(updatedAt = clock())); SaveResult.Saved(clean.id) }
    }

    suspend fun delete(f: Finding) = db.findings().delete(f)

    /** Records a launched tool/workflow step against an engagement (only if authorized). Returns the run id or null. */
    suspend fun recordRun(engagementId: Long, label: String, command: String, source: String = "tool", target: String = ""): Long? {
        val e = db.engagements().get(engagementId) ?: return null
        if (!e.isAuthorized) return null
        return db.toolRuns().insert(ToolRun(engagementId = engagementId, label = label, command = command, source = source, target = target, startedAt = clock()))
    }

    companion object {
        /** Unsaved finding pre-filled from a tool/workflow run. */
        fun draftFromRun(r: ToolRun): Finding = Finding(
            engagementId = r.engagementId,
            title = "${r.label}: ",
            severity = "Info",
            target = r.target,
            tags = if (r.source.startsWith("workflow")) "workflow" else "tool",
            evidence = buildString {
                append("$ ").append(r.command)
                if (r.output.isNotBlank()) append("\n").append(r.output.trimEnd())
            },
            sourceRunId = r.id,
        )
    }
}
