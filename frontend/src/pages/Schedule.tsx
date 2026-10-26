import { useConfirm } from "../hooks/useConfirm";
import { useState } from "react";
import { useLocation } from "react-router-dom";
import { Button, Input, Select, Segmented, Modal, Empty } from "antd";
import {
  Plus,
  ChevronLeft,
  ChevronRight,
  Clock,
  MapPin,
  Pencil,
  Check,
  CalendarDays,
} from "lucide-react";
import { useDemo } from "../hooks/useDemo";
import { RecordEditor } from "../features/RecordEditor";
import { Badge, Avatar } from "../components/ui";
import { mockService } from "../services/mockService";
import { today, dateLabel } from "../utils/format";
import type { Session } from "../types";
function shift(date: string, days: number) {
  const d = new Date(date + "T12:00:00");
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
export function Schedule() {
  const modal = useConfirm();
  const { db, view, role, run } = useDemo(),
    loc = useLocation();
  const [date, setDate] = useState(today()),
    [mode, setMode] = useState(loc.pathname.endsWith("week") ? "Tuần" : "Ngày"),
    [teacher, setTeacher] = useState("all"),
    [cls, setCls] = useState("all"),
    [editor, setEditor] = useState<Session | null | undefined>(),
    [att, setAtt] = useState<Session>(),
    [attendance, setAttendance] = useState<Session["attendance"]>({});
  const isAttendance = loc.pathname.startsWith("/attendance");
  const monday = shift(
    date,
    -((new Date(date + "T12:00:00").getDay() + 6) % 7),
  );
  const days =
    mode === "Tuần"
      ? Array.from({ length: 7 }, (_, i) => shift(monday, i))
      : [date];
  const sessions = view.sessions
    .filter(
      (s) =>
        days.includes(s.date) &&
        (teacher === "all" || s.teacherId === teacher) &&
        (cls === "all" || s.classId === cls) &&
        (!loc.pathname.endsWith("daycare") ||
          view.classes.find((c) => c.id === s.classId)?.type === "Bán trú"),
    )
    .sort((a, b) => a.start.localeCompare(b.start));
  function openAttendance(s: Session) {
    setAtt(s);
    setAttendance({ ...s.attendance });
  }
  const studentIds = att
    ? (db.classes
        .find((c) => c.id === att.classId)
        ?.studentIds.filter((id) => view.students.some((s) => s.id === id)) ??
      [])
    : [];
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">LỊCH HỌC & CHUYÊN CẦN</div>
          <h1>
            {isAttendance
              ? "Điểm danh học sinh"
              : "Mỗi buổi học, một bước tiến"}
          </h1>
          <p>Theo dõi lịch, phân công và hoạt động của từng buổi học.</p>
        </div>
        {role === "ADMIN" && (
          <Button
            type="primary"
            icon={<Plus size={16} />}
            onClick={() => setEditor(null)}
          >
            Tạo lịch học
          </Button>
        )}
      </div>
      <section className="panel schedule-panel">
        <div className="schedule-toolbar">
          <div className="date-navigation">
            <Button
              aria-label="Khoảng thời gian trước"
              icon={<ChevronLeft size={16} />}
              onClick={() => setDate(shift(date, mode === "Tuần" ? -7 : -1))}
            />
            <Input
              aria-label="Ngày xem lịch"
              type="date"
              value={date}
              onChange={(e) => e.target.value && setDate(e.target.value)}
            />
            <Button
              aria-label="Khoảng thời gian sau"
              icon={<ChevronRight size={16} />}
              onClick={() => setDate(shift(date, mode === "Tuần" ? 7 : 1))}
            />
            <Button onClick={() => setDate(today())}>Hôm nay</Button>
          </div>
          <Segmented
            value={mode}
            onChange={(v) => setMode(String(v))}
            options={["Ngày", "Tuần"]}
          />
        </div>
        <div className="schedule-filters">
          <Select
            aria-label="Lọc giáo viên"
            value={teacher}
            onChange={setTeacher}
            options={[
              { value: "all", label: "Tất cả giáo viên" },
              ...view.staff.map((s) => ({ value: s.id, label: s.name })),
            ]}
          />
          <Select
            aria-label="Lọc lớp học"
            value={cls}
            onChange={setCls}
            options={[
              { value: "all", label: "Tất cả lớp học" },
              ...view.classes.map((c) => ({ value: c.id, label: c.name })),
            ]}
          />
          <span>{sessions.length} buổi học</span>
        </div>
        <div
          className={
            "calendar-grid " + (mode === "Tuần" ? "week-view" : "day-view")
          }
        >
          {days.map((d) => (
            <div
              className={"calendar-day " + (d === today() ? "is-today" : "")}
              key={d}
            >
              <div className="calendar-day-heading">
                <b>
                  {new Date(d + "T12:00:00").toLocaleDateString("vi-VN", {
                    weekday: "long",
                  })}
                </b>
                <span>{dateLabel(d)}</span>
              </div>
              <div className="calendar-events">
                {sessions
                  .filter((s) => s.date === d)
                  .map((s) => {
                    const c = view.classes.find((c) => c.id === s.classId),
                      done = Object.keys(s.attendance).length;
                    return (
                      <article
                        className={
                          "calendar-event event-" +
                          (c?.type === "Cá nhân"
                            ? "individual"
                            : c?.type === "Lớp nhóm"
                              ? "group"
                              : "daycare")
                        }
                        key={s.id}
                      >
                        <div className="event-meta">
                          <span>
                            <Clock size={13} />
                            {s.start} – {s.end}
                          </span>
                          {role === "ADMIN" && (
                            <button
                              aria-label={"Đổi lịch " + c?.name + " " + s.date}
                              onClick={() => setEditor(s)}
                            >
                              <Pencil size={14} />
                            </button>
                          )}
                        </div>
                        <h3>{c?.name}</h3>
                        <p>
                          {db.staff.find((t) => t.id === s.teacherId)?.name}
                        </p>
                        <div className="event-location">
                          <MapPin size={13} />
                          {s.room}
                          <span>{c?.type}</span>
                        </div>
                        <div className="event-footer">
                          <Badge>
                            {done ? "Đã điểm danh" : "Chưa điểm danh"}
                          </Badge>
                          <button onClick={() => openAttendance(s)}>
                            {role === "PARENT" ? "Xem điểm danh" : "Điểm danh"}
                            <Check size={13} />
                          </button>
                        </div>
                      </article>
                    );
                  })}
                {!sessions.some((s) => s.date === d) && (
                  <div className="calendar-empty">
                    <CalendarDays size={24} />
                    <span>Chưa có lịch học</span>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      </section>
      {editor !== undefined && (
        <RecordEditor
          kind="sessions"
          record={editor ?? undefined}
          onClose={() => setEditor(undefined)}
        />
      )}
      <Modal
        open={!!att}
        title={
          "Điểm danh · " + db.classes.find((c) => c.id === att?.classId)?.name
        }
        onCancel={() => setAtt(undefined)}
        onOk={() => {
          if (studentIds.some((id) => !attendance[id])) {
            modal.warning({
              title: "Chưa hoàn tất điểm danh",
              content: "Vui lòng chọn trạng thái cho từng học sinh.",
              okText: "Đã hiểu",
            });
            return;
          }
          if (
            att &&
            run(
              (d) => mockService.attendance(d, role, att.id, attendance),
              "Đã lưu điểm danh",
            )
          )
            setAtt(undefined);
        }}
        okText="Lưu điểm danh"
        cancelText="Đóng"
        footer={role === "PARENT" ? null : undefined}
        width={640}
      >
        <p>
          {att && dateLabel(att.date)} · {att?.start} – {att?.end}
        </p>
        {role !== "PARENT" && (
          <Button
            className="mb-4"
            onClick={() =>
              setAttendance(
                Object.fromEntries(studentIds.map((id) => [id, "Có mặt"])),
              )
            }
          >
            Đánh dấu tất cả có mặt
          </Button>
        )}
        <div className="attendance-list">
          {studentIds.map((id) => (
            <div key={id}>
              <div className="person-cell">
                <Avatar
                  name={db.students.find((s) => s.id === id)?.name ?? ""}
                />
                <b>{db.students.find((s) => s.id === id)?.name}</b>
              </div>
              {role === "PARENT" ? (
                <Badge>{attendance[id] ?? "Chưa điểm danh"}</Badge>
              ) : (
                <Select
                  aria-label={"Điểm danh " + id}
                  placeholder="Chọn trạng thái"
                  value={attendance[id]}
                  onChange={(v) => setAttendance({ ...attendance, [id]: v })}
                  options={["Có mặt", "Vắng", "Có phép"].map((value) => ({
                    value,
                    label: value,
                  }))}
                />
              )}
            </div>
          ))}
        </div>
        {studentIds.length === 0 && (
          <Empty description="Lớp chưa có học sinh" />
        )}
      </Modal>
    </>
  );
}
