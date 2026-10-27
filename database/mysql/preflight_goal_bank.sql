-- Read-only report for the existing V4, V5 or V6 database.
SELECT DATABASE() AS schema_name,VERSION() AS mysql_version;
SELECT table_name,column_name,column_type,is_nullable
FROM information_schema.columns WHERE table_schema=DATABASE()
AND table_name IN ('programs','program_levels','domains','skill_levels','goals','specific_goals','goal_criteria','plan_goals')
ORDER BY table_name,ordinal_position;
SELECT table_schema,table_name,column_name,referenced_table_name,referenced_column_name,constraint_name
FROM information_schema.key_column_usage WHERE referenced_table_schema=DATABASE()
AND referenced_table_name IN ('programs','program_levels','domains','skill_levels','goals','specific_goals')
ORDER BY table_name,constraint_name,ordinal_position;
SELECT 'programs' AS entity,COUNT(*) AS row_count FROM programs
UNION ALL SELECT 'domains',COUNT(*) FROM domains
UNION ALL SELECT 'goals',COUNT(*) FROM goals
UNION ALL SELECT 'specific_goals',COUNT(*) FROM specific_goals
UNION ALL SELECT 'goal_criteria',COUNT(*) FROM goal_criteria
UNION ALL SELECT 'plan_goals',COUNT(*) FROM plan_goals
UNION ALL SELECT 'progress_records',COUNT(*) FROM progress_records;
SET @has_legacy_levels=(SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='skill_levels');
SET @catalog_report=IF(@has_legacy_levels>0,
    'SELECT d.id AS domain_id,d.program_id,sl.id AS legacy_skill_level_id,g.id AS goal_id,g.code AS goal_code FROM domains d LEFT JOIN skill_levels sl ON sl.domain_id=d.id LEFT JOIN goals g ON g.skill_level_id=sl.id ORDER BY d.id,sl.id,g.id',
    'SELECT p.id AS program_id,pl.id AS level_id,pl.level_number,d.id AS domain_id,g.id AS goal_id,g.code AS goal_code FROM programs p LEFT JOIN program_levels pl ON pl.program_id=p.id LEFT JOIN domains d ON d.program_level_id=pl.id LEFT JOIN goals g ON g.domain_id=d.id ORDER BY p.id,pl.id,d.id,g.id');
PREPARE catalog_preflight FROM @catalog_report;
EXECUTE catalog_preflight;
DEALLOCATE PREPARE catalog_preflight;
