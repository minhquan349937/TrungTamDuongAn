import { useEffect, useState } from "react";
import { Alert, Button, Input, InputNumber, Modal, Select, Spin, Tag } from "antd";
import { goalRequest, type Plan, type AssignedGoal } from "../services/goalBankApi";
import { goalEvaluationApi, evaluationLabels, evaluationStatus, type GoalEvaluation, type EvaluationInput } from "../services/goalEvaluationApi";
import { useAuth } from "../auth/AuthProvider";
import { PlanGoalAdder } from "./PlanGoalAdder";
import { PencilLine } from "lucide-react";

const planStatuses = [["DRAFT", "Bản nháp"], ["ACTIVE", "Đang học"], ["COMPLETED", "Hoàn thành"], ["PAUSED", "Tạm dừng"], ["CANCELLED", "Đã hủy"]];
export function PlanProgress({ plan, goals, onSaved, mode = "manage" }: { plan: Plan; goals: AssignedGoal[]; onSaved: () => void; mode?: "manage" | "evaluate" }) {
  const { user } = useAuth();
  const [edit, setEdit] = useState(false);
  const [name, setName] = useState(plan.name);
  const [startDate, setStartDate] = useState(plan.startDate);
  const [status, setStatus] = useState(plan.status);
  const [goal, setGoal] = useState<AssignedGoal | null>(null);
  const [records, setRecords] = useState<GoalEvaluation[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [percent, setPercent] = useState<number | null>(null);
  const [restart, setRestart] = useState<EvaluationInput["restartDecision"]>(null);
  const [notes, setNotes] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const goalId = goal?.id;
  function newAttempt() { setEditingId(null); setPercent(null); setRestart(null); setNotes(""); }
  useEffect(() => {
    if (!goalId) return;
    let active = true;
    setRecords([]); setError(""); setBusy(true);
    goalEvaluationApi.list(plan.id, goalId).then(rows => { if (active) setRecords(rows); })
      .catch((cause: Error) => { if (active) setError(cause.message); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [plan.id, goalId]);
  async function save(action: () => Promise<void>) {
    setBusy(true); setError("");
    try { await action(); } catch (cause) { setError((cause as Error).message); }
    finally { setBusy(false); }
  }
  return <div className={`ip-progress ip-progress-${mode}`}>
    {error && !goal && <Alert type="error" message={error} />}
    {mode === "manage" && (user?.role === "ADMIN" || user?.role === "TEACHER") && <div className="ip-plan-management-actions"><Button icon={<PencilLine size={16} />} onClick={() => {
      setName(plan.name); setStartDate(plan.startDate); setStatus(plan.status); setEdit(true);
    }}>Chỉnh sửa kế hoạch</Button>
    {plan.status !== "CANCELLED" && <PlanGoalAdder planId={plan.id} onSaved={onSaved} />}</div>}
    {mode === "evaluate" && goals.map(row => <div className="ip-progress-row" key={row.id}>
      <div><b>{row.goalSnapshot.code}</b> · {row.goalSnapshot.name}
        <small style={{ display: "block" }}>{row.goalSnapshot.program_name} · {row.goalSnapshot.level_name ?? `Cấp độ ${row.goalSnapshot.level_number ?? ""}`} · {row.goalSnapshot.domain_name}</small>
        {row.specificGoalId && <small style={{ display: "block" }}>Mục tiêu chính: {row.goalSnapshot.goal_code} · {row.goalSnapshot.goal_name}</small>}
      </div>
      <div className="ip-goal-result"><Tag color={row.status === "ACHIEVED" ? "green" : row.status === "NOT_ACHIEVED" ? "orange" : "default"}>
        {evaluationLabels[row.status as keyof typeof evaluationLabels] ?? row.status}
      </Tag>{row.achievementPercent != null && <small>Tỷ lệ hoàn thành: <b>{new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(row.achievementPercent)}%</b></small>}</div>
      {mode === "evaluate" && <Button onClick={() => { newAttempt(); setGoal(row); }}>Đánh giá / Lịch sử</Button>}
    </div>)}
    <Modal title="Chỉnh sửa kế hoạch" okText="Lưu thay đổi" cancelText="Hủy" open={edit} onCancel={() => setEdit(false)} confirmLoading={busy} onOk={() => void save(async () => {
      await goalRequest(`/api/plans/${plan.id}`, { name, startDate, status }, "PATCH"); setEdit(false); onSaved();
    })}>
      {error && <Alert type="error" message={error} />}
      <label>Tên kế hoạch<Input value={name} maxLength={200} onChange={e => setName(e.target.value)} /></label>
      <label>Ngày bắt đầu<Input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} /></label>
      <label>Trạng thái<Select value={status} options={planStatuses.map(([value, label]) => ({ value, label }))} onChange={setStatus} style={{ width: "100%" }} /></label>
    </Modal>
    <Modal className="ip-evaluation-modal" title={`Đánh giá · ${goal?.goalSnapshot.code ?? ""}`} open={!!goal} onCancel={() => setGoal(null)} footer={null} width={720}>
      {error && <Alert type="error" showIcon message={error} />}
      <div className="ip-evaluation-intro">
        <h3>{editingId ? `Sửa lần đánh giá ${records.find(r => r.id === editingId)?.attemptNo}` : `Lần đánh giá ${records.length ? records[0].attemptNo + 1 : 1}`}</h3>
        <p>{goal?.goalSnapshot.name}</p>
      </div>
      <div className="ip-evaluation-fields">
        <div className="ip-evaluation-score">
          <label>Tỷ lệ hoàn thành (%)<InputNumber aria-label="Tỷ lệ hoàn thành" min={0} max={100} precision={2} step={1} value={percent} onChange={setPercent} placeholder="Chưa thực hiện" disabled={busy} /></label>
          <div className="ip-evaluation-result">Kết quả: <b>{evaluationLabels[evaluationStatus(percent)]}</b><small>Từ 80%: Đạt</small></div>
        </div>
        <label>Quyết định thực hiện lại<Select aria-label="Quyết định thực hiện lại" value={restart} allowClear disabled={busy}
          onChange={value => setRestart(value ?? null)} placeholder="Chưa quyết định" style={{ width: "100%" }}
          options={[{ value: "RETRY", label: "Thực hiện lại" }, { value: "NO_RETRY", label: "Không thực hiện lại" }]} /></label>
        <label>Nhận xét giáo viên<Input.TextArea aria-label="Nhận xét giáo viên" rows={4} value={notes} onChange={e => setNotes(e.target.value)} disabled={busy} /></label>
      </div>
      <div className="ip-evaluation-actions"><Button type="primary" aria-label={editingId ? "Lưu thay đổi đánh giá" : "Lưu đánh giá"} disabled={busy} loading={busy} onClick={() => void save(async () => {
        if (!goal) return;
        const input: EvaluationInput = { achievementPercent: percent, restartDecision: restart, evaluationNotes: notes.trim() || null };
        const rows = editingId ? await goalEvaluationApi.update(plan.id, goal.id, editingId, input) : await goalEvaluationApi.create(plan.id, goal.id, input);
        setRecords(rows); newAttempt(); onSaved();
      })}>{editingId ? "Lưu thay đổi đánh giá" : "Lưu đánh giá"}</Button>{" "}
      <Button disabled={busy} onClick={newAttempt}>Đánh giá lại</Button></div>
      <section className="ip-evaluation-history">
        <h3>Lịch sử đánh giá ({records.length})</h3>
        {busy && <Spin />}
        {!busy && !records.length && <p>Chưa có lần đánh giá nào.</p>}
        {records.map(row => <article key={row.id} className="ip-observation">
        <b>Lần {row.attemptNo}: {evaluationLabels[row.resultStatus]}{row.achievementPercent !== null ? ` · ${row.achievementPercent}%` : ""}</b>
        <p>{row.evaluationNotes || "Chưa có nhận xét"}</p>
        <small>{row.evaluatedByName} · {new Date(`${row.evaluatedAt ?? row.createdAt}Z`).toLocaleString("vi-VN", { timeZone: "Asia/Ho_Chi_Minh" })}</small>
        {row.restartDecision && <p>{row.restartDecision === "RETRY" ? "Thực hiện lại" : "Không thực hiện lại"}</p>}
        {(user?.role === "ADMIN" || user?.id === row.evaluatedBy) && <Button size="small" disabled={busy} onClick={() => {
          setEditingId(row.id); setPercent(row.achievementPercent); setRestart(row.restartDecision); setNotes(row.evaluationNotes ?? "");
        }}>Sửa lần đánh giá</Button>}
        </article>)}
      </section>
    </Modal>
  </div>;
}
