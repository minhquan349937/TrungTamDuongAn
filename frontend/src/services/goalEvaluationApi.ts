import { goalRequest } from "./goalBankApi";
export type EvaluationInput = {
  achievementPercent: number | null;
  restartDecision: "RETRY" | "NO_RETRY" | null;
  evaluationNotes: string | null;
};
export type GoalEvaluation = EvaluationInput & {
  id: string; planGoalId: string; attemptNo: number;
  resultStatus: "NOT_STARTED" | "NOT_ACHIEVED" | "ACHIEVED";
  evaluatedAt: string | null; evaluatedBy: string; evaluatedByName: string;
  createdAt: string; updatedAt: string;
};
const path = (plan: string, goal: string) => `/api/plans/${plan}/goals/${goal}/evaluations`;
export const goalEvaluationApi = {
  list: (plan: string, goal: string) => goalRequest<GoalEvaluation[]>(path(plan, goal)),
  create: (plan: string, goal: string, input: EvaluationInput) => goalRequest<GoalEvaluation[]>(path(plan, goal), input),
  update: (plan: string, goal: string, id: string, input: EvaluationInput) => goalRequest<GoalEvaluation[]>(`${path(plan, goal)}/${id}`, input, "PUT"),
};
export const evaluationStatus = (percent: number | null) => percent === null ? "NOT_STARTED" : percent < 80 ? "NOT_ACHIEVED" : "ACHIEVED";
export const evaluationLabels: Record<GoalEvaluation["resultStatus"], string> = {
  NOT_STARTED: "Chưa thực hiện", NOT_ACHIEVED: "Không đạt", ACHIEVED: "Đạt",
};
