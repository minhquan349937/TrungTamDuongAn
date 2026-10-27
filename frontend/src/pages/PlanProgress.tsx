import { useEffect, useState } from "react";
import { Alert, Button, Input, InputNumber, Modal, Select, Spin } from "antd";
import { goalRequest, type Plan, type AssignedGoal } from "../services/goalBankApi";

type Observation = { id: string; observedAt: string; notes: string; successCount: number | null; opportunityCount: number | null };
const planStatuses = [ ["DRAFT", "Bản nháp"], ["ACTIVE", "Đang học"], ["COMPLETED", "Hoàn thành"], ["PAUSED", "Tạm dừng"], ["CANCELLED", "Đã hủy"] ];
const goalStatuses = [ ["NOT_STARTED", "Chưa bắt đầu"], ["IN_PROGRESS", "Đang học"], ["ACHIEVED", "Đã hoàn thành"], ["PAUSED", "Tạm dừng"] ];
const options = (rows: string[][]) => rows.map(([value, label]) => ({ value, label }));

export function PlanProgress({ plan, goals, onSaved }: { plan: Plan; goals: AssignedGoal[]; onSaved: () => void }) {
  const [edit, setEdit] = useState(false);
  const [name, setName] = useState(plan.name);
  const [startDate, setStartDate] = useState(plan.startDate);
  const [status, setStatus] = useState(plan.status);
  const [goal, setGoal] = useState<AssignedGoal | null>(null);
  const [records, setRecords] = useState<Observation[]>([]);
  const [notes, setNotes] = useState("");
  const [successes, setSuccesses] = useState<number | null>(null);
  const [opportunities, setOpportunities] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const path = goal ? `/api/plans/${plan.id}/goals/${goal.id}/progress` : "";
  useEffect(() => {
    if (!path) return;
    let active = true;
    setRecords([]); setError(""); setBusy(true);
    void goalRequest<Observation[]>(path).then(rows => { if (active) setRecords(rows); })
      .catch((cause: Error) => { if (active) setError(cause.message); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [path]);
  async function save(action: () => Promise<void>) {
    setBusy(true); setError("");
    try { await action(); } catch (cause) { setError((cause as Error).message); }
    finally { setBusy(false); }
  }
  return <div className="ip-progress">
    {error && !goal && <Alert type="error" message={error} />}
    <Button onClick={() => { setName(plan.name); setStartDate(plan.startDate); setStatus(plan.status); setEdit(true); }}>Chỉnh sửa kế hoạch</Button>
    {goals.map(row => <div className="ip-progress-row" key={row.id}>
      <div><b>{row.goalSnapshot.code}</b> · {row.goalSnapshot.name}</div>
      <Select aria-label={`Trạng thái ${row.goalSnapshot.code}`} value={row.status} options={options(goalStatuses)} disabled={busy}
        onChange={value => void save(async () => { await goalRequest(`/api/plans/${plan.id}/goals/${row.id}`, { status: value }, "PATCH"); onSaved(); })} />
      <Button onClick={() => { setNotes(""); setSuccesses(null); setOpportunities(null); setGoal(row); }}>Theo dõi tiến độ</Button>
    </div>)}
    <Modal title="Chỉnh sửa kế hoạch" okText="Lưu thay đổi" cancelText="Hủy" open={edit} onCancel={() => setEdit(false)} confirmLoading={busy} onOk={() => void save(async () => {
      await goalRequest(`/api/plans/${plan.id}`, { name, startDate, status }, "PATCH"); setEdit(false); onSaved();
    })}>
      {error && <Alert type="error" message={error} />}
      <label>Tên kế hoạch<Input value={name} maxLength={200} onChange={e => setName(e.target.value)} /></label>
      <label>Ngày bắt đầu<Input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} /></label>
      <label>Trạng thái<Select value={status} options={options(planStatuses)} onChange={setStatus} style={{ width: "100%" }} /></label>
    </Modal>
    <Modal title={`Tiến độ · ${goal?.goalSnapshot.code ?? ""}`} open={!!goal} onCancel={() => setGoal(null)} footer={null}>
      {error && <Alert type="error" message={error} />}
      <Input.TextArea aria-label="Ghi chú buổi học" placeholder="Ghi nhận quan sát trong buổi học" value={notes} onChange={e => setNotes(e.target.value)} />
      <p>Số lần thực hiện (có thể để trống)</p>
      <InputNumber aria-label="Số lần thành công" placeholder="Thành công" min={0} precision={0} value={successes} onChange={setSuccesses} /> / {" "}
      <InputNumber aria-label="Tổng cơ hội" placeholder="Tổng cơ hội" min={0} precision={0} value={opportunities} onChange={setOpportunities} />
      <p><Button type="primary" aria-label="Lưu quan sát" disabled={busy || !notes.trim()} loading={busy} onClick={() => void save(async () => {
        const rows = await goalRequest<Observation[]>(path, { notes, successCount: successes, opportunityCount: opportunities });
        setRecords(rows); setNotes("");
      })}>Lưu quan sát</Button></p>
      {busy && <Spin />}
      {records.map(row => <article key={row.id} className="ip-observation">
        <small>{new Date(`${row.observedAt}Z`).toLocaleString("vi-VN")}</small><p>{row.notes}</p>
        {row.successCount !== null && <b>{row.successCount}/{row.opportunityCount} lần thành công</b>}
      </article>)}
    </Modal>
  </div>;
}
