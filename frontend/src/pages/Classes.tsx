import { useConfirm } from "../hooks/useConfirm";
import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button, Input, Select, Modal, Empty } from "antd";
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
export function Classes() {
  const modal = useConfirm();
  const { db, view, role, run } = useDemo(),
    loc = useLocation(),
    nav = useNavigate();
  const [query, setQuery] = useState(""),
    [status, setStatus] = useState("all"),
    [editor, setEditor] = useState<SchoolClass | null | undefined>(),
    [detail, setDetail] = useState<SchoolClass>();
  const type = loc.pathname.startsWith("/individual")
    ? "Cá nhân"
    : loc.pathname.startsWith("/groups")
      ? "Lớp nhóm"
      : "Bán trú";
  const classes = view.classes.filter(
    (c) =>
      c.type === type &&
      normalize(c.name).includes(normalize(query)) &&
      (status === "all" || c.status === status),
  );
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
        <div className="classes-grid">
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
              </small>
              <h2>{c.name}</h2>
              <div className="class-teacher">
                <Avatar
                  name={db.staff.find((s) => s.id === c.teacherId)?.name ?? "?"}
                  index={i}
                />
                <span>
                  <small>Giáo viên phụ trách</small>
                  {db.staff.find((s) => s.id === c.teacherId)?.name}
                </span>
              </div>
              <div className="class-capacity">
                <span>Sĩ số</span>
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
                          title: "Xóa lớp " + c.name + "?",
                          content:
                            "Các buổi học của lớp cũng sẽ được xóa khỏi bản demo.",
                          okText: "Xóa lớp",
                          cancelText: "Hủy",
                          onOk: () =>
                            run((d) =>
                              mockService.remove(d, role, "classes", c.id),
                            ),
                        })
                      }
                    />
                  </>
                )}
              </div>
            </article>
          ))}
        </div>
        {!classes.length && <Empty description="Chưa có lớp học phù hợp" />}
      </section>
      {editor !== undefined && (
        <RecordEditor
          kind="classes"
          record={editor ?? undefined}
          initialType={type}
          onClose={() => setEditor(undefined)}
        />
      )}
      <Modal
        open={!!detail}
        title={detail?.name}
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
          {detail?.type} · Giáo viên:{" "}
          {db.staff.find((s) => s.id === detail?.teacherId)?.name}
        </p>
        <h3>Học sinh tham gia</h3>
        <div className="detail-list">
          {detail?.studentIds.map((id) => (
            <div key={id}>
              <span>{db.students.find((s) => s.id === id)?.name}</span>
              <small>{id}</small>
            </div>
          ))}
        </div>
      </Modal>
    </>
  );
}
