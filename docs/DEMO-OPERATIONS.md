# Owner maintenance for the public demo

The application does not delete reports automatically. Public demo administrators cannot run maintenance or reset the database. This runbook is an owner-operated plan, not a record of an executed cleanup or restore drill. Existing sample and acceptance data are retained.

## Inspect first

The demo caps issues at 500 and comments at 100 per issue. Review capacity when a capacity warning appears or before planning a cleanup. Use the read-only script against the dedicated Issue Desk database:

```bash
PGSERVICE=issue_desk psql -X -v ON_ERROR_STOP=1 \
  -v preserve_through_id=9 -f scripts/demo-maintenance.sql
```

Store connection details in your private PostgreSQL service/pass files with permissions `0600`, outside the repository. Do not paste passwords into commands, screenshots, or tickets. `issue_desk` must identify the dedicated demo database, never another project's database.

The protected prefix `9` preserves the eight initial examples and the existing ISS-009 acceptance record. Reconfirm that boundary and any other examples to retain before a future cleanup. The script refuses a missing boundary, lists only IDs, status, and timestamps, and finishes with a rollback. It lists resolved reports inactive for more than 30 days as **candidates**, not automatic deletion targets. Open reports, recent activity, accounts, and schema are never candidates. Adding a comment updates a report's activity timestamp.

## Back up before considering deletion

Choose a private destination outside any Git checkout or publicly served folder:

```bash
umask 077
backup_dir="$HOME/PrivateBackups/issue-desk"
mkdir -p "$backup_dir"
backup_file="$backup_dir/issue-desk-$(date -u +%Y%m%dT%H%M%SZ).dump"
PGSERVICE=issue_desk pg_dump --format=custom --file="$backup_file"
pg_restore --list "$backup_file" > "$backup_file.contents.txt"
```

Use PostgreSQL client tools compatible with the deployed server version. Confirm the dump command succeeded and that the archive contains the schema, users, issues, comments, audit history, and sequences. Treat backups as private even though demonstration credentials are public. Listing an archive is not a restore test.

Before the first cleanup, restore that archive into a **local disposable PostgreSQL database** using `pg_restore --no-owner --no-privileges`, then compare table counts and inspect a known report, comment, and audit history. Point a local app only at that restored database to verify sign-in and report reads. Do not restore over the live demo. This rehearsal is not yet recorded as completed; it must pass before deletion. It does not require a paid provider backup or a new hosted database.

## Cleanup decision and recovery

Only proceed after the owner reviews the exact candidate IDs, the verified private backup, and the restore rehearsal. Use a short maintenance window that prevents concurrent writes. Recheck the candidate criteria inside one transaction; delete only explicitly approved reports and their dependent comments and audit events. The schema intentionally has no cascading deletion. Preserve all accounts, protected example/acceptance records, migrations, and sequence state. Roll back on any unexpected count or changed activity; do not broaden the candidate list silently.

There is deliberately no deletion script or reset endpoint in this repository. The maintenance command above cannot erase data. After an approved cleanup, confirm health, sign-in, role/ownership checks, and retained history before reopening the demo. Keep the pre-cleanup backup private until those checks pass and the owner chooses a retention period. If recovery is needed, stop writes and restore through the rehearsed recovery procedure, first validating the backup in a separate local database.

## Free hosting boundary

Keep the current Railway and Neon Free plans. Do not enable paid backups, scheduled workers, extra replicas, or continuous keep-alive requests. When hosting sleeps or its allowance is exhausted, the portfolio's recorded walkthrough remains available independently of the backend.
