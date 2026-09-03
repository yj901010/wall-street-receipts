-- WSR_DATABASE_EVIDENCE_VERSION=2
\set ON_ERROR_STOP on

BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;

SELECT 'evidence_version|2';
SELECT 'database_name|' || current_database();
SELECT 'database_encoding|' || pg_encoding_to_char(encoding)
FROM pg_database
WHERE datname = current_database();

SELECT count(*) FILTER (WHERE NOT success) AS failed_flyway_migrations
FROM public.flyway_schema_history
\gset

\if :failed_flyway_migrations
  \echo 'ERROR: restored Flyway history contains an unsuccessful migration'
  \quit 3
\endif

SELECT 'flyway_successful_count|' || count(*)::text
FROM public.flyway_schema_history
WHERE success;

SELECT 'flyway_max_installed_rank|' || COALESCE(max(installed_rank), 0)::text
FROM public.flyway_schema_history
WHERE success;

SELECT 'flyway|' || installed_rank::text || '|' ||
       COALESCE(version, 'null') || '|' ||
       encode(convert_to(description, 'UTF8'), 'hex') || '|' ||
       type || '|' ||
       encode(convert_to(script, 'UTF8'), 'hex') || '|' ||
       COALESCE(checksum::text, 'null') || '|' ||
       success::text
FROM public.flyway_schema_history
ORDER BY installed_rank;

SELECT 'platform_metadata|' || metadata_key || '|' || metadata_value
FROM public.platform_metadata
ORDER BY metadata_key;

SELECT 'analyst_calls|' || count(*)::text
FROM public.analyst_calls;

SELECT 'analyst_call_revisions|' || count(*)::text
FROM public.analyst_call_revisions;

SELECT 'call_outcomes|' || count(*)::text
FROM public.call_outcomes;

SELECT format(
           'SELECT %L || count(*)::text FROM %I.%I;',
           'table_rows|' || schemaname || '.' || tablename || '|',
           schemaname,
           tablename
       )
FROM pg_catalog.pg_tables
WHERE schemaname = 'public'
ORDER BY tablename
\gexec

COMMIT;
