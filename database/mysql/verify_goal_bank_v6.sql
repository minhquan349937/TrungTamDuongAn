-- Read-only post-migration checks; every result must be PASS.
SELECT 'catalog_ids_varchar8' AS check_name,
    IF(COUNT(*)=5,'PASS','FAIL') AS result
FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name IN ('programs','program_levels','domains','goals','specific_goals')
    AND column_name='id' AND data_type='varchar' AND character_maximum_length=8
UNION ALL
SELECT 'plan_parent_required',IF(COUNT(*)=1,'PASS','FAIL')
FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='plan_goals'
    AND column_name='goal_id' AND is_nullable='NO' AND data_type='varchar' AND character_maximum_length=8
UNION ALL
SELECT 'plan_specific_optional',IF(COUNT(*)=1,'PASS','FAIL')
FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='plan_goals'
    AND column_name='specific_goal_id' AND is_nullable='YES' AND data_type='varchar' AND character_maximum_length=8
UNION ALL
SELECT 'specific_parent_composite_fk',IF(COUNT(*)=2,'PASS','FAIL')
FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() AND table_name='plan_goals'
    AND constraint_name='fk_plan_goal_specific_parent' AND referenced_table_name='specific_goals'
UNION ALL
SELECT 'legacy_columns_removed',IF(COUNT(*)=0,'PASS','FAIL')
FROM information_schema.columns WHERE table_schema=DATABASE()
    AND ((table_name='domains' AND column_name='program_id') OR (table_name='goals' AND column_name='skill_level_id'))
UNION ALL
SELECT 'legacy_table_removed',IF(COUNT(*)=0,'PASS','FAIL')
FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='skill_levels'
UNION ALL
SELECT 'snapshot_triggers_present',IF(COUNT(*)=2,'PASS','FAIL')
FROM information_schema.triggers WHERE trigger_schema=DATABASE()
    AND trigger_name IN ('plan_goals_snapshot_insert','plan_goals_snapshot_immutable')
UNION ALL
SELECT 'foreign_key_checks_enabled',IF(@@foreign_key_checks=1,'PASS','FAIL')
UNION ALL
SELECT 'id_generators_present',IF(COUNT(*)=5,'PASS','FAIL') FROM catalog_id_sequences
UNION ALL
SELECT 'invoice_view_preserved',IF(COUNT(*)=1,'PASS','FAIL')
FROM information_schema.views WHERE table_schema=DATABASE() AND table_name='invoice_balance'
UNION ALL
SELECT 'business_procedures_preserved',IF(COUNT(*)=2,'PASS','FAIL')
FROM information_schema.routines WHERE routine_schema=DATABASE()
    AND routine_name IN ('save_class_session','save_staff_salary');
