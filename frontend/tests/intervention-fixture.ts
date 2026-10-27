import { expect, type Page } from "@playwright/test";
import type {
  GoalSelection,
  GoalTarget,
  AssignedGoal,
} from "../src/services/goalBankApi";
import type {
  AgeOption,
  DomainGroup,
  GoalChoice,
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
    canSelectGeneralGoal: !hasSpecificGoals,
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
    previewCalls: 0,
    completeCalls: 0,
    childRequests: 0,
    loseNextResponse: false,
    observations: [] as { id: string; observedAt: string; notes: string; successCount: number | null; opportunityCount: number | null }[],
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
    else if (path === "/api/intervention/students") data = students;
    else if (path === `/api/intervention/students/${studentId}`)
      data = { student: students[0], plans: state.plans, bank: [] };
    else if (path === `/api/intervention/students/${students[1].id}`)
      data = { student: students[1], plans: [], bank: [] };
    else if (path === "/api/intervention/programs") data = programs;
    else if (/^\/api\/intervention\/programs\/[^/]+\/domains$/.test(path))
      data = fields[path.split("/")[4]];
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
        canSelectGeneralGoal: false,
        location: options.get(parent.id)!.location,
        specificGoals: children,
      };
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
      expect(
        items.every((row) => row.location.programId === draft.programId),
      ).toBe(true);
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
              criteriaSnapshot: {},
              goalSnapshot: {
                code: selected.code,
                name: selected.name,
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
    } else if (/^\/api\/plans\/[^/]+$/.test(path) && method === "PATCH") {
      Object.assign(state.plans.find(row => row.plan.id === path.split("/")[3])!.plan,route.request().postDataJSON());
      status = 204;
    } else if (/^\/api\/plans\/[^/]+\/goals\/[^/]+\/progress$/.test(path)) {
      if (method === "POST") state.observations.unshift({ id: "observation-1", observedAt: "2026-10-27T08:00:00", ...route.request().postDataJSON() });
      data = state.observations;
    } else status = 404;
    await route.fulfill({
      status,
      contentType: "application/json",
      body: status === 204 ? "" : JSON.stringify(data),
    });
  });
  return { state, studentId, students };
}
