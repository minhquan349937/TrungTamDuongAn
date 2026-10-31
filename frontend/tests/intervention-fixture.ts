import { expect, type Page } from "@playwright/test";
import type { GoalEvaluation } from "../src/services/goalEvaluationApi";
import type { PlanEvaluation } from "../src/services/planEvaluationApi";
import type {
  GoalSelection,
  GoalTarget,
  AssignedGoal,
} from "../src/services/goalBankApi";
import type {
  AgeOption,
  DomainGroup,
  GoalChoice,
  GoalLearningHistory,
  PlanDraft,
  PlanReview,
  PlanView,
  ProgramSummary,
  StudentSummary,
} from "../src/services/interventionApi";

export async function mockIntervention(page: Page) {
  const studentId = "00000000-0000-0000-0000-000000000001";
  const students: StudentSummary[] = [
    {
      id: studentId,
      code: "HS01",
      fullName: "Học sinh kiểm thử An",
      birthDate: "2023-01-01",
      status: "ACTIVE",
      ageMonths: 45,
      studyingGoals: 0,
      achievedGoals: 0,
    },
    {
      id: "00000000-0000-0000-0000-000000000002",
      code: "HS02",
      fullName: "Học sinh kiểm thử Bình",
      birthDate: "2024-01-01",
      status: "ACTIVE",
      ageMonths: 33,
      studyingGoals: 0,
      achievedGoals: 0,
    },
  ];
  const programs: ProgramSummary[] = [
    {
      id: "PR0001",
      code: "ESDM",
      name: "Denver",
      description: null,
      domainCount: 2,
      goalCount: 4,
      ageMinMonths: 12,
      ageMaxMonths: 36,
    },
    {
      id: "PR0002",
      code: "VB",
      name: "Ngôn ngữ và kỹ năng xã hội",
      description: null,
      domainCount: 1,
      goalCount: 2,
      ageMinMonths: 0,
      ageMaxMonths: 48,
    },
  ];
  const a1: AgeOption = {
    domainId: "DM0001", levelDomainId: "LD0001",
    levelId: "LV0001",
    levelName: "Cấp độ 1",
    ageMinMonths: 12,
    ageMaxMonths: 24,
  };
  const a2: AgeOption = {
    domainId: "DM0001", levelDomainId: "LD0002",
    levelId: "LV0002",
    levelName: "Cấp độ 2",
    ageMinMonths: 24,
    ageMaxMonths: 36,
  };
  const a3: AgeOption = { ...a1, domainId: "DM0003", levelDomainId: "LD0003" };
  const avb: AgeOption = {
    domainId: "DM0004", levelDomainId: "LD0004",
    levelId: "LV0003",
    levelName: "Cấp độ VB",
    ageMinMonths: 0,
    ageMaxMonths: 48,
  };
  const fields: Record<string, DomainGroup[]> = {
    PR0001: [
      { id: "DM0001", code: "LANG", name: "Ngôn ngữ", ages: [a1, a2] },
      { id: "DM0003", code: "COG", name: "Nhận thức", ages: [a3] },
    ],
    PR0002: [{ id: "DM0004", code: "VB", name: "Giao tiếp", ages: [avb] }],
  };
  const main = (
    id: string,
    code: string,
    name: string,
    hasSpecificGoals = false,
  ): GoalChoice => ({
    id,
    code,
    name,
    description: `Mô tả ${name}`,
    displayOrder: 1,
    hasSpecificGoals,
    canSelectGeneralGoal: true,
  });
  const targets: Record<string, GoalChoice[]> = {
    LD0001: [
      main("GL000001", "D.1", "Định vị âm thanh"),
      main("GL000002", "D.2", "Nhận biết đồ vật"),
    ],
    LD0002: [main("GL000003", "D.3", "Làm theo chỉ dẫn")],
    LD0003: [main("GL000004", "D.4", "Ghép hình")],
    LD0004: [
      main("GL000005", "VB.1", "Giao tiếp yêu cầu", true),
      main("GL000006", "VB.2", "Mục tiêu không có con"),
    ],
  };
  const parent = targets.LD0004[0];
  const children = [
    {
      id: "SG000001",
      code: "VB.1A",
      description: "Giao tiếp bằng mắt",
      ageMinMonths: 0,
      ageMaxMonths: 24,
      inheritedAge: false,
      displayOrder: 1,
    },
    {
      id: "SG000002",
      code: "VB.1B",
      description: "Yêu cầu bằng lời",
      ageMinMonths: 24,
      ageMaxMonths: 48,
      inheritedAge: false,
      displayOrder: 2,
    },
  ];
  const options = new Map<string, GoalSelection>();
  for (const program of programs)
    for (const group of fields[program.id])
      for (const age of group.ages)
        for (const goal of targets[age.levelDomainId]) {
          const location = {
            programId: program.id,
            programName: program.name,
            domainId: age.domainId,
            domainName: group.name,
            levelId: age.levelId,
            levelName: age.levelName,
            ageMinMonths: age.ageMinMonths,
            ageMaxMonths: age.ageMaxMonths,
          };
          options.set(goal.id, {
            goalId: goal.id,
            specificGoalId: null,
            targetType: "GOAL",
            code: goal.code,
            name: goal.name,
            description: goal.description,
            location,
            ageMinMonths: age.ageMinMonths,
            ageMaxMonths: age.ageMaxMonths,
          });
          if (goal.id === parent.id)
            for (const child of children)
              options.set(child.id, {
                goalId: goal.id,
                specificGoalId: child.id,
                targetType: "SPECIFIC_GOAL",
                code: child.code,
                name: goal.name,
                description: child.description,
                location,
                ageMinMonths: child.ageMinMonths,
                ageMaxMonths: child.ageMaxMonths,
              });
        }
  const state = {
    plans: [] as PlanView[],
    history: [] as GoalLearningHistory[],
    previewCalls: 0,
    completeCalls: 0,
    childRequests: 0,
    loseNextResponse: false,
    evaluations: [] as GoalEvaluation[],
    monthlyReports: [] as PlanEvaluation[],
  };
  page.on("pageerror", (error) => {
    throw error;
  });
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    let data: unknown = {};
    let status = 200;
    if (path === "/api/auth/me")
      data = {
        id: "actor",
        email: "admin@example.invalid",
        displayName: "Test admin",
        role: "ADMIN",
      };
    else if (path === "/api/auth/csrf") data = { token: "csrf" };
    else if (path === "/api/programs") data = programs;
    else if (path === "/api/programs/PR0001/levels") data = [{ id: "LV0001", programId: "PR0001", levelNumber: 1, name: "Cấp độ 1", ageMinMonths: 12, ageMaxMonths: 24, displayOrder: 1 }];
    else if (path === "/api/levels/LV0001/domains") data = [{ levelDomainId: "LD0001", id: "DM0001", programLevelId: "LV0001", code: "LANG", name: "Ngôn ngữ", description: null, displayOrder: 1 }];
    else if (path === "/api/level-domains/LD0001/goals") data = { content: targets.LD0001, page: 0, size: 20, totalElements: targets.LD0001.length, totalPages: 1 };
    else if (path === "/api/intervention/students") data = students;
    else if (path === `/api/intervention/students/${studentId}`)
      data = { student: students[0], plans: state.plans.map(row => ({ ...row, plan: { ...row.plan,
        createdAt: row.plan.createdAt ?? "2026-10-01T08:00:00",
        hasEvaluation: state.monthlyReports.some(r => r.learningPlanId === row.plan.id),
        sentToParent: state.monthlyReports.some(r => r.learningPlanId === row.plan.id && r.sentToParent),
      } })) };
    else if (path === `/api/intervention/students/${students[1].id}`)
      data = { student: students[1], plans: [] };
    else if (path === "/api/intervention/programs") data = programs;
    else if (/^\/api\/intervention\/programs\/[^/]+\/domains$/.test(path))
      data = fields[path.split("/")[4]];
    else if (/^\/api\/intervention\/students\/[^/]+\/level-domains\/[^/]+\/targets$/.test(path)) {
      const domain = path.split("/")[6];
      const history = path.split("/")[4] === studentId ? state.history : [];
      const achieved = new Set(history.filter(h => h.resultStatus === "ACHIEVED").map(h => h.specificGoalId ? `S:${h.specificGoalId}` : `G:${h.goalId}`));
      data = {
        supportsSpecificGoals: domain === "LD0004",
        goals: targets[domain].map(g => ({
          ...g,
          canSelectGeneralGoal: !achieved.has(`G:${g.id}`),
          hasSpecificGoals: g.hasSpecificGoals && children.some(c => !achieved.has(`S:${c.id}`)),
        })).filter(g => g.canSelectGeneralGoal || g.hasSpecificGoals),
        history: history.filter(h => targets[domain].some(g => g.id === h.goalId)),
      };
    }
    else if (/^\/api\/intervention\/level-domains\/[^/]+\/targets$/.test(path))
      data = {
        supportsSpecificGoals: path.includes("LD0004"),
        goals: targets[path.split("/")[4]],
      };
    else if (path === `/api/goals/${parent.id}`) {
      state.childRequests++;
      data = {
        ...parent,
        supportsSpecificGoals: true,
        canSelectGeneralGoal: true,
        location: options.get(parent.id)!.location,
        specificGoals: children,
      };
    } else if (/^\/api\/goals\/[^/]+$/.test(path)) {
      const selected = options.get(path.split("/")[3])!;
      data = { ...targets.LD0001.find(g => g.id === selected.goalId), supportsSpecificGoals: false,
        canSelectGeneralGoal: true, location: selected.location, specificGoals: [] };
    } else if (
      path === `/api/intervention/students/${studentId}/preview` &&
      method === "POST"
    ) {
      state.previewCalls++;
      const draft = route.request().postDataJSON() as PlanDraft;
      expect(draft.requestId).toMatch(/^[a-f\d-]{36}$/);
      const items = draft.selections.map((row: GoalTarget) =>
        options.get(row.specificGoalId ?? row.goalId)!,
      );
      expect(items.every(Boolean)).toBe(true);
      data = {
        ...draft,
        items,
        total: items.length,
        reviewToken: "a".repeat(64),
      } satisfies PlanReview;
    } else if (
      path === `/api/intervention/students/${studentId}/complete` &&
      method === "POST"
    ) {
      state.completeCalls++;
      const body = route.request().postDataJSON() as PlanDraft & {
        reviewToken: string;
      };
      expect(body.reviewToken).toBe("a".repeat(64));
      expect(
        body.selections.every(
          (row) =>
            Object.keys(row).sort().join(",") === "goalId,specificGoalId",
        ),
      ).toBe(true);
      let saved = state.plans.find(({ plan }) => plan.id === body.requestId);
      const alreadySaved = !!saved;
      if (!saved) {
        saved = {
          plan: {
            id: body.requestId,
            studentId,
            name: body.name,
            startDate: body.startDate,
            status: "ACTIVE",
          },
          goals: body.selections.map((target, i): AssignedGoal => {
            const selected = options.get(
              target.specificGoalId ?? target.goalId,
            )!;
            return {
              ...target,
              id: `assignment-${i}`,
              planId: body.requestId,
              targetType: selected.targetType,
              targetId: selected.specificGoalId ?? selected.goalId,
              status: "NOT_STARTED",
              achievementPercent: null,
              criteriaSnapshot: {},
              goalSnapshot: {
                code: selected.code,
                name: selected.specificGoalId ? selected.description! : selected.name,
                goal_name: selected.name,
                goal_code: selected.goalId,
                level_name: selected.location.levelName,
                description: selected.description,
                target_type: selected.targetType,
                domain_id: selected.location.domainId,
                domain_name: selected.location.domainName,
                program_id: selected.location.programId,
                program_name: selected.location.programName,
                program_level_id: selected.location.levelId,
                age_min_months: selected.ageMinMonths,
                age_max_months: selected.ageMaxMonths,
              },
            };
          }),
        };
        state.plans.push(saved);
        students[0].studyingGoals += saved.goals.length;
      }
      if (state.loseNextResponse) {
        state.loseNextResponse = false;
        status = 500;
        data = { message: "Thử lại yêu cầu hoàn thành." };
      } else data = { result: saved, alreadySaved };
    } else if (/^\/api\/learning-plans\/[^/]+\/evaluations$/.test(path)) {
      const planId = path.split("/")[3];
      if (method === "POST") {
        const plan = state.plans.find(p => p.plan.id === planId)!.plan;
        const row: PlanEvaluation = {
          id: `report-${state.monthlyReports.length + 1}`, learningPlanId: planId,
          planName: plan.name, studentId, studentName: students[0].fullName,
          evaluationNotes: route.request().postDataJSON().evaluationNotes,
          evaluatedBy: "actor", evaluatedByName: "Test admin", evaluatedAt: "2026-10-28T08:00:00",
          sentToParent: false, sentAt: null, updatedAt: "2026-10-28T08:00:00",
        };
        state.monthlyReports.unshift(row); data = row; status = 201;
      } else data = state.monthlyReports.filter(r => r.learningPlanId === planId);
    } else if (/^\/api\/plan-evaluations\/[^/]+(?:\/send)?$/.test(path)) {
      const report = state.monthlyReports.find(r => r.id === path.split("/")[3])!;
      if (method === "PUT") report.evaluationNotes = route.request().postDataJSON().evaluationNotes;
      if (method === "POST") { report.sentToParent = true; report.sentAt = "2026-10-29T08:00:00"; }
      data = report;
    } else if (/^\/api\/plans\/[^/]+\/goals\/[^/]+\/replace$/.test(path) && method === "PUT") {
      const planId = path.split("/")[3], oldId = path.split("/")[5];
      const target = route.request().postDataJSON() as GoalTarget;
      const selected = options.get(target.specificGoalId ?? target.goalId)!;
      const plan = state.plans.find(p => p.plan.id === planId)!;
      plan.goals = plan.goals.map(row => row.id === oldId ? {
        ...row, id: `replacement-${oldId}`, goalId: target.goalId, specificGoalId: target.specificGoalId,
        targetId: target.specificGoalId ?? target.goalId, targetType: selected.targetType,
        status: "NOT_STARTED", achievementPercent: null, goalSnapshot: { ...row.goalSnapshot, code: selected.code, name: selected.name,
          description: selected.description, goal_name: selected.name, goal_code: selected.code },
      } : row);
      data = plan.goals.find(row => row.id === `replacement-${oldId}`);
    } else if (/^\/api\/plans\/[^/]+\/status$/.test(path) && method === "PATCH") {
      const planId = path.split("/")[3], next = route.request().postDataJSON().status;
      if (next === "COMPLETED" && !state.monthlyReports.some(r => r.learningPlanId === planId)) {
        status = 409; data = { message: "Cần lưu đánh giá cả kế hoạch trước khi hoàn thành kế hoạch." };
      } else {
        const plan = state.plans.find(row => row.plan.id === planId)!.plan;
        plan.status = next;
        if (next === "COMPLETED") plan.endDate = "2026-10-29";
        status = 204;
      }
    } else if (/^\/api\/plans\/[^/]+$/.test(path) && method === "PATCH") {
      Object.assign(state.plans.find(row => row.plan.id === path.split("/")[3])!.plan,route.request().postDataJSON());
      status = 204;
    } else if (/^\/api\/plans\/[^/]+\/goals\/[^/]+\/evaluations(?:\/[^/]+)?$/.test(path)) {
      const goalId = path.split("/")[5];
      const input = method === "GET" ? null : route.request().postDataJSON();
      if (input) {
        const old = method === "PUT" ? state.evaluations.find(r => r.id === path.split("/")[7]) : undefined;
        const row: GoalEvaluation = {
          id: old?.id ?? `evaluation-${state.evaluations.length + 1}`,
          planGoalId: goalId, attemptNo: old?.attemptNo ?? state.evaluations.filter(r => r.planGoalId === goalId).length + 1,
          ...input, resultStatus: input.achievementPercent === null ? "NOT_STARTED" : input.achievementPercent < 80 ? "NOT_ACHIEVED" : "ACHIEVED",
          evaluatedAt: "2026-10-28T08:00:00", createdAt: "2026-10-28T08:00:00", updatedAt: "2026-10-28T08:00:00", evaluatedBy: "actor", evaluatedByName: "Test admin",
        };
        if (old) Object.assign(old, row); else state.evaluations.unshift(row);
        const latest = state.evaluations.filter(r => r.planGoalId === goalId).sort((a,b) => b.attemptNo-a.attemptNo)[0];
        const assignedGoal = state.plans.flatMap(p => p.goals).find(g => g.id === goalId)!;
        assignedGoal.status = latest.resultStatus;
        assignedGoal.achievementPercent = latest.achievementPercent;
      }
      data = state.evaluations.filter(r => r.planGoalId === goalId).sort((a,b) => b.attemptNo-a.attemptNo);
    } else status = 404;
    await route.fulfill({
      status,
      contentType: "application/json",
      body: status === 204 ? "" : JSON.stringify(data),
    });
  });
  return { state, studentId, students };
}
