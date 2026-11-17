import { useEffect, useState } from "react";
import { Alert, App, Button, Form, Input, Modal, Select, Table, Tag } from "antd";
import { accountApi, type Account, type AccountUpdate } from "../services/accountApi";
import { useAuth } from "../auth/AuthProvider";

const roles = { ADMIN: "Quản trị viên", TEACHER: "Giáo viên", PARENT: "Phụ huynh" };
const statuses = { ACTIVE: "Hoạt động", LOCKED: "Đã khóa", DISABLED: "Ngừng hoạt động" };
export function Accounts() {
  const { message } = App.useApp();
  const { user, logout } = useAuth();
  const [rows, setRows] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<Account | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<AccountUpdate & { confirmPassword?: string }>();
  const selectedRole = Form.useWatch("role", form);
  async function reload() {
    setLoading(true);
    try { setRows(await accountApi.list()); setError(""); }
    catch (cause) { setError((cause as Error).message); }
    finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);
  useEffect(() => {
    if (editing) { form.resetFields(); form.setFieldsValue({ ...editing, password: "", confirmPassword: "" }); }
  }, [editing, form]);
  const term = search.trim().toLocaleLowerCase("vi-VN");
  return <>
    <div className="page-heading"><div><div className="eyebrow">QUẢN LÝ HỆ THỐNG</div><h1>Tài khoản & phân quyền</h1><p>Đăng nhập bằng số điện thoại hoặc email đã đăng ký.</p></div></div>
    {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => void reload()}>Thử lại</Button>} />}
    <section className="panel" style={{ padding: 24 }}>
      <Input.Search aria-label="Tìm kiếm tài khoản" placeholder="Tên, mã tài khoản, số điện thoại hoặc email" value={search} onChange={e => setSearch(e.target.value)} allowClear style={{ maxWidth: 480, marginBottom: 20 }} />
      <Table rowKey="id" loading={loading} pagination={{ pageSize: 10 }} scroll={{ x: 900 }}
        dataSource={rows.filter(row => `${row.id} ${row.displayName} ${row.email ?? ""} ${row.phone ?? ""}`.toLocaleLowerCase("vi-VN").includes(term))}
        locale={{ emptyText: "Không có tài khoản phù hợp." }} columns={[
          { title: "Họ tên", dataIndex: "displayName" }, { title: "Mã tài khoản", dataIndex: "id" },
          { title: "Số điện thoại", dataIndex: "phone", render: (value: string | null) => value || "Chưa có" },
          { title: "Email", dataIndex: "email", render: (value: string | null) => value || "—" },
          { title: "Vai trò", dataIndex: "role", render: (role: Account["role"]) => <Tag>{roles[role]}</Tag> },
          { title: "Trạng thái", dataIndex: "status", render: (status: Account["status"]) => <Tag color={status === "ACTIVE" ? "green" : "red"}>{statuses[status]}</Tag> },
          { title: "Thao tác", render: (_: unknown, row: Account) => <Button onClick={() => setEditing(row)}>Chỉnh sửa</Button> },
        ]} />
    </section>
    <Modal title={`Chỉnh sửa tài khoản · ${editing?.displayName ?? ""}`} open={!!editing} onCancel={() => { if (!saving) setEditing(null); }}
      onOk={() => form.submit()} okText="Lưu thay đổi" confirmLoading={saving} cancelButtonProps={{ disabled: saving }} closable={!saving} maskClosable={!saving} destroyOnClose>
      <Form form={form} layout="vertical" onFinish={async values => {
        if (!editing) return;
        if (!values.email?.trim() && !values.phone?.trim()) { message.error("Cần nhập số điện thoại hoặc email đăng nhập."); return; }
        setSaving(true);
        try {
          await accountApi.update(editing.id, {
            displayName: values.displayName.trim(), email: values.email?.trim() || null, phone: values.phone?.trim() || null,
            role: values.role, status: values.status, ...(values.password ? { password: values.password } : {}),
          });
          const requiresLogin = editing.id === user?.id && (values.password || values.role !== editing.role || values.status !== "ACTIVE");
          setEditing(null); message.success("Đã cập nhật tài khoản.");
          if (requiresLogin) { await logout(); return; }
          await reload();
        } catch (cause) { message.error((cause as Error).message); }
        finally { setSaving(false); }
      }}>
        <Form.Item name="displayName" label="Họ tên" rules={[{ required: true, whitespace: true, message: "Vui lòng nhập họ tên." }]}><Input maxLength={150} /></Form.Item>
        <Form.Item name="phone" label="Số điện thoại" rules={selectedRole === "PARENT" ? [{ required: true, whitespace: true, message: "Phụ huynh cần số điện thoại đăng nhập." }] : []}><Input type="tel" maxLength={30} /></Form.Item>
        <Form.Item name="email" label="Email" rules={[{ type: "email", message: "Email không hợp lệ." }]}><Input maxLength={254} /></Form.Item>
        <Form.Item name="role" label="Vai trò" rules={[{ required: true, message: "Vui lòng chọn vai trò." }]} extra={editing?.allowedRoles && editing.allowedRoles.length < 3 ? "Vai trò phải phù hợp với hồ sơ nhân viên hoặc phụ huynh đã liên kết." : undefined}>
          <Select options={Object.entries(roles).map(([value, label]) => ({ value, label, disabled: !!editing?.allowedRoles && !editing.allowedRoles.includes(value as Account["role"]) }))} />
        </Form.Item>
        <Form.Item name="status" label="Trạng thái" rules={[{ required: true, message: "Vui lòng chọn trạng thái." }]}><Select options={Object.entries(statuses).map(([value, label]) => ({ value, label }))} /></Form.Item>
        <Form.Item name="password" label="Mật khẩu mới" extra="Để trống để giữ mật khẩu hiện tại." rules={[{ min: 6, message: "Mật khẩu cần ít nhất 6 ký tự." }, { validator: async (_, value) => { if (value && (value.trim().length === 0 || new TextEncoder().encode(value).length > 72)) throw new Error("Mật khẩu phải có nội dung và không vượt quá 72 byte."); } }]}><Input.Password autoComplete="new-password" /></Form.Item>
        <Form.Item name="confirmPassword" label="Xác nhận mật khẩu mới" dependencies={["password"]} rules={[({ getFieldValue }) => ({ validator: async (_, value) => { if ((getFieldValue("password") || value) && value !== getFieldValue("password")) throw new Error("Mật khẩu xác nhận không khớp."); } })]}><Input.Password autoComplete="new-password" /></Form.Item>
      </Form>
    </Modal>
  </>;
}
