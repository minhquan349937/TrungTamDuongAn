import { useConfirm } from "../hooks/useConfirm";
import { useEffect, useState } from "react";
import { App, Button, Form, Input, Modal } from "antd";
import { useLocation, useNavigate } from "react-router-dom";
import {
  Plus,
  CalendarCheck,
  BookOpen,
  ArrowLeft,
  Bell,
  ShieldCheck,
  Pencil,
} from "lucide-react";
import { useDemo } from "../hooks/useDemo";
import { DataTable, Badge, SectionTitle, Avatar } from "../components/ui";
import { StaffTable } from "../features/PeopleTables";
import { RecordEditor } from "../features/RecordEditor";
import { mockService } from "../services/mockService";
import { today, dateLabel } from "../utils/format";
import { titles } from "../routes/menu";
import { studentProfileApi, type GuardianProfile, type GuardianUpdateInput, type StudentProfile } from "../services/studentProfileApi";
export function LeavePage() {
  const modal = useConfirm();
  const { db, view, role, run } = useDemo(),
    [add, setAdd] = useState(false);
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">CÔNG VIỆC & NHÂN SỰ</div>
          <h1>Đơn nghỉ phép</h1>
          <p>Đăng ký và theo dõi trạng thái nghỉ phép của đội ngũ.</p>
        </div>
        <Button
          type="primary"
          icon={<Plus size={16} />}
          onClick={() => setAdd(true)}
        >
          Đăng ký nghỉ phép
        </Button>
      </div>
      <DataTable
        title="Danh sách đơn nghỉ phép"
        rows={view.leaves}
        searchText={(l) =>
          l.reason + " " + db.staff.find((s) => s.id === l.teacherId)?.name
        }
        statuses={["Chờ duyệt", "Đã duyệt", "Từ chối"]}
        statusOf={(l) => l.status}
        columns={[
          {
            label: "Nhân viên",
            render: (l) => db.staff.find((s) => s.id === l.teacherId)?.name,
          },
          {
            label: "Thời gian",
            render: (l) => dateLabel(l.from) + " – " + dateLabel(l.to),
          },
          { label: "Lý do", render: (l) => l.reason },
          { label: "Trạng thái", render: (l) => <Badge>{l.status}</Badge> },
          {
            label: "Thao tác",
            render: (l) =>
              role === "ADMIN" && l.status === "Chờ duyệt" ? (
                <div className="row-actions">
                  <Button
                    size="small"
                    type="primary"
                    onClick={() =>
                      run(
                        (d) => mockService.approve(d, role, l.id, "Đã duyệt"),
                        "Đã duyệt đơn nghỉ phép",
                      )
                    }
                  >
                    Duyệt
                  </Button>
                  <Button
                    size="small"
                    danger
                    onClick={() =>
                      modal.confirm({
                        title: "Từ chối đơn nghỉ phép?",
                        content: l.reason,
                        okText: "Từ chối đơn",
                        cancelText: "Hủy",
                        onOk: () =>
                          run((d) =>
                            mockService.approve(d, role, l.id, "Từ chối"),
                          ),
                      })
                    }
                  >
                    Từ chối
                  </Button>
                </div>
              ) : (
                "—"
              ),
          },
        ]}
      />
      {add && <RecordEditor kind="leaves" onClose={() => setAdd(false)} />}
    </>
  );
}
export function WorkPage() {
  const { db, role, run } = useDemo();
  const checked = (db.checkins.NV001 ?? []).includes(today());
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">KHÔNG GIAN GIÁO VIÊN</div>
          <h1>Hồ sơ công việc cá nhân</h1>
          <p>Thông tin chuyên môn và quá trình làm việc của bạn.</p>
        </div>
      </div>
      <div className="action-banner">
        <CalendarCheck size={32} />
        <div>
          <h3>Chấm công ngày {dateLabel(today())}</h3>
          <p>
            {checked
              ? "Đã ghi nhận ngày làm việc của bạn."
              : "Chúc bạn một ngày làm việc nhiều niềm vui."}
          </p>
        </div>
        <Button
          type="primary"
          disabled={checked}
          onClick={() =>
            run((d) => mockService.checkin(d, role), "Chấm công thành công")
          }
        >
          {checked ? "Đã chấm công" : "Chấm công hôm nay"}
        </Button>
      </div>
      <StaffTable />
    </>
  );
}
export function Notifications() {
  const { view } = useDemo();
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">KẾT NỐI TRUNG TÂM</div>
          <h1>Thông báo</h1>
          <p>Những cập nhật mới từ Dương An.</p>
        </div>
      </div>
      <section className="panel">
        {view.notices.map((n) => (
          <article className="notice-row" key={n.id}>
            <span className="mini-icon">
              <Bell size={20} />
            </span>
            <div>
              <small>{dateLabel(n.date)}</small>
              <h2>{n.title}</h2>
              <p>{n.body}</p>
            </div>
          </article>
        ))}
      </section>
    </>
  );
}
export function Parents() {
  const { message } = App.useApp();
  const [form] = Form.useForm<GuardianUpdateInput>();
  const [students, setStudents] = useState<StudentProfile[]>([]);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState<GuardianProfile | null>(null);
  const [saving, setSaving] = useState(false);
  async function reload() {
    try {
      setStudents(await studentProfileApi.list());
      setError("");
    } catch (err) { setError((err as Error).message); }
  }
  useEffect(() => {
    let active = true;
    studentProfileApi.list().then((rows) => { if (active) setStudents(rows); })
      .catch((err: Error) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, []);
  useEffect(() => {
    if (!editing) return;
    form.setFieldsValue({ fullName: editing.fullName, phone: editing.phone,
      email: editing.email, address: editing.address });
  }, [editing, form]);
  async function saveGuardian(values: GuardianUpdateInput) {
    if (!editing) return;
    setSaving(true);
    try {
      await studentProfileApi.updateGuardian(editing.id, {
        fullName: values.fullName.trim(), phone: values.phone.trim(),
        email: values.email?.trim() || null, address: values.address?.trim() || null,
      });
      await reload();
      setEditing(null);
      message.success("Đã cập nhật hồ sơ phụ huynh");
    } catch (err) { message.error((err as Error).message); }
    finally { setSaving(false); }
  }
  const parents = [...new Map(students.filter((s) => s.guardian).map((s) => [s.guardian!.id, s.guardian!])).values()];
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">QUẢN LÝ HỒ SƠ</div>
          <h1>Hồ sơ phụ huynh</h1>
          <p>Phụ huynh chính đã được nhập cùng hồ sơ học sinh.</p>
        </div>
      </div>
      {error && <div className="info-note">{error}</div>}
      <DataTable
        title="Danh sách phụ huynh"
        rows={parents}
        searchText={(p) => p.fullName + " " + p.id}
        columns={[
          {
            label: "Phụ huynh",
            render: (p, i) => (
              <div className="person-cell">
                <Avatar name={p.fullName} index={i} />
                <div>
                  <b>{p.fullName}</b>
                  <small>{p.code}</small>
                </div>
              </div>
            ),
          },
          { label: "Email", render: (p) => p.email || "—" },
          { label: "Điện thoại", render: (p) => p.phone },
          {
            label: "Hồ sơ con liên kết",
            render: (p) =>
              students
                .filter((s) => s.guardian?.id === p.id)
                .map((s) => s.fullName)
                .join(", "),
          },
          {
            label: "Thao tác",
            render: (p) => <Button icon={<Pencil size={15} />} onClick={() => setEditing(p)}>Sửa</Button>,
          },
        ]}
      />
      <Modal open={!!editing} title="Sửa hồ sơ phụ huynh" onCancel={() => setEditing(null)}
        onOk={() => form.submit()} okText="Lưu thay đổi" confirmLoading={saving} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={saveGuardian}>
          <Form.Item name="fullName" label="Họ và tên" rules={[{ required: true, whitespace: true, message: "Vui lòng nhập họ tên." }]}>
            <Input maxLength={150} />
          </Form.Item>
          <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, whitespace: true, message: "Vui lòng nhập số điện thoại." }]}>
            <Input maxLength={30} type="tel" />
          </Form.Item>
          <Form.Item name="email" label="Email" rules={[{ type: "email", message: "Email không hợp lệ." }]}>
            <Input maxLength={254} />
          </Form.Item>
          <Form.Item name="address" label="Địa chỉ"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
    </>
  );
}
export function Accounts() {
  const { db } = useDemo();
  const rows = [
    ...db.admins.map((a) => ({ ...a, role: "Quản trị viên" })),
    ...db.staff.map((s) => ({
      ...s,
      email: s.id.toLowerCase() + "@example.invalid",
      role: "Giáo viên",
    })),
    ...db.parents.map((p) => ({ ...p, role: "Phụ huynh" })),
  ];
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">QUẢN LÝ HỆ THỐNG</div>
          <h1>Tài khoản & phân quyền</h1>
          <p>
            Chuyển vai trò tại góc trên bên phải để trải nghiệm quyền truy cập.
          </p>
        </div>
      </div>
      <div className="info-note mb-4">
        <ShieldCheck size={18} /> Quyền demo được kiểm tra tại lớp dịch vụ và
        đường dẫn. Quản lý tài khoản thực sẽ phát triển ở giai đoạn 2.
      </div>
      <DataTable
        title="Tài khoản minh họa"
        rows={rows}
        searchText={(r) => r.name + " " + r.email}
        statusOf={(r) => r.role}
        statuses={["Quản trị viên", "Giáo viên", "Phụ huynh"]}
        columns={[
          { label: "Họ tên", render: (r) => <b>{r.name}</b> },
          { label: "Mã tài khoản", render: (r) => r.id },
          { label: "Email", render: (r) => r.email },
          { label: "Vai trò", render: (r) => <Badge>{r.role}</Badge> },
        ]}
      />
    </>
  );
}
export function PhaseTwo() {
  const loc = useLocation(),
    nav = useNavigate();
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">TRUNG TÂM DƯƠNG AN</div>
          <h1>{titles[loc.pathname] ?? "Trang không tồn tại"}</h1>
          <p>Từng bước hoàn thiện không gian đồng hành cùng con.</p>
        </div>
      </div>
      <section className="panel phase-two">
        <span className="phase-icon">
          <BookOpen size={38} />
        </span>
        <Badge>Phát triển ở giai đoạn 2</Badge>
        <h2>Chuẩn bị cho hành trình tiếp theo</h2>
        <p>Chức năng sẽ phát triển ở giai đoạn 2.</p>
        <p>
          {/plan|report/.test(loc.pathname)
            ? "Giáo viên lập kế hoạch và báo cáo, gửi quản trị viên duyệt. Phụ huynh chỉ xem nội dung đã được duyệt và công bố."
            : "Màn hình này đã được bố trí trong cấu trúc hệ thống. Các nghiệp vụ chuyên sâu sẽ được bổ sung ở phiên bản tiếp theo."}
        </p>
        <Button
          icon={<ArrowLeft size={16} />}
          onClick={() => nav("/dashboard")}
        >
          Trở về tổng quan
        </Button>
      </section>
    </>
  );
}
