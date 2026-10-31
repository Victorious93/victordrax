package com.victorious.victordrax.backup

import androidx.room.withTransaction
import com.victorious.victordrax.db.AppDb

enum class RestoreMode { MERGE, REPLACE }

data class RestoreResult(val engagements: Int, val findings: Int, val notes: Int, val playbooks: Int)

/** Collects and restores [BackupBundle]s against Room. */
class BackupStore(private val db: AppDb) {
    suspend fun collect(): BackupBundle = BackupBundle(
        exportedAt = System.currentTimeMillis(),
        engagements = db.engagements().allOnce(),
        notes = db.engagements().allNotesOnce(),
        findings = db.findings().allOnce(),
        playbooks = db.playbooks().allOnce().filter { !it.builtIn },
        steps = db.playbooks().allStepsOnce(),
    )

    /**
     * REPLACE wipes engagements (cascading notes/findings/scans) and user playbooks, then inserts the
     * backup with original ids. MERGE inserts every backup engagement as a new row (ids remapped) so
     * nothing local is overwritten; user playbooks whose name already exists are skipped.
     */
    suspend fun restore(b: BackupBundle, mode: RestoreMode): RestoreResult = db.withTransaction {
        val e = db.engagements(); val f = db.findings(); val p = db.playbooks()
        val userPbIds = b.playbooks.filter { !it.builtIn }.map { it.id }.toSet()
        var pbCount = 0
        if (mode == RestoreMode.REPLACE) {
            e.deleteAll(); p.deleteUserPlaybooks()
            b.engagements.forEach { e.upsert(it) }
            b.notes.forEach { e.upsertNote(it) }
            b.findings.forEach { f.upsert(it) }
            b.playbooks.filter { !it.builtIn }.forEach { pb ->
                val newId = p.insert(pb.copy(id = 0)); pbCount++
                b.steps.filter { it.playbookId == pb.id }.forEach { s -> p.insertStep(s.copy(id = 0, playbookId = newId)) }
            }
        } else {
            val map = HashMap<Long, Long>()
            b.engagements.forEach { map[it.id] = e.insert(it.copy(id = 0)) }
            b.notes.forEach { n -> map[n.engagementId]?.let { e.addNote(n.copy(id = 0, engagementId = it)) } }
            b.findings.forEach { x -> map[x.engagementId]?.let { f.insert(x.copy(id = 0, engagementId = it)) } }
            val existing = p.allOnce().map { it.name }.toSet()
            b.playbooks.filter { it.id in userPbIds && it.name !in existing }.forEach { pb ->
                val newId = p.insert(pb.copy(id = 0)); pbCount++
                b.steps.filter { it.playbookId == pb.id }.forEach { s -> p.insertStep(s.copy(id = 0, playbookId = newId)) }
            }
        }
        RestoreResult(b.engagements.size, b.findings.size, b.notes.size, pbCount)
    }
}
