-- Read-only templates. Replace session variables with prepared statement parameters in Java.
SET NAMES utf8mb4;
SET time_zone='+00:00';

-- 1. Roster on a LOCAL calendar date supplied by the backend.
-- ENDED remains valid for historical dates; only CANCELLED is excluded entirely.
SELECT s.id,s.code,s.full_name
FROM student s
WHERE EXISTS (
    SELECT 1 FROM enrollment e WHERE e.student_id=s.id AND e.class_id=@class_id
    AND e.status<>'CANCELLED' AND e.started_on<=@local_session_date
    AND (e.ended_on IS NULL OR e.ended_on>@local_session_date)
)
ORDER BY s.full_name,s.id;

-- 2. Parent portal scope (plan/progress permissions are intentionally not granted by this query).
SELECT s.id,s.code,s.full_name
FROM guardian g JOIN app_user u ON u.id=g.user_id
JOIN student_guardian sg ON sg.guardian_id=g.id JOIN student s ON s.id=sg.student_id
WHERE u.id=@authenticated_user_id AND u.role='PARENT' AND u.status='ACTIVE'
  AND sg.revoked_at IS NULL AND sg.can_view_portal=1;

-- 3. Denver V8: main goals only, never join specific_goals.
SELECT p.code program_code,pl.name level_name,d.name domain_name,g.code,g.name,g.description
FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id
JOIN domains d ON d.id=ld.domain_id JOIN program_levels pl ON pl.id=ld.program_level_id
JOIN programs p ON p.id=ld.program_id
WHERE p.code='ESDM' AND p.is_active=1 AND d.is_active=1 AND g.is_active=1
ORDER BY pl.display_order,ld.display_order,g.display_order,g.id;

-- 3b. VB V8. Goals without children or criteria remain visible.
SELECT p.code AS program_code,pl.level_number,d.code AS domain_code,
       g.id AS goal_id,g.code AS goal_code,g.name,g.description AS goal_description,
       sg.id AS specific_goal_id,sg.code,sg.description,sg.expected_behavior,
       COALESCE(sg.age_min_months,pl.age_min_months) AS age_min_months,
       COALESCE(sg.age_max_months,pl.age_max_months) AS age_max_months,
       gc.measurement_type,gc.target_count,gc.observation_minutes,
       gc.required_periods,gc.period_unit,gc.adult_count,gc.setting_count
FROM programs p JOIN program_levels pl ON pl.program_id=p.id
JOIN program_level_domains ld ON ld.program_level_id=pl.id
JOIN domains d ON d.id=ld.domain_id JOIN goals g ON g.level_domain_id=ld.id
LEFT JOIN specific_goals sg ON sg.goal_id=g.id AND sg.is_active=1
LEFT JOIN goal_criteria gc ON gc.specific_goal_id=sg.id
WHERE p.code='VB' AND p.is_active=1 AND d.is_active=1 AND g.is_active=1
ORDER BY p.code,p.id,pl.display_order,pl.id,ld.display_order,d.id,g.display_order,g.id,sg.display_order,sg.id;

-- 4. Plan content MUST come from snapshots, not the latest master goal text.
SELECT pg.id,pg.status,pg.assigned_at,pg.supersedes_id,
       pg.goal_snapshot->>'$.description' AS assigned_description,
       pg.goal_snapshot,pg.criteria_snapshot
FROM plan_goals pg JOIN learning_plans lp ON lp.id=pg.plan_id
WHERE lp.student_id=@authorized_student_id AND lp.id=@plan_id
ORDER BY pg.assigned_at,pg.id;

-- 5. Observation history for the exact assigned version, including home observations.
SELECT pr.* FROM progress_records pr
JOIN plan_goals pg ON pg.id=pr.plan_goal_id JOIN learning_plans lp ON lp.id=pg.plan_id
WHERE lp.student_id=@authorized_student_id AND pg.id=@plan_goal_id
ORDER BY pr.observed_at,pr.id;

-- 6. Outstanding issued invoices; no draft/void invoices in debt totals.
SELECT i.code,b.billing_month,b.total_amount,b.paid_amount,b.outstanding_amount,b.payment_status
FROM invoice_balance b JOIN invoice i ON i.id=b.invoice_id
WHERE b.student_id=@authorized_student_id AND b.status='ISSUED' AND b.outstanding_amount>0
ORDER BY i.due_on,i.id;
