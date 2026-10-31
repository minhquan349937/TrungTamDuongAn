DELIMITER $$
CREATE PROCEDURE verify_legacy_evaluations_v11()
BEGIN
    IF (SELECT COUNT(*) FROM goal_evaluations WHERE id IN ('00000000-0000-0000-0000-000000011001','00000000-0000-0000-0000-000000011002','00000000-0000-0000-0000-000000011003'))<>3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy history lost';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM goal_evaluations WHERE id='00000000-0000-0000-0000-000000011001' AND attempt_no=1 AND achievement_percent=50 AND result_status='NOT_ACHIEVED')
       OR NOT EXISTS(SELECT 1 FROM goal_evaluations WHERE id='00000000-0000-0000-0000-000000011002' AND attempt_no=2 AND achievement_percent=80 AND result_status='ACHIEVED')
       OR NOT EXISTS(SELECT 1 FROM goal_evaluations WHERE id='00000000-0000-0000-0000-000000011003' AND attempt_no=3 AND achievement_percent IS NULL AND result_status='NOT_STARTED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incorrect legacy conversion';
    END IF;
    IF (SELECT COUNT(*) FROM migration_v11_archive WHERE source_table='progress_records')<>3
       OR (SELECT COUNT(*) FROM migration_v11_archive WHERE source_table='goal_criteria')<>1
       OR (SELECT COUNT(*) FROM migration_v11_archive WHERE source_table='student_goal_bank')<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing legacy archive';
    END IF;
    IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('student_goal_bank','goal_criteria','progress_records')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Obsolete tables remain';
    END IF;
    SELECT 'PASS: legacy attempts, percentages, unknown values and archives preserved' result;
END$$
CALL verify_legacy_evaluations_v11()$$
DROP PROCEDURE verify_legacy_evaluations_v11$$
DELIMITER ;
