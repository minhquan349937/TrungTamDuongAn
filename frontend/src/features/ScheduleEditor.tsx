import { useEffect, useState } from "react";
import { Alert, Form, Input, Modal, Select } from "antd";
import type { Session } from "../types";
import type { ScheduleData, ScheduleInput } from "../services/scheduleApi";

export function ScheduleEditor({ record, date, data, onSave, onClose }: {
  record?: Session; date: string; data: ScheduleData; onSave: (input: ScheduleInput) => Promise<void>; onClose: () => void;
}) {
  const [form] = Form.useForm<ScheduleInput>();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const classId = Form.useWatch("classId", form);
  const selected = data.classes.find(cls => cls.id === classId);
  useEffect(() => { form.resetFields(); form.setFieldsValue(record ? { classId: record.classId, date: record.date, start: record.start, end: record.end, room: record.room } : { date }); }, [record, date, form]);
  const required = [{ required: true, message: "Vui lòng nhập thông tin này." }];
  return <Modal open title={record ? "Đổi lịch học" : "Tạo lịch học"} onCancel={() => { if (!saving) onClose(); }}
    onOk={() => form.submit()} okText={record ? "Lưu thay đổi" : "Lưu lịch học"} cancelText="Hủy" confirmLoading={saving}
    cancelButtonProps={{ disabled: saving }} closable={!saving} maskClosable={!saving}>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    {!data.classes.some(cls => cls.status === "Đang hoạt động" && cls.teacherAvailable) && <Alert type="info" showIcon message="Chưa có lớp hoạt động với giáo viên có tài khoản hoạt động. Hãy tạo lớp hoặc kiểm tra phân công giáo viên." />}
    <Form form={form} layout="vertical" onFinish={async values => {
      setSaving(true); setError("");
      try { await onSave({ ...values, room: values.room.trim() }); }
      catch (cause) { setError((cause as Error).message); }
      finally { setSaving(false); }
    }}>
      <Form.Item name="classId" label="Lớp học" rules={required}><Select showSearch optionFilterProp="label" disabled={!!record}
        placeholder="Chọn lớp học đã lưu trên hệ thống" options={data.classes.map(cls => ({ value: cls.id,
          label: `${cls.name} · ${data.staff.find(t => t.id === cls.teacherId)?.name ?? "Chưa có giáo viên"}`,
          disabled: cls.status !== "Đang hoạt động" || !cls.teacherAvailable,
        }))} notFoundContent="Chưa có lớp học trong CSDL" /></Form.Item>
      <Form.Item label="Giáo viên phụ trách"><Input aria-label="Giáo viên phụ trách" readOnly value={data.staff.find(t => t.id === selected?.teacherId)?.name ?? ""} placeholder="Giáo viên theo phân công của lớp" /></Form.Item>
      <Form.Item name="date" label="Ngày học" rules={required}><Input type="date" /></Form.Item>
      <div className="form-grid">
        <Form.Item name="start" label="Bắt đầu" rules={required}><Input type="time" /></Form.Item>
        <Form.Item name="end" label="Kết thúc" dependencies={["start"]} rules={[...required, ({ getFieldValue }) => ({ validator: async (_, value) => { if (value && getFieldValue("start") && value <= getFieldValue("start")) throw new Error("Giờ kết thúc phải sau giờ bắt đầu."); } })]}><Input type="time" /></Form.Item>
      </div>
      <Form.Item name="room" label="Phòng học" rules={[...required, { whitespace: true, message: "Vui lòng nhập phòng học." }]}><Input maxLength={250} placeholder="Nhập phòng học" /></Form.Item>
      {record && <Form.Item name="changeReason" label="Lý do đổi lịch"><Input.TextArea rows={2} maxLength={4000} /></Form.Item>}
      <p>Lịch học được lưu vào hệ thống và hiển thị cho giáo viên, phụ huynh được liên kết.</p>
    </Form>
  </Modal>;
}
