import { useEffect, useState } from "react";
import { Alert, App, Button, Descriptions, Form, Input, Modal, Select } from "antd";
import { Plus } from "lucide-react";
import { DataTable, Avatar, Badge } from "../components/ui";
import { age, dateLabel, today } from "../utils/format";
import { studentProfileApi, type StudentProfile } from "../services/studentProfileApi";
import { staffProfileApi, type StaffInput, type StaffProfile } from "../services/staffProfileApi";

const studentStatuses: Record<StudentProfile["status"], string> = {
  ACTIVE: "Đang học", PAUSED: "Tạm nghỉ", WITHDRAWN: "Ngừng học",
};
const staffStatuses: Record<StaffProfile["status"], string> = {
  ACTIVE: "Đang làm việc", ON_LEAVE: "Nghỉ phép", TERMINATED: "Ngừng làm việc",
};
const required = [{ required: true, message: "Vui lòng nhập thông tin này." }];

export function DashboardStudentsTable() {
  const [rows, setRows] = useState<StudentProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [detail, setDetail] = useState<StudentProfile | null>(null);

  async function reload() {
    try { setRows(await studentProfileApi.list()); setError(""); }
    catch (err) { setError((err as Error).message); }
    finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  return <>
    {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => void reload()}>Thử lại</Button>} />}
    <DataTable title="Học sinh gần đây" 
      rows={rows} pageSize={3} searchText={(s) => `${s.fullName} ${s.code} ${s.guardian?.fullName ?? ""}`}
      statuses={Object.values(studentStatuses)} statusOf={(s) => studentStatuses[s.status]}
      columns={[
        { label: "Học sinh", render: (s, i) => <div className="person-cell"><Avatar name={s.fullName} index={i} />
          <div><button className="name-link" onClick={() => setDetail(s)}>{s.fullName}</button><small>Mã: {s.code}</small></div></div> },
        { label: "Độ tuổi", render: (s) => age(s.birthDate) },
        { label: "Ngày nhập học", render: (s) => dateLabel(s.admittedOn) },
        { label: "Phụ huynh", render: (s) => s.guardian?.fullName ?? "Chưa có" },
        { label: "Trạng thái", render: (s) => <Badge tone={s.status === "ACTIVE" ? "green" : "red"}>{studentStatuses[s.status]}</Badge> },
      ]} />
    {loading && <div className="empty-inline">Đang tải hồ sơ học sinh…</div>}
    <Modal open={!!detail} title="Hồ sơ học sinh" onCancel={() => setDetail(null)} footer={null} width={650}>
      {detail && <Descriptions column={1} items={[
        { key: "code", label: "Mã học sinh", children: detail.code },
        { key: "name", label: "Họ tên", children: detail.fullName },
        { key: "birth", label: "Ngày sinh", children: dateLabel(detail.birthDate) },
        { key: "admitted", label: "Ngày nhập học", children: dateLabel(detail.admittedOn) },
        { key: "status", label: "Trạng thái", children: studentStatuses[detail.status] },
        { key: "guardian", label: "Phụ huynh chính", children: detail.guardian?.fullName },
        { key: "phone", label: "Điện thoại", children: detail.guardian?.phone },
      ]} />}
    </Modal>
  </>;
}

