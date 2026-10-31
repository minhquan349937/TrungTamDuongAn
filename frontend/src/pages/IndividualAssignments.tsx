import { useEffect, useState } from "react";
import { Alert, App, Button, Form, Modal, Select, Table } from "antd";
import { Plus } from "lucide-react";
import { individualAssignmentApi, type AssignmentTeacher, type IndividualAssignment } from "../services/individualAssignmentApi";
import { studentProfileApi, type StudentProfile } from "../services/studentProfileApi";
import { dateLabel } from "../utils/format";

export function IndividualAssignments() {
  const { message } = App.useApp();
  const [rows, setRows] = useState<IndividualAssignment[]>([]);
  const [teachers, setTeachers] = useState<AssignmentTeacher[]>([]);
  const [students, setStudents] = useState<StudentProfile[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editor, setEditor] = useState<IndividualAssignment | null | undefined>();
  const [form] = Form.useForm<{ studentId: string; teacherId: string }>();
  async function reload() {
    setLoading(true);
    try {
      const [assignments, staff, profiles] = await Promise.all([
        individualAssignmentApi.list(), individualAssignmentApi.teachers(), studentProfileApi.list(),
      ]);
      setRows(assignments); setTeachers(staff); setStudents(profiles); setError("");
    } catch (cause) { setError((cause as Error).message); }
    finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);
  useEffect(() => {
    if (editor === undefined) return;
    form.resetFields();
    if (editor) form.setFieldsValue({ studentId: editor.studentId, teacherId: editor.teacherId });
  }, [editor, form]);
  return <>
    <div className="page-heading"><div><div className="eyebrow">QUẢN LÝ LỚP CÁ NHÂN</div>
      <h1>Phân công giáo viên</h1><p>Giáo viên được phân công có thể xem kế hoạch và đánh giá mục tiêu của học sinh.</p>
    </div><Button type="primary" icon={<Plus size={16} />} onClick={() => setEditor(null)}>Thêm phân công</Button></div>
    {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => void reload()}>Thử lại</Button>} />}
    <section className="panel" style={{ padding: 24 }}>
      <Table rowKey="id" dataSource={rows} loading={loading} pagination={{ pageSize: 10 }} columns={[
        { title: "Học sinh", dataIndex: "studentName" },
        { title: "Mã học sinh", dataIndex: "studentCode" },
        { title: "Giáo viên phụ trách", dataIndex: "teacherName" },
        { title: "Ngày bắt đầu", dataIndex: "startsOn", render: dateLabel },
        { title: "Thao tác", render: (_: unknown, row: IndividualAssignment) => <Button onClick={() => setEditor(row)}>Đổi giáo viên</Button> },
      ]} />
    </section>
    <Modal open={editor !== undefined} title={editor ? "Đổi giáo viên phụ trách" : "Phân công giáo viên"}
      onCancel={() => setEditor(undefined)} onOk={() => form.submit()} confirmLoading={saving} okText="Lưu phân công" cancelText="Hủy" destroyOnClose>
      <Form form={form} layout="vertical" onFinish={async values => {
        setSaving(true);
        try {
          if (editor) await individualAssignmentApi.change(editor.id, values.teacherId);
          else await individualAssignmentApi.create(values.studentId, values.teacherId);
          setEditor(undefined); message.success("Đã lưu phân công giáo viên"); await reload();
        } catch (cause) { message.error((cause as Error).message); }
        finally { setSaving(false); }
      }}>
        <Form.Item name="studentId" label="Học sinh" rules={[{ required: true, message: "Chọn học sinh." }]}>
          <Select aria-label="Học sinh" showSearch optionFilterProp="label" disabled={!!editor} options={students.filter(s => s.status === "ACTIVE" || s.id === editor?.studentId).map(s => ({ value: s.id, label: `${s.code} · ${s.fullName}` }))} />
        </Form.Item>
        <Form.Item name="teacherId" label="Giáo viên phụ trách" rules={[{ required: true, message: "Chọn giáo viên." }]}>
          <Select aria-label="Giáo viên phụ trách" showSearch optionFilterProp="label" options={teachers.map(t => ({ value: t.id, label: `${t.code} · ${t.fullName}` }))} />
        </Form.Item>
        {!teachers.length && <Alert type="warning" showIcon message="Chưa có nhân viên đang làm việc được liên kết với tài khoản giáo viên hoạt động." />}
      </Form>
    </Modal>
  </>;
}
