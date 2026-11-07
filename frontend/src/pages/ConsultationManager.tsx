import { useCallback, useEffect, useState } from "react";
import { Alert, App, Button, Card, DatePicker, Descriptions, Drawer, Form, Input, Select, Table, Tag } from "antd";
import type { Dayjs } from "dayjs";
import dayjs from "dayjs";
import { Search } from "lucide-react";
import { consultationStatusLabel, websiteApi } from "../services/websiteApi";
import type { Consultation, ConsultationStatus } from "../services/websiteApi";

const statusColor: Record<ConsultationStatus, string> = { NEW: "blue", CONTACTING: "gold", CONSULTED: "green", CLOSED: "default" };
const when = (iso: string) => dayjs(iso).format("DD/MM/YYYY HH:mm");
const statusOptions = (Object.keys(consultationStatusLabel) as ConsultationStatus[]).map((value) => ({ value, label: consultationStatusLabel[value] }));
const PAGE_SIZE = 20;

function Detail({ item, onClose, onSaved }: { item: Consultation; onClose: () => void; onSaved: () => void }) {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [assignees, setAssignees] = useState<{ value: string; label: string }[]>([]);
  const [current, setCurrent] = useState(item);

  useEffect(() => {
    websiteApi.assignees().then((list) => setAssignees(list.map((a) => ({ value: a.id, label: `${a.name} (${a.role === "ADMIN" ? "Quản trị" : "Giáo viên"})` })))).catch(() => undefined);
  }, []);

  async function save() {
    const values = await form.validateFields();
    setSaving(true);
    try {
      setCurrent(await websiteApi.updateConsultation(item.id, { status: values.status, assignedUserId: values.assignedUserId ?? null, internalNote: values.internalNote ?? "" }));
      message.success("Đã cập nhật yêu cầu tư vấn.");
      onSaved();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Drawer open width={560} onClose={onClose} title={`Yêu cầu của ${item.parentName}`}>
      <Descriptions column={1} size="small" bordered>
        <Descriptions.Item label="Họ tên phụ huynh">{current.parentName}</Descriptions.Item>
        <Descriptions.Item label="Số điện thoại"><a href={`tel:${current.phone}`}>{current.phone}</a></Descriptions.Item>
        <Descriptions.Item label="Độ tuổi của trẻ">{current.childAge || "—"}</Descriptions.Item>
        <Descriptions.Item label="Nội dung cần tư vấn"><span className="wm-pre">{current.message || "—"}</span></Descriptions.Item>
        <Descriptions.Item label="Đồng ý được liên hệ">{when(current.consentedAt)}</Descriptions.Item>
        <Descriptions.Item label="Ngày gửi">{when(current.createdAt)}</Descriptions.Item>
        <Descriptions.Item label="Cập nhật lần cuối">{when(current.updatedAt)}{current.updatedByName ? ` · ${current.updatedByName}` : ""}</Descriptions.Item>
      </Descriptions>
      <Form form={form} name="consultation" layout="vertical" className="wm-detail-form"
        initialValues={{ status: current.status, assignedUserId: current.assignedUserId ?? undefined, internalNote: current.internalNote ?? "" }}>
        <Form.Item name="status" label="Trạng thái" rules={[{ required: true }]}><Select options={statusOptions} /></Form.Item>
        <Form.Item name="assignedUserId" label="Người phụ trách"><Select allowClear showSearch optionFilterProp="label" placeholder="Chưa gán" options={assignees} /></Form.Item>
        <Form.Item name="internalNote" label="Ghi chú nội bộ" rules={[{ max: 4000, message: "Tối đa 4000 ký tự." }]}>
          <Input.TextArea rows={5} maxLength={4000} showCount placeholder="Chỉ nhân viên trung tâm nhìn thấy" />
        </Form.Item>
        <Button type="primary" loading={saving} onClick={() => void save()}>Lưu cập nhật</Button>
      </Form>
    </Drawer>
  );
}

export function ConsultationManager({ onChanged }: { onChanged?: () => void }) {
  const [text, setText] = useState("");
  const [q, setQ] = useState("");
  const [status, setStatus] = useState<string | undefined>();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<{ items: Consultation[]; total: number }>({ items: [], total: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<Consultation | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setError("");
      setData(await websiteApi.consultations({
        q: q || undefined, status, from: range?.[0]?.format("YYYY-MM-DD"), to: range?.[1]?.format("YYYY-MM-DD"), page, size: PAGE_SIZE,
      }));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [q, status, range, page]);
  useEffect(() => { void load(); }, [load]);

  const filter = (apply: () => void) => { apply(); setPage(0); };

  return (
    <Card size="small" title="Yêu cầu tư vấn từ website">
      <div className="wm-filters">
        <Input.Search allowClear placeholder="Tìm theo tên hoặc số điện thoại" value={text} onChange={(e) => setText(e.target.value)}
          onSearch={(value) => filter(() => setQ(value.trim()))} enterButton={<Search size={16} aria-label="Tìm" />} />
        <Select allowClear placeholder="Mọi trạng thái" options={statusOptions} value={status} onChange={(value) => filter(() => setStatus(value))} />
        <DatePicker.RangePicker format="DD/MM/YYYY" placeholder={["Từ ngày", "Đến ngày"]} value={range}
          onChange={(value) => filter(() => setRange(value))} />
      </div>
      {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>Tải lại</Button>} />}
      <Table<Consultation> rowKey="id" size="small" loading={loading} dataSource={data.items} scroll={{ x: "max-content" }}
        locale={{ emptyText: "Chưa có yêu cầu tư vấn nào" }}
        pagination={{ current: page + 1, pageSize: PAGE_SIZE, total: data.total, showSizeChanger: false, showTotal: (total) => `${total} yêu cầu`, onChange: (next) => setPage(next - 1) }}
        columns={[
          { title: "Ngày gửi", render: (_, r) => when(r.createdAt) },
          { title: "Phụ huynh", render: (_, r) => <b>{r.parentName}</b> },
          { title: "Điện thoại", dataIndex: "phone" },
          { title: "Độ tuổi", render: (_, r) => r.childAge || "—" },
          { title: "Trạng thái", render: (_, r) => <Tag color={statusColor[r.status]}>{consultationStatusLabel[r.status]}</Tag> },
          { title: "Phụ trách", render: (_, r) => r.assignedName || "—" },
          { title: "", render: (_, r) => <Button size="small" onClick={() => setSelected(r)}>Xem</Button> },
        ]} />
      {selected && <Detail key={selected.id} item={selected} onClose={() => setSelected(null)} onSaved={() => { void load(); onChanged?.(); }} />}
    </Card>
  );
}
