import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Alert, App, Button, Card, Checkbox, Drawer, Empty, Form, Input, Modal, Select, Spin, Switch, Table, Tag } from "antd";
import { ArrowDown, ArrowUp, ImagePlus, Pencil, Plus, Trash2 } from "lucide-react";
import { adminMediaUrl, websiteApi } from "../services/websiteApi";
import type { AdminMedia, AdminRecord } from "../services/websiteApi";

/** Lets every editor tell the page that the draft changed, so the "unpublished changes" badge refreshes. */
export const WebsiteContext = createContext<{ changed: () => void }>({ changed: () => {} });
const useChanged = () => useContext(WebsiteContext).changed;

export type FieldDef = {
  name: string;
  label: string;
  type: "text" | "textarea" | "select" | "image" | "switch";
  required?: boolean;
  max?: number;
  rows?: number;
  options?: { value: string; label: string }[];
  help?: string;
};

const errorText = (error: unknown) => (error as Error).message || "Có lỗi xảy ra. Vui lòng thử lại.";

export function SampleTag({ record }: { record?: AdminRecord }) {
  return record?.sample ? <Tag color="gold" title="Nội dung do hệ thống tạo sẵn, hãy thay bằng thông tin thật">Nội dung mẫu</Tag> : null;
}

/** Image chosen from a fresh upload. Public display needs the "allowed to publish" confirmation. */
export function ImageField({ value, onChange }: { value?: string | null; onChange?: (id: string | null) => void }) {
  const { message } = App.useApp();
  const [meta, setMeta] = useState<AdminMedia | null>(null);
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!value) { setMeta(null); return; }
    let active = true;
    websiteApi.mediaGet(value).then((m) => { if (active) { setMeta(m); setConsent(m.publicConsent); } }).catch(() => active && setMeta(null));
    return () => { active = false; };
  }, [value]);

  async function choose(file: File | undefined) {
    if (!file) return;
    setBusy(true);
    try {
      const saved = await websiteApi.upload(file, { publicConsent: consent });
      setMeta(saved);
      onChange?.(saved.id);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
      if (input.current) input.current.value = "";
    }
  }

  async function changeConsent(next: boolean) {
    setConsent(next);
    if (!meta) return;
    try {
      setMeta(await websiteApi.mediaUpdate(meta.id, { publicConsent: next }));
    } catch (error) {
      setConsent(!next);
      message.error(errorText(error));
    }
  }

  return (
    <div className="wm-image">
      <div className="wm-image-preview">
        {value && meta ? <img src={adminMediaUrl(value)} alt="Ảnh đã tải lên" /> : <span>Chưa có ảnh</span>}
      </div>
      <div className="wm-image-actions">
        <input ref={input} type="file" hidden accept="image/jpeg,image/png,image/webp" onChange={(e) => void choose(e.target.files?.[0])} />
        <Button icon={<ImagePlus size={16} />} loading={busy} onClick={() => input.current?.click()}>{value ? "Đổi ảnh" : "Tải ảnh lên"}</Button>
        {value && <Button danger type="text" onClick={() => onChange?.(null)}>Gỡ ảnh</Button>}
        <Checkbox checked={consent} onChange={(e) => void changeConsent(e.target.checked)}>
          Tôi xác nhận ảnh này được phép công khai
        </Checkbox>
        <small>JPG, PNG hoặc WebP, tối đa 5 MB. Ảnh chưa được xác nhận sẽ không hiển thị trên website.</small>
      </div>
    </div>
  );
}

function renderField(field: FieldDef) {
  const rules = [
    ...(field.required ? [{ required: true, whitespace: true, message: `Vui lòng nhập ${field.label.toLowerCase()}.` }] : []),
    ...(field.max ? [{ max: field.max, message: `${field.label} tối đa ${field.max} ký tự.` }] : []),
  ];
  const control =
    field.type === "textarea" ? <Input.TextArea rows={field.rows ?? 4} maxLength={field.max} showCount />
    : field.type === "select" ? <Select options={field.options} />
    : field.type === "image" ? <ImageField />
    : field.type === "switch" ? <Switch />
    : <Input maxLength={field.max} />;
  return (
    <Form.Item key={field.name} name={field.name} label={field.label} rules={rules} extra={field.help}
      valuePropName={field.type === "switch" ? "checked" : "value"}>
      {control}
    </Form.Item>
  );
}

