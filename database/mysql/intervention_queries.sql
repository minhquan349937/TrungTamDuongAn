-- V8: SELECT templates for the five-page intervention workflow.
-- These are Workbench examples. Backend uses bound parameters, not string concatenation.
SET NAMES utf8mb4;
USE duong_an_v11;
SET @student_id = NULL;     -- Existing student UUID
SET @program_id = 'PR0001'; -- Select a program from query 3
SET @level_domain_id = NULL; -- Select an LD from query 4a; never pass a DM here
SET @goal_id = NULL;         -- A VB main goal from query 4b
SET @plan_id = NULL;         -- A saved learning plan UUID

-- 1. All student profiles, including students with no plan.
-- Counts are unique learning targets: main goal for Denver, specific goal for VB.
-- Exclude cancelled plans and superseded versions without hiding zero-count students.
SELECT ROW_NUMBER() OVER(ORDER BY s.code,s.id) stt,s.id,s.code,s.full_name,
       GREATEST(0,TIMESTAMPDIFF(MONTH,s.birth_date,DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR))) age_months,
       COALESCE(t.studying,0) studying_goals,COALESCE(t.achieved,0) achieved_goals
FROM student s
LEFT JOIN (
 SELECT lp.student_id,
        COUNT(DISTINCT CASE WHEN lp.status='ACTIVE' AND pg.status IN('NOT_STARTED','IN_PROGRESS')
            THEN IF(pg.specific_goal_id IS NULL,CONCAT('G:',pg.goal_id),CONCAT('S:',pg.specific_goal_id)) END) studying,
        COUNT(DISTINCT CASE WHEN pg.status='ACHIEVED'
            THEN IF(pg.specific_goal_id IS NULL,CONCAT('G:',pg.goal_id),CONCAT('S:',pg.specific_goal_id)) END) achieved
 FROM learning_plans lp JOIN plan_goals pg ON pg.plan_id=lp.id
 WHERE lp.status<>'CANCELLED'
   AND NOT EXISTS(SELECT 1 FROM plan_goals newer WHERE newer.supersedes_id=pg.id)
 GROUP BY lp.student_id
) t ON t.student_id=s.id
ORDER BY s.code,s.id;

-- 2. Student workspace. The backend assembles five tabs from these results.
-- Current: ACTIVE/DRAFT plans. History: all plans.
SELECT id,student_id,name,start_date,status FROM learning_plans
WHERE student_id=@student_id ORDER BY start_date DESC,id;
-- Goal bank: stored bank snapshots plus unique targets in non-cancelled plans.
SELECT id,goal_snapshot,created_at FROM student_goal_bank
WHERE student_id=@student_id ORDER BY created_at,id;
-- Completed/paused tabs: filter pg.status='ACHIEVED' / 'PAUSED' from this query.
SELECT pg.* FROM plan_goals pg JOIN learning_plans lp ON lp.id=pg.plan_id
WHERE lp.student_id=@student_id AND lp.status<>'CANCELLED'
 AND NOT EXISTS(SELECT 1 FROM plan_goals newer WHERE newer.supersedes_id=pg.id)
ORDER BY pg.assigned_at,pg.id;

-- 3. Program cards. Aggregate independently before joins to avoid multiplied counts.
-- goal_count counts main goals; specific goals do not inflate this number.
SELECT p.id,p.code,p.name,p.description,COALESCE(dc.total,0) domain_count,
       COALESCE(gc.total,0) goal_count,a.age_min,a.age_max
FROM programs p
LEFT JOIN (SELECT program_id,COUNT(*) total FROM domains WHERE is_active=1 GROUP BY program_id) dc ON dc.program_id=p.id
LEFT JOIN (
 SELECT ld.program_id,COUNT(*) total FROM goals g
 JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id
 WHERE g.is_active=1 AND d.is_active=1 GROUP BY ld.program_id
) gc ON gc.program_id=p.id
LEFT JOIN (
 SELECT program_id,MIN(age_min_months) age_min,MAX(age_max_months) age_max
 FROM program_levels GROUP BY program_id
) a ON a.program_id=p.id
WHERE p.is_active=1 ORDER BY p.code,p.id;

-- 4a. Group these rows by domain_id. Each field has independent age/level options.
-- DM identifies a shared field; LD identifies the exact level-field pair used to fetch goals.
SELECT d.id domain_id,d.code,d.name,ld.id level_domain_id,
       pl.id level_id,pl.name level_name,pl.age_min_months,pl.age_max_months
FROM domains d JOIN program_level_domains ld ON ld.domain_id=d.id
JOIN program_levels pl ON pl.id=ld.program_level_id JOIN programs p ON p.id=ld.program_id
WHERE p.id=@program_id AND p.is_active=1 AND d.is_active=1
ORDER BY d.display_order,d.id,pl.display_order,pl.id,ld.display_order,ld.id;

-- 4b. Denver: execute only this query; no specific_goals lookup or child selector.
SELECT g.id,g.code,g.name,g.description,g.display_order
FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id
JOIN domains d ON d.id=ld.domain_id JOIN programs p ON p.id=ld.program_id
WHERE ld.id=@level_domain_id AND p.is_active=1 AND d.is_active=1 AND g.is_active=1
ORDER BY g.display_order,g.id;

-- 4c. VB only: query children when opening a main goal. Allow main selection if none exist.
-- Backend skips this query completely for Denver.
SELECT sg.id,sg.goal_id,sg.code,sg.description,
       COALESCE(sg.age_min_months,pl.age_min_months) age_min_months,
       COALESCE(sg.age_max_months,pl.age_max_months) age_max_months,sg.display_order
FROM specific_goals sg JOIN goals g ON g.id=sg.goal_id
JOIN program_level_domains ld ON ld.id=g.level_domain_id
JOIN program_levels pl ON pl.id=ld.program_level_id
WHERE sg.goal_id=@goal_id AND sg.is_active=1
ORDER BY sg.display_order,sg.id;

-- 5. Preview uses selected goal IDs validated by backend, ordered by
-- domain.display_order, domain.id, level.display_order, level.id,
-- goal.display_order, goal.id, then specific_goal.display_order/specific_goal.id.
-- POST /api/intervention/students/{id}/preview does NOT save anything.
-- POST /api/intervention/students/{id}/complete locks the student and saves
-- learning_plans + plan_goals in ONE transaction. Do not replace it with raw FE INSERTs.

-- Read the saved outline from immutable snapshots, not the latest catalog text.
SELECT pg.id,pg.goal_id,pg.specific_goal_id,pg.status,
       pg.goal_snapshot->>'$.program_name' program_name,
       pg.goal_snapshot->>'$.program_level_id' level_id,
       pg.goal_snapshot->>'$.domain_id' domain_id,
       pg.goal_snapshot->>'$.domain_name' domain_name,
       pg.goal_snapshot->>'$.code' target_code,
       pg.goal_snapshot->>'$.name' target_name,
       pg.goal_snapshot->>'$.description' target_description
FROM plan_goals pg JOIN learning_plans lp ON lp.id=pg.plan_id
WHERE lp.student_id=@student_id AND lp.id=@plan_id
 AND NOT EXISTS(SELECT 1 FROM plan_goals newer WHERE newer.supersedes_id=pg.id)
ORDER BY pg.assigned_at,pg.id;
