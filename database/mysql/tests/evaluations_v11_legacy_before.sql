-- Run ONLY against an isolated restore of the pre-V11 database.
SET @goal=(SELECT id FROM plan_goals ORDER BY id LIMIT 1);
SET @actor=(SELECT assigned_by FROM plan_goals WHERE id=@goal);
SET @student=(SELECT lp.student_id FROM learning_plans lp JOIN plan_goals pg ON pg.plan_id=lp.id WHERE pg.id=@goal);
INSERT INTO progress_records(id,plan_goal_id,observed_at,success_count,opportunity_count,notes,recorded_by)
VALUES('00000000-0000-0000-0000-000000011001',@goal,'2026-10-01',1,2,'Legacy 50%',@actor),
      ('00000000-0000-0000-0000-000000011002',@goal,'2026-10-02',4,5,'Legacy 80%',@actor),
      ('00000000-0000-0000-0000-000000011003',@goal,'2026-10-03',NULL,NULL,'Legacy without percentage',@actor);
INSERT INTO goal_criteria(id,specific_goal_id,measurement_type,target_percentage)
SELECT '00000000-0000-0000-0000-000000011004',id,'PERCENTAGE',80 FROM specific_goals ORDER BY id LIMIT 1;
INSERT INTO student_goal_bank(id,student_id,goal_id,goal_snapshot,selected_by)
SELECT '00000000-0000-0000-0000-000000011005',@student,goal_id,goal_snapshot,@actor FROM plan_goals WHERE id=@goal;
