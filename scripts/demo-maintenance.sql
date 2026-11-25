-- Read-only inspection. No database credentials or report text are printed.
\set ON_ERROR_STOP on
\if :{?preserve_through_id}
\else
  \echo 'Required: -v preserve_through_id=N (review the protected example/acceptance IDs first)'
  \quit 1
\endif
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '5s';
SELECT COUNT(*) AS issues_used, 500 AS issue_capacity FROM issues;
SELECT COUNT(*) AS comments_total FROM comments;
SELECT issue_id, COUNT(*) AS comments_used, 100 AS comments_per_issue_capacity
FROM comments GROUP BY issue_id HAVING COUNT(*) >= 80 ORDER BY issue_id;
SELECT id, status, updated_at
FROM issues
WHERE id > :'preserve_through_id'::bigint
  AND :'preserve_through_id'::bigint >= 0
  AND status = 'RESOLVED'
  AND updated_at < CURRENT_TIMESTAMP - INTERVAL '30 days'
ORDER BY id;
ROLLBACK;
