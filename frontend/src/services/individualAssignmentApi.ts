import { goalRequest } from "./goalBankApi";
export type IndividualAssignment = {
  id: string; code: string; studentId: string; studentCode: string; studentName: string;
  teacherId: string; teacherName: string; startsOn: string;
};
export type AssignmentTeacher = { id: string; code: string; fullName: string };
const root = "/api/admin/individual-assignments";
export const individualAssignmentApi = {
  list: () => goalRequest<IndividualAssignment[]>(root),
  teachers: () => goalRequest<AssignmentTeacher[]>(`${root}/teachers`),
  create: (studentId: string, teacherId: string) => goalRequest<IndividualAssignment>(root, { studentId, teacherId }),
  change: (id: string, teacherId: string) => goalRequest<IndividualAssignment>(`${root}/${id}`, { teacherId }, "PUT"),
};