function EditModal({ title, fields, initial, onCancel, onSubmit }: {
  title: string; fields: FieldDef[]; initial: Record<string, unknown>; onCancel: () => void;
  onSubmit: (values: Record<string, unknown>) => Promise<void>;
}) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  return (
    <Modal open title={title} onCancel={onCancel} okText="Lưu bản nháp" cancelText="Hủy" confirmLoading={saving} width={640} maskClosable={false}
      onOk={async () => {
        const values = await form.validateFields();
        setSaving(true);
        try { await onSubmit(values); } finally { setSaving(false); }
      }}>
      <Form form={form} name="website-edit" layout="vertical" initialValues={initial}>{fields.map(renderField)}</Form>
    </Modal>
  );
}

type ListProps = {
  resource: string;
  section?: string;
  title: string;
  addLabel: string;
  fields: FieldDef[];
  columns: { title: string; render: (record: AdminRecord) => ReactNode }[];
  /** Values forced on every save, e.g. the owning section of a list item. */
  fixed?: Record<string, unknown>;
  extraActions?: (record: AdminRecord) => ReactNode;
};

export function ListEditor({ resource, section, title, addLabel, fields, columns, fixed, extraActions }: ListProps) {
  const { message, modal } = App.useApp();
  const changed = useChanged();
  const [rows, setRows] = useState<AdminRecord[] | null>(null);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState<AdminRecord | "new" | null>(null);

  const load = useCallback(async () => {
    try { setError(""); setRows(await websiteApi.list(resource, section)); } catch (e) { setError(errorText(e)); }
  }, [resource, section]);
  useEffect(() => { void load(); }, [load]);

  async function run(action: () => Promise<unknown>, success?: string) {
    try {
      await action();
      if (success) message.success(success);
      await load();
      changed();
    } catch (e) {
      message.error(errorText(e));
    }
  }

  const move = (index: number, delta: number) => {
    if (!rows) return;
    const ids = rows.map((r) => r.id);
    [ids[index], ids[index + delta]] = [ids[index + delta], ids[index]];
    void run(() => websiteApi.reorder(resource, ids));
  };

  const remove = (record: AdminRecord) =>
    modal.confirm({
      title: "Xóa mục này?", content: "Mục sẽ bị xóa khỏi bản nháp. Website công khai chỉ đổi khi bạn xuất bản.",
      okText: "Xóa", okType: "danger", cancelText: "Hủy",
      onOk: () => run(() => websiteApi.remove(resource, record.id), "Đã xóa."),
    });

  const initial = (record: AdminRecord | "new") =>
    record === "new" ? { visible: true, ...Object.fromEntries(fields.filter((f) => f.type === "select" && f.options?.length === 1).map((f) => [f.name, f.options![0].value])) }
      : Object.fromEntries(fields.map((f) => [f.name, record[f.name]]));

  return (
    <Card size="small" title={title} extra={<Button type="primary" icon={<Plus size={16} />} onClick={() => setEditing("new")}>{addLabel}</Button>}>
      {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load()}>Tải lại</Button>} />}
      {!rows && !error && <Spin />}
      {rows && (
        <Table<AdminRecord> rowKey="id" size="small" pagination={false} dataSource={rows} scroll={{ x: "max-content" }}
          locale={{ emptyText: <Empty description="Chưa có mục nào" /> }}
          columns={[
            { title: "Thứ tự", width: 96, render: (_, __, index) => (
              <span className="wm-order">
                <Button size="small" type="text" aria-label="Chuyển lên" disabled={index === 0} icon={<ArrowUp size={15} />} onClick={() => move(index, -1)} />
                <Button size="small" type="text" aria-label="Chuyển xuống" disabled={index === rows.length - 1} icon={<ArrowDown size={15} />} onClick={() => move(index, 1)} />
              </span>) },
            ...columns.map((c) => ({ title: c.title, render: (_: unknown, r: AdminRecord) => c.render(r) })),
            { title: "Hiển thị", width: 90, render: (_, r) => (
              <Switch checked={Boolean(r.visible)} aria-label="Hiển thị trên website"
                onChange={(visible) => void run(() => websiteApi.update(resource, r.id, { ...r, visible }))} />) },
            { title: "", width: 190, render: (_, r) => (
              <span className="wm-actions">
                <SampleTag record={r} />
                {extraActions?.(r)}
                <Button size="small" icon={<Pencil size={14} />} onClick={() => setEditing(r)}>Sửa</Button>
                <Button size="small" danger icon={<Trash2 size={14} />} aria-label="Xóa" onClick={() => remove(r)} />
              </span>) },
          ]} />
      )}
      {editing && (
        <EditModal title={editing === "new" ? addLabel : "Sửa nội dung"} fields={fields} initial={initial(editing)} onCancel={() => setEditing(null)}
          onSubmit={async (values) => {
            try {
              const body = { ...(editing === "new" ? {} : editing), ...values, ...fixed };
              if (editing === "new") await websiteApi.create(resource, body); else await websiteApi.update(resource, editing.id, body);
              message.success("Đã lưu bản nháp. Website công khai chưa thay đổi.");
              setEditing(null);
              await load();
              changed();
            } catch (e) {
              message.error(errorText(e));
            }
          }} />
      )}
    </Card>
  );
}

