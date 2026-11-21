import { goalRequest } from "./goalBankApi";

export type SuggestedActivity = { id: string; goalId: string | null; name: string; description: string | null };
export type ActivityGoalOption = { id: string; code: string; name: string; program: string; domain: string };
const url = (goalId: string) => `/api/suggested-activities/goals/${encodeURIComponent(goalId)}`;
export const suggestedActivityApi = {
  searchGoals: (query: string) => goalRequest<ActivityGoalOption[]>(`/api/suggested-activities/goal-search?q=${encodeURIComponent(query)}`),
  searchActivities: (query: string) => goalRequest<SuggestedActivity[]>(`/api/suggested-activities/activities?q=${encodeURIComponent(query)}`),
  createLibrary: (name: string) => goalRequest<SuggestedActivity>("/api/suggested-activities/activities", { name }),
  list: (goalId: string) => goalRequest<SuggestedActivity[]>(url(goalId)),
  create: (goalId: string, name: string, description: string | null) =>
    goalRequest<SuggestedActivity>(url(goalId), { name, description }),
  link: (goalId: string, activityId: string) =>
    goalRequest<SuggestedActivity>(`${url(goalId)}/links`, { activityId }),
};
