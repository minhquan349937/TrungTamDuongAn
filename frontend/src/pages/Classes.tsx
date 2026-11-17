import { useConfirm } from "../hooks/useConfirm";
import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Alert, App, Button, Input, Select, Modal, Empty, Spin } from "antd";
import {
  Plus,
  Search,
  Users,
  CalendarDays,
  Pencil,
  Trash2,
} from "lucide-react";
import { useDemo } from "../hooks/useDemo";
import { RecordEditor } from "../features/RecordEditor";
import { Badge, Avatar } from "../components/ui";
import type { SchoolClass } from "../types";
import { mockService } from "../services/mockService";
import { normalize } from "../utils/format";
import { individualAssignmentApi, type AssignmentTeacher } from "../services/individualAssignmentApi";
import { studentProfileApi, type StudentProfile } from "../services/studentProfileApi";
import { classManagementApi } from "../services/classManagementApi";
export function Classes() {
  const modal = useConfirm();
  const { message } = App.useApp();
  const { db, view, role, run } = useDemo(),
    loc = useLocation(),
    nav = useNavigate();
  const [query, setQuery] = useState(""),
    [status, setStatus] = useState("all"),
    [editor, setEditor] = useState<SchoolClass | null | undefined>(),
    [detail, setDetail] = useState<SchoolClass>();
  const [teachers, setTeachers] = useState<AssignmentTeacher[]>([]);
  const [teachersLoading, setTeachersLoading] = useState(false);
  const [teachersError, setTeachersError] = useState("");
  const [students, setStudents] = useState<StudentProfile[]>([]);
  const [studentsLoading, setStudentsLoading] = useState(false);
  const [studentsError, setStudentsError] = useState("");
  const [serverClasses, setServerClasses] = useState<SchoolClass[]>([]);
  const [classesLoading, setClassesLoading] = useState(false);
  const [classesError, setClassesError] = useState("");
  const [reload, setReload] = useState(0);
  const type = loc.pathname.startsWith("/individual") ? "Cá nhân" : loc.pathname.startsWith("/groups") ? "Lớp nhóm" : "Bán trú";
  const serverManaged = role === "ADMIN" && type !== "Cá nhân";
  useEffect(() => {
    if (!serverManaged) return;
    let active = true;
    setServerClasses([]); setClassesLoading(true); setClassesError("");
    classManagementApi.list(type).then(rows => { if (active) setServerClasses(rows); })
      .catch((error: Error) => { if (active) setClassesError(error.message); })
      .finally(() => { if (active) setClassesLoading(false); });
    return () => { active = false; };
  }, [serverManaged, type, reload]);
  useEffect(() => {
    if (role !== "ADMIN") return;
    let cancelled = false;
    setStudentsLoading(true);
    studentProfileApi.list().then(rows => {
      if (!cancelled) { setStudents(rows); setStudentsError(""); }
    }).catch(cause => {
      if (!cancelled) setStudentsError((cause as Error).message);
    }).finally(() => { if (!cancelled) setStudentsLoading(false); });
    return () => { cancelled = true; };
  }, [role, editor]);
  useEffect(() => {
    if (role !== "ADMIN") return;
    let cancelled = false;
    setTeachersLoading(true);
    individualAssignmentApi.teachers().then(rows => {
      if (!cancelled) { setTeachers(rows); setTeachersError(""); }
    }).catch(cause => {
      if (!cancelled) setTeachersError((cause as Error).message);
    }).finally(() => { if (!cancelled) setTeachersLoading(false); });
    return () => { cancelled = true; };
  }, [role, editor]);
  const teacherName = (id?: string) => teachers.find(t => t.id === id)?.fullName ?? db.staff.find(t => t.id === id)?.name;
  const pendingClasses = serverManaged && !classesLoading && !classesError ? view.classes.filter(c =>
    c.type === type && teachers.some(t => t.id === c.teacherId) && c.studentIds.every(id => students.some(s => s.id === id)) &&
    !serverClasses.some(saved => saved.id === c.id)) : [];
  const classSource = serverManaged ? [...serverClasses, ...pendingClasses] : view.classes;
  const classes = classSource.filter(
    (c) =>
      c.type === type &&
      normalize(c.name).includes(normalize(query)) &&
      (status === "all" || c.status === status),
  );
  const selectedClass = detail ? classSource.find(c => c.id === detail.id) : undefined;
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">QUẢN LÝ LỚP HỌC</div>
          <h1>
            {type === "Cá nhân"
              ? "Lớp can thiệp cá nhân"
              : type === "Lớp nhóm"
                ? "Lớp học nhóm"
                : "Lớp bán trú"}
          </h1>
          <p>Không gian phù hợp cho từng bước phát triển của con.</p>
        </div>
        {role === "ADMIN" && (
          <Button
            type="primary"
            icon={<Plus size={16} />}
            onClick={() => setEditor(null)}
          >
            Thêm lớp học
          </Button>
        )}
      </div>
      {teachersError && <Alert type="error" showIcon message={teachersError} />}
      {studentsError && <Alert type="error" showIcon message={studentsError} />}
      {classesError && <Alert type="error" showIcon message={classesError} action={<Button onClick={() => setReload(n => n + 1)}>Thử lại</Button>} />}
      <section className="panel">
        <div className="table-toolbar">
          <Input
            prefix={<Search size={16} />}
            placeholder="Tìm lớp học…"
            aria-label="Tìm lớp học"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <Select
            aria-label="Trạng thái lớp"
            value={status}
            onChange={setStatus}
            options={["all", "Đang hoạt động", "Tạm dừng"].map((value) => ({
              value,
              label: value === "all" ? "Tất cả trạng thái" : value,
            }))}
          />
          <span className="results-hint">{classes.length} lớp học</span>
        </div>
        <Spin spinning={classesLoading}><div className="classes-grid">
          {classes.map((c, i) => (
            <article className="class-card" key={c.id}>
              <div className="class-card-top">
                <span className="class-symbol">
                  <Users size={23} />
                </span>
                <Badge>{c.status}</Badge>
              </div>
              <small>
                {c.id} · {c.type}
                  {pendingClasses.some(pending => pending.id === c.id) && " · Chỉ lưu trong trình duyệt"}
              </small>
              <h2>{c.name}</h2>
              <div className="class-teacher">
                <Avatar
                  name={teacherName(c.teacherId) ?? "?"}
                  index={i}
                />
                <span>
                  <small>Giáo viên phụ trách</small>
                  {teacherName(c.teacherId)}
                </span>
              </div>
              <div className="class-capacity">
                <span>{role === "PARENT" ? "Học sinh liên kết" : "Sĩ số"}</span>
                <b>
                  {c.studentIds.length} / {c.capacity}
                </b>
              </div>
              <div className="capacity-track">
                <span
                  style={{
                    width:
                      Math.min(100, (c.studentIds.length / c.capacity) * 100) +
                      "%",
                  }}
                />
              </div>
              <div className="class-actions">
                <Button onClick={() => setDetail(c)}>Xem chi tiết</Button>
                {pendingClasses.some(pending => pending.id === c.id) && <Button type="primary" onClick={() => setEditor(c)}>Đồng bộ lên hệ thống</Button>}
                {role === "ADMIN" && (
                  <>
                    <Button
                      aria-label={"Sửa lớp " + c.name}
                      icon={<Pencil size={15} />}
                      onClick={() => setEditor(c)}
                    />
                    <Button
                      danger
                      aria-label={"Xóa lớp " + c.name}
                      icon={<Trash2 size={15} />}
                      onClick={() =>
                        modal.confirm({
                          title: (serverManaged ? "Đóng lớp " : "Xóa lớp ") + c.name + "?",
                          content:
                            serverManaged ? "Lớp sẽ ngừng hiển thị trong danh sách phụ trách của giáo viên. Lịch sử học tập được giữ lại." : "Các buổi học của lớp cũng sẽ được xóa khỏi bản demo.",
                          okText: serverManaged ? "Đóng lớp" : "Xóa lớp",
                          cancelText: "Hủy",
                          onOk: async () => {
                            if (serverManaged && serverClasses.some(saved => saved.id === c.id)) {
                              try { await classManagementApi.close(c.id); message.success("Đã đóng lớp học."); setReload(n => n + 1); }
                              catch (error) { message.error((error as Error).message); throw error; }
                              return;
                            }
                            run((d) =>
                              mockService.remove(d, role, "classes", c.id),
                            );
                          },
                        })
                      }
                    />
                  </>
                )}
              </div>
            </article>
          ))}
        </div></Spin>
        {!classesLoading && !classes.length && <Empty description="Chưa có lớp học phù hợp" />}
      </section>
      {editor !== undefined && (
        <RecordEditor
          kind="classes"
          record={editor ?? undefined}
          initialType={type}
          classTeachers={teachers}
          teachersLoading={teachersLoading}
          classStudents={students}
          studentsLoading={studentsLoading}
          onSaveClass={serverManaged ? async row => {
            const existing = serverClasses.some(saved => saved.id === row.id);
            await classManagementApi.save(row, existing);
            if (!existing && db.classes.some(local => local.id === row.id)) {
              run(data => ({ ...data, classes: data.classes.filter(local => local.id !== row.id) }), "Đã đồng bộ lớp học lên hệ thống.");
            }
            message.success(editor ? "Đã cập nhật lớp học và phân công." : "Đã tạo lớp học và phân công.");
            setReload(n => n + 1);
          } : undefined}
          onClose={() => setEditor(undefined)}
        />
      )}
      <Modal
        open={!!selectedClass}
        title={selectedClass?.name}
        onCancel={() => setDetail(undefined)}
        footer={
          <Button
            icon={<CalendarDays size={16} />}
            onClick={() => nav("/schedule")}
          >
            Xem lịch học
          </Button>
        }
      >
        <p>
          {selectedClass?.type} · Giáo viên:{" "}
          {teacherName(selectedClass?.teacherId)}
        </p>
        <h3>Học sinh tham gia</h3>
        <div className="detail-list">
          {selectedClass?.studentIds.map((id) => (
            <div key={id}>
              <span>{students.find(s => s.id === id)?.fullName ?? db.students.find(s => s.id === id)?.name}</span>
              <small>{students.find(s => s.id === id)?.code ?? db.students.find(s => s.id === id)?.code ?? "—"}</small>
            </div>
          ))}
        </div>
      </Modal>
    </>
  );
}
