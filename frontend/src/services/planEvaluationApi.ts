import { goalRequest } from "./goalBankApi";

export type PlanEvaluation = {
  id: string;
  learningPlanId: string;
  planName: string;
  studentId: string;
  studentName: string;
  evaluationNotes: string;
  evaluatedBy: string;
  evaluatedByName: string;
  evaluatedAt: string;
  sentToParent: boolean;
  sentAt: string | null;
  updatedAt: string;
};

export const planEvaluationApi = {
  list: (planId: string) => goalRequest<PlanEvaluation[]>(`/api/learning-plans/${planId}/evaluations`),
  get: (id: string) => goalRequest<PlanEvaluation>(`/api/plan-evaluations/${id}`),
  create: (planId: string, evaluationNotes: string) =>
    goalRequest<PlanEvaluation>(`/api/learning-plans/${planId}/evaluations`, { evaluationNotes }),
  update: (id: string, evaluationNotes: string) =>
    goalRequest<PlanEvaluation>(`/api/plan-evaluations/${id}`, { evaluationNotes }, "PUT"),
  publish: (id: string) => goalRequest<PlanEvaluation>(`/api/plan-evaluations/${id}/send`, {}),
  forParent: () => goalRequest<PlanEvaluation[]>("/api/parent/plan-evaluations"),
};