export function DashboardStaffTable({ onChanged, compact = true }: { onChanged?: () => void; compact?: boolean }) {
  const { message } = App.useApp();
  const [rows, setRows] = useState<StaffProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editor, setEditor] = useState<StaffProfile | null | undefined>();
  const [detail, setDetail] = useState<StaffProfile | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<StaffInput>();

  async function reload() {
    try { setRows(await staffProfileApi.list()); setError(""); }
    catch (err) { setError((err as Error).message); }
    finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);
  useEffect(() => {
    if (editor === undefined) return;
    form.resetFields();
    if (editor) form.setFieldsValue({
      fullName: editor.fullName, position: editor.position, specialty: editor.specialty,
      phone: editor.phone, email: editor.email, joinedOn: editor.joinedOn, status: editor.status,
    });
    else form.setFieldsValue({ joinedOn: today(), status: "ACTIVE" });
  }, [editor, form]);

  async function save(values: StaffInput) {
    setSaving(true);
    try {
      const input: StaffInput = {
        fullName: values.fullName.trim(), position: values.position?.trim() || null,
        specialty: values.specialty?.trim() || null, phone: values.phone?.trim() || null,
        email: values.email?.trim() || null, joinedOn: values.joinedOn, status: values.status,
      };
      if (editor) await staffProfileApi.update(editor.id, input);
      else await staffProfileApi.create(input);
      message.success(editor ? "Đã cập nhật hồ sơ nhân viên" : "Đã tạo hồ sơ nhân viên");
      setEditor(undefined);
      await reload();
      onChanged?.();
    } catch (err) { message.error((err as Error).message); }
    finally { setSaving(false); }
  }

  return <>
    {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => void reload()}>Thử lại</Button>} />}
    <DataTable title={compact ? "Giáo viên & nhân viên" : "Hồ sơ nhân sự"}

      rows={rows} pageSize={compact ? 3 : 10}
      searchText={(s) => `${s.fullName} ${s.code} ${s.specialty ?? ""}`}
      statuses={Object.values(staffStatuses)} statusOf={(s) => staffStatuses[s.status]}
      action={!compact && <Button className="outline-action" icon={<Plus size={16} />} onClick={() => setEditor(null)}>Thêm giáo viên, nhân viên</Button>}
      columns={[
        { label: "Nhân viên", render: (s, i) => <div className="person-cell"><Avatar name={s.fullName} index={i + 1} />
          <div><button className="name-link" onClick={() => setDetail(s)}>{s.fullName}</button><small>Mã: {s.code}</small></div></div> },
        { label: "Chức vụ", render: (s) => s.position ?? "—" },
        { label: "Chuyên môn", render: (s) => s.specialty ?? "—" },
        { label: "Ngày vào làm", render: (s) => dateLabel(s.joinedOn) },
        { label: "Trạng thái", render: (s) => <Badge tone={s.status === "ACTIVE" ? "green" : "red"}>{staffStatuses[s.status]}</Badge> },
        ...(!compact ? [{ label: "Thao tác", render: (s: StaffProfile) => <Button size="small" onClick={() => setEditor(s)}>Sửa</Button> }] : []),
      ]} />
    {loading && <div className="empty-inline">Đang tải hồ sơ nhân viên…</div>}
    <Modal open={editor !== undefined} title={editor ? "Sửa hồ sơ nhân viên" : "Thêm hồ sơ nhân viên"}
      onCancel={() => setEditor(undefined)} onOk={() => form.submit()} confirmLoading={saving}
      okText={editor ? "Lưu thay đổi" : "Tạo hồ sơ"} width={680} destroyOnClose>
      <Form form={form} layout="vertical" onFinish={save}>
        <h3>Thông tin nhân viên</h3>
        <div className="form-grid">
          <Form.Item name="fullName" label="Họ và tên" rules={[...required, { whitespace: true }]}><Input maxLength={150} /></Form.Item>
          <Form.Item name="position" label="Chức vụ"><Input maxLength={150} /></Form.Item>
          <Form.Item name="specialty" label="Chuyên môn"><Input maxLength={150} /></Form.Item>
          <Form.Item name="joinedOn" label="Ngày vào làm" rules={required}><Input type="date" max={today()} /></Form.Item>
          <Form.Item name="phone" label="Số điện thoại"><Input maxLength={30} type="tel" /></Form.Item>
          <Form.Item name="email" label="Email" rules={[{ type: "email", message: "Email không hợp lệ." }]}><Input maxLength={254} /></Form.Item>
          <Form.Item name="status" label="Trạng thái" rules={required}><Select options={Object.entries(staffStatuses).map(([value, label]) => ({ value, label }))} /></Form.Item>
        </div>
      </Form>
    </Modal>
    <Modal open={!!detail} title="Hồ sơ nhân viên" onCancel={() => setDetail(null)} footer={null} width={650}>
      {detail && <Descriptions column={1} items={[
        { key: "code", label: "Mã nhân viên", children: detail.code },
        { key: "name", label: "Họ tên", children: detail.fullName },
        { key: "position", label: "Chức vụ", children: detail.position },
        { key: "specialty", label: "Chuyên môn", children: detail.specialty },
        { key: "phone", label: "Số điện thoại", children: detail.phone },
        { key: "email", label: "Email", children: detail.email },
        { key: "joined", label: "Ngày vào làm", children: dateLabel(detail.joinedOn) },
        { key: "status", label: "Trạng thái", children: staffStatuses[detail.status] },
      ]} />}
    </Modal>
  </>;
}
