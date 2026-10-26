import { useConfirm } from "../hooks/useConfirm";
import { useState } from "react";
import { Button, Modal, Tabs, Descriptions } from "antd";
import { Plus, Eye, Pencil, Trash2, ArrowUpRight } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useDemo } from "../hooks/useDemo";
import { DataTable, Avatar, Badge } from "../components/ui";
import { RecordEditor } from "./RecordEditor";
import { mockService } from "../services/mockService";
import { age, dateLabel, money } from "../utils/format";
import type { Student, Staff } from "../types";
export function StudentsTable({ compact = false }: { compact?: boolean }) {
  const modal = useConfirm();
  const { db, view, role, run } = useDemo();
  const [editor, setEditor] = useState<Student | null | undefined>(),
    [detail, setDetail] = useState<string>();
  const student = view.students.find((s) => s.id === detail);
  const admin = role === "ADMIN";
  function remove(s: Student) {
    modal.confirm({
      title: "Xóa hồ sơ " + s.name + "?",
      content:
        "Hồ sơ, liên kết lớp, điểm danh và học phí của học sinh sẽ được xóa khỏi dữ liệu demo.",
      okText: "Xóa hồ sơ",
      okButtonProps: { danger: true },
      cancelText: "Hủy",
      onOk: () => run((d) => mockService.remove(d, role, "students", s.id)),
    });
  }
  return (
    <>
      <DataTable
        title={
          compact
            ? "Học sinh gần đây"
            : role === "PARENT"
              ? "Hồ sơ của con"
              : "Hồ sơ học sinh"
        }
        subtitle={
          compact
            ? "Theo dõi và quản lý hành trình của từng học sinh"
            : undefined
        }
        rows={view.students}
        pageSize={compact ? 3 : 10}
        searchText={(s) => s.name + " " + s.id}
        statusOf={(s) => s.status}
        statuses={["Đang học", "Tạm nghỉ", "Ngừng học"]}
        action={
          admin && (
            <Button
              className="outline-action"
              icon={<Plus size={16} />}
              onClick={() => setEditor(null)}
            >
              Thêm học sinh
            </Button>
          )
        }
        columns={[
          {
            label: "Học sinh",
            render: (s, i) => (
              <div className="person-cell">
                <Avatar name={s.name} index={i} />
                <div>
                  <button className="name-link" onClick={() => setDetail(s.id)}>
                    {s.name}
                  </button>
                  <small>{s.id}</small>
                </div>
              </div>
            ),
          },
          { label: "Độ tuổi", render: (s) => age(s.birthDate) },
          {
            label: "Hình thức học",
            render: (s) => (
              <span className="study-types">
                {[
                  ...new Set(
                    db.classes
                      .filter((c) => c.studentIds.includes(s.id))
                      .map((c) => c.type),
                  ),
                ].join(" · ") || "Chưa phân lớp"}
              </span>
            ),
          },
          {
            label: "Giáo viên phụ trách",
            render: (s) =>
              db.staff.find((t) => t.id === s.teacherId)?.name ??
              "Chưa phân công",
          },
          { label: "Trạng thái", render: (s) => <Badge>{s.status}</Badge> },
          {
            label: "Thao tác",
            render: (s) => (
              <div className="row-actions">
                <button
                  aria-label={"Xem " + s.name}
                  onClick={() => setDetail(s.id)}
                >
                  <Eye size={16} />
                </button>
                {admin && (
                  <>
                    <button
                      aria-label={"Sửa " + s.name}
                      onClick={() => setEditor(s)}
                    >
                      <Pencil size={16} />
                    </button>
                    <button
                      aria-label={"Xóa " + s.name}
                      onClick={() => remove(s)}
                    >
                      <Trash2 size={16} />
                    </button>
                  </>
                )}
              </div>
            ),
          },
        ]}
      />
      {editor !== undefined && (
        <RecordEditor
          kind="students"
          record={editor ?? undefined}
          onClose={() => setEditor(undefined)}
        />
      )}
      <Modal
        open={!!student}
        title="Hồ sơ học sinh"
        onCancel={() => setDetail(undefined)}
        footer={
          student && admin ? (
            <Button
              type="primary"
              onClick={() => {
                setEditor(student);
                setDetail(undefined);
              }}
            >
              Chỉnh sửa hồ sơ
            </Button>
          ) : null
        }
        width={700}
      >
        {student && (
          <>
            <div className="profile-heading">
              <Avatar name={student.name} />
              <div>
                <h2>{student.name}</h2>
                <span>
                  {student.id} · <Badge>{student.status}</Badge>
                </span>
              </div>
            </div>
            <Tabs
              items={[
                {
                  key: "info",
                  label: "Thông tin chung",
                  children: (
                    <Descriptions
                      column={1}
                      items={[
                        {
                          key: "birth",
                          label: "Ngày sinh",
                          children: dateLabel(student.birthDate),
                        },
                        {
                          key: "teacher",
                          label: "Giáo viên phụ trách",
                          children: db.staff.find(
                            (t) => t.id === student.teacherId,
                          )?.name,
                        },
                        {
                          key: "joined",
                          label: "Ngày nhập học",
                          children: dateLabel(student.joined),
                        },
                        {
                          key: "note",
                          label: "Ghi chú",
                          children: student.note,
                        },
                      ]}
                    />
                  ),
                },
                {
                  key: "parent",
                  label: "Phụ huynh liên kết",
                  children: (
                    <Descriptions
                      column={1}
                      items={[
                        {
                          key: "p",
                          label: "Họ tên",
                          children: db.parents.find(
                            (p) => p.id === student.parentId,
                          )?.name,
                        },
                        {
                          key: "phone",
                          label: "Điện thoại giả lập",
                          children: db.parents.find(
                            (p) => p.id === student.parentId,
                          )?.phone,
                        },
                        {
                          key: "email",
                          label: "Email minh họa",
                          children: db.parents.find(
                            (p) => p.id === student.parentId,
                          )?.email,
                        },
                      ]}
                    />
                  ),
                },
                {
                  key: "classes",
                  label: "Lớp đang tham gia",
                  children: (
                    <div className="detail-list">
                      {db.classes
                        .filter((c) => c.studentIds.includes(student.id))
                        .map((c) => (
                          <div key={c.id}>
                            <b>{c.name}</b>
                            <span>{c.type}</span>
                            <Badge>{c.status}</Badge>
                          </div>
                        ))}
                    </div>
                  ),
                },
              ]}
            />
          </>
        )}
      </Modal>
    </>
  );
}
export function StaffTable({ compact = false }: { compact?: boolean }) {
  const modal = useConfirm();
  const { db, view, role, run } = useDemo();
  const nav = useNavigate();
  const [editor, setEditor] = useState<Staff | null | undefined>(),
    [detail, setDetail] = useState<string>();
  const staff = view.staff.find((s) => s.id === detail);
  return (
    <>
      <DataTable
        title={compact ? "Giáo viên & nhân viên" : "Hồ sơ nhân sự"}
        subtitle={
          compact ? "Đội ngũ đồng hành cùng các con mỗi ngày" : undefined
        }
        rows={view.staff}
        pageSize={compact ? 3 : 10}
        searchText={(s) => s.name + " " + s.id + " " + s.specialty}
        statuses={["Đang làm việc", "Nghỉ phép", "Ngừng làm việc"]}
        statusOf={(s) => s.status}
        action={
          role === "ADMIN" && (
            <Button
              className="outline-action"
              icon={<Plus size={16} />}
              onClick={() => setEditor(null)}
            >
              Thêm nhân viên
            </Button>
          )
        }
        columns={[
          {
            label: "Nhân viên",
            render: (s, i) => (
              <div className="person-cell">
                <Avatar name={s.name} index={i + 1} />
                <div>
                  <button className="name-link" onClick={() => setDetail(s.id)}>
                    {s.name}
                  </button>
                  <small>{s.id}</small>
                </div>
              </div>
            ),
          },
          { label: "Chuyên môn", render: (s) => s.specialty },
          {
            label: "Lớp phụ trách",
            render: (s) => (
              <span className="class-count">
                {db.classes.filter((c) => c.teacherId === s.id).length} lớp
              </span>
            ),
          },
          { label: "Trạng thái", render: (s) => <Badge>{s.status}</Badge> },
          {
            label: "Thao tác",
            render: (s) => (
              <div className="row-actions">
                <button
                  aria-label={"Xem " + s.name}
                  onClick={() => setDetail(s.id)}
                >
                  <Eye size={16} />
                </button>
                {role === "ADMIN" && (
                  <>
                    <button
                      aria-label={"Sửa " + s.name}
                      onClick={() => setEditor(s)}
                    >
                      <Pencil size={16} />
                    </button>
                    <button
                      aria-label={"Xóa " + s.name}
                      onClick={() =>
                        modal.confirm({
                          title: "Xóa hồ sơ nhân viên?",
                          content: s.name,
                          okText: "Xóa",
                          cancelText: "Hủy",
                          okButtonProps: { danger: true },
                          onOk: () =>
                            run((d) =>
                              mockService.remove(d, role, "staff", s.id),
                            ),
                        })
                      }
                    >
                      <Trash2 size={16} />
                    </button>
                  </>
                )}
              </div>
            ),
          },
        ]}
      />
      {editor !== undefined && (
        <RecordEditor
          kind="staff"
          record={editor ?? undefined}
          onClose={() => setEditor(undefined)}
        />
      )}
      <Modal
        open={!!staff}
        title="Hồ sơ nhân viên"
        onCancel={() => setDetail(undefined)}
        footer={null}
        width={800}
      >
        {staff && (
          <>
            <div className="profile-heading">
              <Avatar name={staff.name} />
              <div>
                <h2>{staff.name}</h2>
                <span>
                  {staff.id} · {staff.specialty}
                </span>
              </div>
            </div>
            <Tabs
              items={[
                {
                  key: "info",
                  label: "Thông tin nhân viên",
                  children: (
                    <Descriptions
                      column={1}
                      items={[
                        { key: "name", label: "Họ tên", children: staff.name },
                        {
                          key: "status",
                          label: "Trạng thái",
                          children: <Badge>{staff.status}</Badge>,
                        },
                      ]}
                    />
                  ),
                },
                {
                  key: "certificate",
                  label: "Chuyên môn và chứng chỉ",
                  children: (
                    <>
                      <h3>{staff.specialty}</h3>
                      <p>{staff.certificate || "Chưa cập nhật chứng chỉ"}</p>
                    </>
                  ),
                },
                {
                  key: "assign",
                  label: "Phân công giảng dạy",
                  children: (
                    <div className="detail-list">
                      {db.classes
                        .filter((c) => c.teacherId === staff.id)
                        .map((c) => (
                          <div key={c.id}>
                            <b>{c.name}</b>
                            <span>{c.type}</span>
                          </div>
                        ))}
                      <Button
                        onClick={() => {
                          setDetail(undefined);
                          nav("/individual/assign");
                        }}
                        icon={<ArrowUpRight size={15} />}
                      >
                        Xem phân công lớp
                      </Button>
                    </div>
                  ),
                },
                {
                  key: "schedule",
                  label: "Lịch làm việc",
                  children: (
                    <Button
                      onClick={() => {
                        setDetail(undefined);
                        nav("/schedule");
                      }}
                    >
                      Xem lịch học
                    </Button>
                  ),
                },
                {
                  key: "checkin",
                  label: "Chấm công",
                  children: (
                    <p>
                      Đã chấm công {(db.checkins[staff.id] ?? []).length} ngày:{" "}
                      {(db.checkins[staff.id] ?? [])
                        .map(dateLabel)
                        .join(", ") || "Chưa có dữ liệu."}
                    </p>
                  ),
                },
                {
                  key: "leave",
                  label: "Nghỉ phép",
                  children: (
                    <div className="detail-list">
                      {db.leaves
                        .filter((l) => l.teacherId === staff.id)
                        .map((l) => (
                          <div key={l.id}>
                            <span>
                              {dateLabel(l.from)} – {dateLabel(l.to)}
                            </span>
                            <Badge>{l.status}</Badge>
                          </div>
                        ))}
                      <Button
                        onClick={() => {
                          setDetail(undefined);
                          nav("/leave");
                        }}
                      >
                        Quản lý nghỉ phép
                      </Button>
                    </div>
                  ),
                },
                {
                  key: "salary",
                  label: "Lương cơ bản",
                  children: <h3>{money(staff.salary)} / tháng</h3>,
                },
              ]}
            />
          </>
        )}
      </Modal>
    </>
  );
}
