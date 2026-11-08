# Findings manager

Findings live under an engagement (**Engage → an engagement → Findings**).

## What a finding holds
- Title and description
- Severity (with severity chips)
- Status, tags and target
- Evidence file paths (attach via the file picker)

## Tool-run log
Tool and workflow runs are recorded in a `tool_runs` log. From a run you can spawn a **pre-filled finding draft**. Note: runs record the *command* used — tool stdout is not captured because VictorSuite sessions are interactive and do not send output back.

## Filtering
The findings screen supports severity chips, text search, filtering and sorting. Runs and findings recorded only for authorized engagements.
