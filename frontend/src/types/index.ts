export type Role = "ADMIN" | "TEACHER" | "PARENT";
export type StudyType = "Cá nhân" | "Lớp nhóm" | "Bán trú";
export interface Student {
  id: string;
  name: string;
  birthDate: string;
  status: "Đang học" | "Tạm nghỉ" | "Ngừng học";
  teacherId: string;
  parentId: string;
  joined: string;
  note: string;
}
export interface Staff {
  id: string;
  name: string;
  specialty: string;
  certificate: string;
  status: "Đang làm việc" | "Nghỉ phép" | "Ngừng làm việc";
  salary: number;
}
export interface Parent {
  id: string;
  name: string;
  email: string;
  phone: string;
  studentIds: string[];
}
export interface SchoolClass {
  id: string;
  name: string;
  type: StudyType;
  teacherId: string;
  studentIds: string[];
  capacity: number;
  status: "Đang hoạt động" | "Tạm dừng";
}
export interface Session {
  id: string;
  classId: string;
  teacherId: string;
  date: string;
  start: string;
  end: string;
  room: string;
  attendance: Record<string, "Có mặt" | "Vắng" | "Có phép">;
}
export interface Fee {
  id: string;
  studentId: string;
  title: string;
  month: string;
  amount: number;
  paidAt: string | null;
}
export interface Leave {
  id: string;
  teacherId: string;
  from: string;
  to: string;
  reason: string;
  status: "Chờ duyệt" | "Đã duyệt" | "Từ chối";
}
export interface Notice {
  id: string;
  title: string;
  date: string;
  body: string;
}
export interface Database {
  version: number;
  students: Student[];
  staff: Staff[];
  parents: Parent[];
  classes: SchoolClass[];
  sessions: Session[];
  fees: Fee[];
  leaves: Leave[];
  notices: Notice[];
  checkins: Record<string, string[]>;
  admins: { id: string; name: string; email: string }[];
}
export type Collection =
  "students" | "staff" | "classes" | "sessions" | "fees" | "leaves";
export type Entity = Student | Staff | SchoolClass | Session | Fee | Leave;