/** One fixed record: a website section heading/text, or the contact settings. */
export function SingleEditor({ resource, id, title, fields, hint }: { resource: string; id: string; title: string; fields: FieldDef[]; hint?: string }) {
  const { message } = App.useApp();
  const changed = useChanged();
  const [form] = Form.useForm();
  const [record, setRecord] = useState<AdminRecord | null>(null);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const fieldsRef = useRef(fields); // callers pass inline arrays; depending on them would reload on every render
  fieldsRef.current = fields;

  const load = useCallback(async () => {
    try {
      const found = (await websiteApi.list(resource)).find((r) => r.id === id);
      if (!found) throw new Error("Không tìm thấy nội dung này. Hãy kiểm tra đã chạy migration V14.");
      setRecord(found);
      form.setFieldsValue(Object.fromEntries(fieldsRef.current.map((f) => [f.name, found[f.name]])));
    } catch (e) { setError(errorText(e)); }
  }, [resource, id, form]);
  useEffect(() => { void load(); }, [load]);

  async function save() {
    const values = await form.validateFields();
    setSaving(true);
    try {
      setRecord(await websiteApi.update(resource, id, { ...record, ...values }));
      message.success("Đã lưu bản nháp. Website công khai chưa thay đổi.");
      changed();
    } catch (e) {
      message.error(errorText(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card size="small" title={<span>{title} <SampleTag record={record ?? undefined} /></span>}>
      {hint && <p className="wm-hint">{hint}</p>}
      {error ? <Alert type="error" showIcon message={error} /> : !record ? <Spin /> : (
        <Form form={form} name={`${resource}-${id}`} layout="vertical">
          {fields.map(renderField)}
          <Button type="primary" loading={saving} onClick={() => void save()}>Lưu bản nháp</Button>
        </Form>
      )}
    </Card>
  );
}

function AlbumMedia({ album, onClose }: { album: AdminRecord; onClose: () => void }) {
  const { message, modal } = App.useApp();
  const changed = useChanged();
  const [items, setItems] = useState<AdminMedia[] | null>(null);
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    try { setItems(await websiteApi.media(album.id)); } catch (e) { message.error(errorText(e)); }
  }, [album.id, message]);
  useEffect(() => { void load(); }, [load]);

  async function upload(files: FileList | null) {
    if (!files?.length) return;
    setBusy(true);
    for (const file of Array.from(files)) {
      try { await websiteApi.upload(file, { albumId: album.id, publicConsent: consent }); }
      catch (e) { message.error(`${file.name}: ${errorText(e)}`); }
    }
    if (input.current) input.current.value = "";
    setBusy(false);
    await load();
    changed();
  }

  const patch = async (item: AdminMedia, meta: Partial<AdminMedia>) => {
    try { await websiteApi.mediaUpdate(item.id, meta); await load(); changed(); } catch (e) { message.error(errorText(e)); }
  };
  const move = async (index: number, delta: number) => {
    if (!items) return;
    const ids = items.map((m) => m.id);
    [ids[index], ids[index + delta]] = [ids[index + delta], ids[index]];
    try { await websiteApi.mediaReorder(ids); await load(); changed(); } catch (e) { message.error(errorText(e)); }
  };
  const remove = (item: AdminMedia) => modal.confirm({
    title: "Xóa tệp này?", content: "Tệp sẽ bị xóa vĩnh viễn khỏi máy chủ.", okText: "Xóa", okType: "danger", cancelText: "Hủy",
    onOk: async () => { try { await websiteApi.mediaRemove(item.id); await load(); changed(); } catch (e) { message.error(errorText(e)); } },
  });

  return (
    <Drawer open width={720} onClose={onClose} title={`Ảnh và video: ${String(album.title)}`}>
      <div className="wm-upload">
        <input ref={input} type="file" multiple hidden accept="image/jpeg,image/png,image/webp,video/mp4,video/webm" onChange={(e) => void upload(e.target.files)} />
        <Button type="primary" icon={<ImagePlus size={16} />} loading={busy} onClick={() => input.current?.click()}>Tải ảnh/video lên</Button>
        <Checkbox checked={consent} onChange={(e) => setConsent(e.target.checked)}>Tôi xác nhận các tệp tải lên được phép công khai</Checkbox>
        <small>Ảnh JPG, PNG, WebP tối đa 5 MB; video MP4, WebM tối đa 50 MB. Tư liệu chưa xác nhận sẽ không hiển thị trên website.</small>
      </div>
      {!items ? <Spin /> : items.length === 0 ? <Empty description="Album chưa có tư liệu" /> : items.map((item, index) => (
        <div key={item.id} className="wm-media">
          <div className="wm-media-thumb">
            {item.kind === "VIDEO" ? <video src={adminMediaUrl(item.id)} controls preload="metadata" /> : <img src={adminMediaUrl(item.id)} alt={item.altText ?? item.originalName} />}
          </div>
          <div className="wm-media-form">
            <Input key={item.id + "t" + item.title} defaultValue={item.title ?? ""} placeholder="Chú thích" maxLength={300}
              onBlur={(e) => e.target.value !== (item.title ?? "") && void patch(item, { title: e.target.value })} />
            <Input key={item.id + "a" + item.altText} defaultValue={item.altText ?? ""} placeholder="Mô tả thay thế cho người dùng đọc màn hình" maxLength={300}
              onBlur={(e) => e.target.value !== (item.altText ?? "") && void patch(item, { altText: e.target.value })} />
            <div className="wm-media-flags">
              <Switch checked={item.publicConsent} onChange={(publicConsent) => void patch(item, { publicConsent })} /> Được phép công khai
              <Switch checked={item.visible} onChange={(visible) => void patch(item, { visible })} /> Hiển thị
            </div>
            <div className="wm-actions">
              <Button size="small" disabled={index === 0} icon={<ArrowUp size={14} />} aria-label="Chuyển lên" onClick={() => void move(index, -1)} />
              <Button size="small" disabled={index === items.length - 1} icon={<ArrowDown size={14} />} aria-label="Chuyển xuống" onClick={() => void move(index, 1)} />
              <Button size="small" danger icon={<Trash2 size={14} />} aria-label="Xóa" onClick={() => remove(item)} />
              {!item.publicConsent && <Tag color="orange">Chưa xác nhận: không hiển thị</Tag>}
            </div>
          </div>
        </div>
      ))}
    </Drawer>
  );
}

export function AlbumsEditor() {
  const [album, setAlbum] = useState<AdminRecord | null>(null);
  return (
    <>
      <ListEditor resource="albums" title="Album không gian và hoạt động" addLabel="Thêm album"
        fields={[
          { name: "title", label: "Tên album", type: "text", required: true, max: 200 },
          { name: "category", label: "Phân loại", type: "select", required: true, options: [{ value: "SPACE", label: "Không gian" }, { value: "ACTIVITY", label: "Hoạt động" }] },
          { name: "description", label: "Mô tả", type: "textarea", max: 1000, rows: 3 },
          { name: "visible", label: "Hiển thị album", type: "switch" },
        ]}
        columns={[
          { title: "Tên album", render: (r) => <b>{String(r.title)}</b> },
          { title: "Phân loại", render: (r) => (r.category === "SPACE" ? "Không gian" : "Hoạt động") },
        ]}
        extraActions={(r) => <Button size="small" onClick={() => setAlbum(r)}>Ảnh/video</Button>} />
      {album && <AlbumMedia album={album} onClose={() => setAlbum(null)} />}
    </>
  );
}
