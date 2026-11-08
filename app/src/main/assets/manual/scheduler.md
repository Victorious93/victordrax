# Scan scheduler

Schedule a tool command or a workflow to run later or on a repeat, from **More → Scan scheduler**.

## Intervals
Run once, or recurring: hourly, every 6 hours, daily, or weekly. Scheduling uses Android **WorkManager**, with start and finish notifications.

## Scope is re-checked at run time
Scope and authorization are enforced both when you create a schedule **and** again each time a run fires. If the engagement is no longer authorized or the target is no longer in scope, that run is blocked and the schedule is switched off.

## Caveat
"Finished" only means the command was handed to VictorSuite. Because its sessions are interactive, the app cannot see the actual completion of the command.
