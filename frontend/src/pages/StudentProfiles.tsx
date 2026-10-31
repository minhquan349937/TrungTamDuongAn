import { useEffect, useState } from "react";
import { Alert, App, Button, Descriptions, Form, Input, Modal, Select, Space, Table, Tag } from "antd";
import { Plus } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { studentProfileApi, type CreateProfileInput, type ProfileInput, type StudentProfile } from "../services/studentProfileApi";
import { dateLabel, today } from "../utils/format";

const statusLabels: Record<StudentProfile["status"], string> = {
  ACTIVE: "Đang học", PAUSED: "Tạm nghỉ", WITHDRAWN: "Ngừng học",
};
const relationshipLabels: Record<NonNullable<StudentProfile["guardian"]>["relationship"], string> = {
  MOTHER: "Mẹ", FATHER: "Bố", GRANDFATHER: "Ông", GRANDMOTHER: "Bà", GUARDIAN: "Người giám hộ", OTHER: "Khác",
};
type FormValues = CreateProfileInput & Partial<Pick<ProfileInput, "code" | "status">>;

export function StudentProfiles() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm<FormValues>();
  const [rows, setRows] = useState<StudentProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editor, setEditor] = useState<StudentProfile | null | undefined>();
  const [detail, setDetail] = useState<StudentProfile | null>(null);
  const [saving, setSaving] = useState(false);

  async function reload() {
    try {
      setError("");
      setRows(await studentProfileApi.list());
    } catch (err) { setError((err as Error).message); }
    finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);
  useEffect(() => {
    if (editor === undefined) return;
    form.resetFields();
    if (editor) form.setFieldsValue({
      code: editor.code, fullName: editor.fullName, birthDate: editor.birthDate,
      admittedOn: editor.admittedOn, status: editor.status, notes: editor.notes,
      guardian: editor.guardian ? {
        fullName: editor.guardian.fullName, phone: editor.guardian.phone,
        email: editor.guardian.email, address: editor.guardian.address,
        relationship: editor.guardian.relationship,
      } : undefined,
    });
    else form.setFieldsValue({ admittedOn: today(), guardian: { relationship: "MOTHER" } });
  }, [editor, form]);

  async function save(values: FormValues) {
    setSaving(true);
    try {
      const input: CreateProfileInput = {
        fullName: values.fullName.trim(), birthDate: values.birthDate,
        admittedOn: values.admittedOn,
        notes: values.notes?.trim() || null,
        guardian: {
          ...values.guardian,
          fullName: values.guardian.fullName.trim(), phone: values.guardian.phone.trim(),
          email: values.guardian.email?.trim() || null,
          address: values.guardian.address?.trim() || null,
        },
      };
      if (editor) await studentProfileApi.update(editor.id, {
        ...input, code: editor.code, status: values.status ?? editor.status,
      });
      else await studentProfileApi.create(input);
      message.success(editor ? "Đã cập nhật hồ sơ" : "Đã tạo hồ sơ học sinh và phụ huynh");
      setEditor(undefined);
      await reload();
    } catch (err) { message.error((err as Error).message); }
    finally { setSaving(false); }
  }
  const required = [{ required: true, message: "Vui lòng nhập thông tin này." }];
  return <>
    <div className="page-heading"><div><div className="eyebrow">QUẢN LÝ HỒ SƠ</div>
      <h1>Hồ sơ học sinh</h1><p>Tạo hồ sơ học sinh và phụ huynh chính trong cùng một lần lưu.</p></div></div>
    {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => void reload()}>Thử lại</Button>} />}
    <section className="panel" style={{ padding: 24 }}>
      <Space style={{ width: "100%", justifyContent: "space-between", marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>Danh sách học sinh</h2>
        <Button type="primary" icon={<Plus size={16} />} onClick={() => setEditor(null)}>Thêm học sinh</Button>
      </Space>
      <Table rowKey="id" loading={loading} dataSource={rows} pagination={{ pageSize: 10 }}
        columns={[
          { title: "Mã", dataIndex: "code" },
          { title: "Học sinh", dataIndex: "fullName", render: (name: string, row: StudentProfile) => <Button type="link" onClick={() => setDetail(row)}>{name}</Button> },
          { title: "Ngày sinh", dataIndex: "birthDate", render: dateLabel },
          { title: "Phụ huynh chính", render: (_: unknown, row: StudentProfile) => row.guardian?.fullName ?? "Chưa có" },
          { title: "Trạng thái", dataIndex: "status", render: (value: StudentProfile["status"]) => <Tag color={value === "ACTIVE" ? "green" : "red"}>{statusLabels[value]}</Tag> },
          { title: "Giáo viên", render: () => "Phân công qua lớp/buổi học" },
          { title: "Thao tác", render: (_: unknown, row: StudentProfile) => <Button onClick={() => setEditor(row)}>Sửa</Button> },
        ]} />
    </section>
    <Modal open={editor !== undefined} title={editor ? "Sửa hồ sơ học sinh" : "Thêm hồ sơ học sinh"}
      onCancel={() => setEditor(undefined)} onOk={() => form.submit()} confirmLoading={saving}
      okText={editor ? "Lưu thay đổi" : "Tạo hồ sơ"} width={680} destroyOnClose>
      <Form form={form} layout="vertical" onFinish={save}>
        <h3>Thông tin học sinh</h3>
        <div className="form-grid">
          <Form.Item name="fullName" label="Họ và tên" rules={[...required, { whitespace: true }]}><Input maxLength={150} /></Form.Item>
          <Form.Item name="birthDate" label="Ngày sinh" rules={required}><Input type="date" max={today()} /></Form.Item>
          <Form.Item name="admittedOn" label="Ngày nhập học" rules={required}><Input type="date" max={today()} /></Form.Item>
          {editor && <Form.Item name="status" label="Trạng thái" rules={required}><Select options={[
            { value: "ACTIVE", label: "Đang học" }, { value: "PAUSED", label: "Tạm nghỉ" },
            ...(editor.status === "WITHDRAWN" ? [{ value: "WITHDRAWN", label: "Ngừng học" }] : []),
          ]} /></Form.Item>}
        </div>
        <Form.Item name="notes" label="Ghi chú"><Input.TextArea rows={2} /></Form.Item>
        <h3>Phụ huynh chính</h3>
        <div className="form-grid">
          <Form.Item name={["guardian", "fullName"]} label="Họ và tên phụ huynh" rules={[...required, { whitespace: true }]}><Input maxLength={150} /></Form.Item>
          <Form.Item name={["guardian", "relationship"]} label="Quan hệ với học sinh" rules={required}><Select options={Object.entries(relationshipLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Form.Item name={["guardian", "phone"]} label="Số điện thoại" rules={[...required, { whitespace: true }]}><Input maxLength={30} type="tel" /></Form.Item>
          <Form.Item name={["guardian", "email"]} label="Email" rules={[{ type: "email", message: "Email không hợp lệ." }]}><Input maxLength={254} /></Form.Item>
        </div>
        <Form.Item name={["guardian", "address"]} label="Địa chỉ"><Input.TextArea rows={2} /></Form.Item>
        <Alert type="info" showIcon message="Có thể phân công giáo viên sau khi tạo hồ sơ, tại lớp hoặc buổi học." />
      </Form>
    </Modal>
    <Modal open={!!detail} title="Hồ sơ học sinh" onCancel={() => setDetail(null)} footer={detail && <Space>
      <Button onClick={() => { setDetail(null); setEditor(detail); }}>Sửa hồ sơ</Button>
      <Button type="primary" onClick={() => navigate(`/individual/plan/${detail.id}`)}>Mở kế hoạch can thiệp</Button>
    </Space>} width={650}>
      {detail && <Descriptions column={1} items={[
        { key: "code", label: "Mã học sinh", children: detail.code },
        { key: "name", label: "Họ tên", children: detail.fullName },
        { key: "birth", label: "Ngày sinh", children: dateLabel(detail.birthDate) },
        { key: "admitted", label: "Ngày nhập học", children: dateLabel(detail.admittedOn) },
        { key: "status", label: "Trạng thái", children: statusLabels[detail.status] },
        { key: "guardian", label: "Phụ huynh chính", children: detail.guardian?.fullName },
        { key: "relationship", label: "Quan hệ", children: detail.guardian && relationshipLabels[detail.guardian.relationship] },
        { key: "phone", label: "Điện thoại", children: detail.guardian?.phone },
        { key: "email", label: "Email", children: detail.guardian?.email },
        { key: "address", label: "Địa chỉ", children: detail.guardian?.address },
        { key: "notes", label: "Ghi chú", children: detail.notes },
      ]} />}
    </Modal>
  </>;
}
