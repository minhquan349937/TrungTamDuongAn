import { useEffect, useState } from "react";
import { Alert, Button, Input, Select } from "antd";
import type { ActivityChoice } from "../services/goalBankApi";
import { suggestedActivityApi, type SuggestedActivity } from "../services/suggestedActivityApi";

export function GoalActivitySelect({ goalId, value, onChange, disabled = false, insidePopover = false }: {
  goalId: string | undefined;
  value: ActivityChoice[];
  onChange: (rows: ActivityChoice[]) => void;
  disabled?: boolean;
  insidePopover?: boolean;
}) {
  const [options, setOptions] = useState<SuggestedActivity[]>([]);
  const [loading, setLoading] = useState(false);
  const [adding, setAdding] = useState(false);
  const [saving, setSaving] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState("");
  useEffect(() => {
    if (!goalId) { setOptions([]); return; }
    let active = true;
    setLoading(true); setError(""); setAdding(false);
    void suggestedActivityApi.list(goalId).then(rows => { if (active) setOptions(rows); })
      .catch((cause: Error) => { if (active) setError(cause.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [goalId]);

  async function add() {
    if (!goalId || !name.trim()) return;
    setSaving(true); setError("");
    try {
      const row = await suggestedActivityApi.create(goalId, name.trim(), description.trim() || null);
      setOptions(current => current.some(item => item.id === row.id) ? current : [...current, row]);
      onChange(value.some(item => item.activityId === row.id) ? value
        : [...value, { activityId: row.id, name: row.name, description: row.description, note: null }]);
      setName(""); setDescription(""); setAdding(false);
    } catch (cause) { setError((cause as Error).message); }
    finally { setSaving(false); }
  }

  return <div>
    <Select mode="multiple" style={{ width: "100%" }} showSearch optionFilterProp="label"
      aria-label="Hoạt động gợi ý" placeholder="Chọn hoặc thêm hoạt động" maxTagCount="responsive"
      getPopupContainer={insidePopover ? trigger => trigger.parentElement ?? document.body : undefined}
      disabled={!goalId || disabled} loading={loading} value={value.map(row => row.activityId)}
      options={options.map(row => ({ value: row.id, label: row.name }))}
      onChange={(ids: string[]) => onChange(ids.map(id => value.find(row => row.activityId === id)
        ?? { activityId: id, name: options.find(row => row.id === id)?.name,
          description: options.find(row => row.id === id)?.description ?? null, note: null }))}
      popupRender={menu => <div>
        {menu}
        <div onMouseDown={event => event.stopPropagation()} style={{ borderTop: "1px solid #eee", padding: 8 }}>
          {!adding ? <Button type="link" onClick={() => setAdding(true)}>+ Thêm hoạt động</Button> :
            <div style={{ display: "grid", gap: 8 }}>
              <Input autoFocus placeholder="Tên hoạt động" maxLength={255} value={name} onChange={event => setName(event.target.value)} />
              <Input.TextArea placeholder="Mô tả, cách thực hiện (tùy chọn)" value={description} onChange={event => setDescription(event.target.value)} />
              <div style={{ display: "flex", gap: 8 }}>
                <Button type="primary" size="small" loading={saving} disabled={!name.trim()} onClick={() => void add()}>Thêm vào thư viện và chọn</Button>
                <Button size="small" onClick={() => { setAdding(false); setError(""); }}>Hủy</Button>
              </div>
            </div>}
        </div>
      </div>} />
    {error && <Alert type="error" message={error} style={{ marginTop: 8 }} />}
    {value.map(row => <label key={row.activityId} style={{ display: "block", marginTop: 8 }}>
      Ghi chú áp dụng {row.name ?? row.activityId} (tùy chọn)
      <Input.TextArea value={row.note ?? ""} disabled={disabled} autoSize={{ minRows: 2, maxRows: 4 }}
        onChange={event => onChange(value.map(item => item.activityId === row.activityId ? { ...item, note: event.target.value || null } : item))} />
    </label>)}
  </div>;
}
