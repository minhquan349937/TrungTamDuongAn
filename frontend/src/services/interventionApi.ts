import {
  goalRequest,
  type Program,
  type GoalTarget,
  type GoalSelection,
  type AssignedGoal,
  type Plan,
} from "./goalBankApi";
export type StudentSummary = {
  id: string;
  code: string;
  fullName: string;
  birthDate: string;
  status: string;
  ageMonths: number;
  studyingGoals: number;
  achievedGoals: number;
};
export type ProgramSummary = Program & {
  domainCount: number;
  goalCount: number;
  ageMinMonths: number | null;
  ageMaxMonths: number | null;
};
export type AgeOption = {
  levelDomainId: string;
  domainId: string;
  levelId: string;
  levelName: string;
  ageMinMonths: number;
  ageMaxMonths: number;
};
export type DomainGroup = { id: string; code: string; name: string; ages: AgeOption[] };
export type GoalChoice = {
  id: string;
  code: string;
  name: string;
  description: string | null;
  displayOrder: number;
  hasSpecificGoals: boolean;
  canSelectGeneralGoal: boolean;
};
export type DomainTargets = {
  supportsSpecificGoals: boolean;
  goals: GoalChoice[];
};
export type GoalLearningHistory = GoalTarget & {
  resultStatus: "NOT_STARTED" | "NOT_ACHIEVED" | "ACHIEVED";
  achievementPercent: number | null;
  evaluatedAt: string | null;
};
export type StudentDomainTargets = DomainTargets & { history: GoalLearningHistory[] };
export type PlanView = { plan: Plan; goals: AssignedGoal[] };
export type Workspace = {
  student: StudentSummary;
  plans: PlanView[];
};
export type PlanDraft = {
  requestId: string;
  programId: string;
  name: string;
  startDate: string;
  selections: GoalTarget[];
};
export type PlanReview = Omit<PlanDraft, "selections"> & {
  items: GoalSelection[];
  total: number;
  reviewToken: string;
};
export type SavedPlan = { result: PlanView; alreadySaved: boolean };
const root = "/api/intervention";
export const interventionApi = {
  students: (q = "") =>
    goalRequest<StudentSummary[]>(
      `${root}/students?q=${encodeURIComponent(q)}`,
    ),
  workspace: (id: string) => goalRequest<Workspace>(`${root}/students/${id}`),
  programs: () => goalRequest<ProgramSummary[]>(`${root}/programs`),
  domains: (id: string) =>
    goalRequest<DomainGroup[]>(`${root}/programs/${id}/domains`),
  targets: (id: string) =>
    goalRequest<DomainTargets>(`${root}/level-domains/${id}/targets`),
  studentTargets: (student: string, id: string) =>
    goalRequest<StudentDomainTargets>(`${root}/students/${student}/level-domains/${id}/targets`),
  preview: (id: string, draft: PlanDraft) =>
    goalRequest<PlanReview>(`${root}/students/${id}/preview`, draft),
  complete: (id: string, review: PlanReview) =>
    goalRequest<SavedPlan>(`${root}/students/${id}/complete`, {
      requestId: review.requestId,
      programId: review.programId,
      name: review.name,
      startDate: review.startDate,
      reviewToken: review.reviewToken,
      selections: review.items.map(({ goalId, specificGoalId }) => ({
        goalId,
        specificGoalId,
      })),
    }),
};
