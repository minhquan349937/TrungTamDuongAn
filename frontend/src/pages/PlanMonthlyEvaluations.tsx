import { useEffect, useState } from "react";
import { Alert, Button, Input, Modal, Popconfirm, Spin, Tag } from "antd";
import { useAuth } from "../auth/AuthProvider";
import { planEvaluationApi, type PlanEvaluation } from "../services/planEvaluationApi";
import type { Plan } from "../services/goalBankApi";
import { PlanLifecycleActions } from "./PlanLifecycleActions";
import "./intervention-workflow.css";

const dateLabel = (date: string) => new Date(`${date}Z`).toLocaleString("vi-VN", { timeZone: "Asia/Ho_Chi_Minh" });
const monthLabel = (date: string) => new Date(`${date}Z`).toLocaleDateString("vi-VN", { month: "long", year: "numeric", timeZone: "Asia/Ho_Chi_Minh" });

export function PlanMonthlyEvaluations({ plan, studentName, onPlanChanged }: { plan: Plan; studentName: string; onPlanChanged: () => void }) {
  const planId = plan.id;
  const { user } = useAuth();
  const [rows, setRows] = useState<PlanEvaluation[]>([]);
  const [editing, setEditing] = useState<PlanEvaluation | "new" | null>(null);
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [copyNotice, setCopyNotice] = useState("");
  const latest = rows[0];
  async function reload() { setRows(await planEvaluationApi.list(planId)); }
  useEffect(() => {
    let active = true;
    planEvaluationApi.list(planId).then(data => { if (active) setRows(data); })
      .catch((cause: Error) => { if (active) setError(cause.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [planId]);
  function open(row: PlanEvaluation | "new") {
    setEditing(row); setNotes(row === "new" ? "" : row.evaluationNotes); setError(""); setCopyNotice("");
  }
  function openAssessment() {
    if (!latest) { open("new"); return; }
    if (!latest.sentToParent && (user?.role === "ADMIN" || user?.id === latest.evaluatedBy)) { open(latest); return; }
    open("new"); setNotes(latest.evaluationNotes);
    setCopyNotice(latest.sentToParent
      ? "Bản đánh giá trước đã công bố. Thay đổi của bạn sẽ được lưu thành bản nháp mới; bản đã công bố vẫn được giữ nguyên."
      : "Bản nháp trước do giáo viên khác tạo. Bạn có thể lưu nhận xét của mình thành bản đánh giá mới.");
  }
  async function save() {
    if (!notes.trim()) { setError("Vui lòng nhập nhận xét trước khi lưu."); return; }
    setBusy(true); setError("");
    try {
      if (editing === "new") await planEvaluationApi.create(planId, notes.trim());
      else if (editing) await planEvaluationApi.update(editing.id, notes.trim());
      await reload(); setEditing(null); onPlanChanged();
    } catch (cause) { setError((cause as Error).message); }
    finally { setBusy(false); }
  }
  async function publish(id: string) {
    setBusy(true); setError("");
    try { await planEvaluationApi.publish(id); await reload(); onPlanChanged(); }
    catch (cause) { setError((cause as Error).message); }
    finally { setBusy(false); }
  }
  return <section className="ip-monthly-reports">
    <div className="ip-monthly-heading">
      <div><h3>Đánh giá cả kế hoạch</h3><p>Nhận xét chung của giáo viên về kế hoạch học tập.</p></div>
    </div>
    {error && !editing && <Alert type="error" showIcon message={error} />}
    {loading ? <Spin /> : rows.length === 0 ? <p className="ip-monthly-empty">Chưa có nhận xét nào cho kế hoạch này.</p> :
      <div className="ip-monthly-list">{rows.map(row => <article key={row.id} className="ip-monthly-card">
        <div className="ip-monthly-card-heading"><strong>{monthLabel(row.evaluatedAt)}</strong><Tag color={row.sentToParent ? "green" : "gold"}>{row.sentToParent ? "Đã công bố cho phụ huynh" : "Bản nháp"}</Tag></div>
        <p>{row.evaluationNotes}</p>
        <small>{row.evaluatedByName} · {dateLabel(row.evaluatedAt)}{row.sentAt ? ` · Công bố ${dateLabel(row.sentAt)}` : ""}</small>
        {!row.sentToParent && (user?.role === "ADMIN" || user?.id === row.evaluatedBy) && <div className="ip-monthly-actions">
          <Button onClick={() => open(row)}>Sửa bản nháp</Button>
          <Popconfirm title="Công bố nhận xét cho phụ huynh?" description="Sau khi công bố, nội dung nhận xét sẽ được giữ nguyên." okText="Công bố" cancelText="Hủy" onConfirm={() => void publish(row.id)}>
            <Button type="primary" disabled={busy}>Công bố trong cổng phụ huynh</Button>
          </Popconfirm>
        </div>}
      </article>)}</div>}
    <div className="ip-plan-assessment-actions">
      <Button type="primary" disabled={loading || busy} onClick={openAssessment}>{latest ? "Chỉnh sửa đánh giá" : "Đánh giá kế hoạch"}</Button>
      {latest && <Button disabled={loading || busy} onClick={() => open("new")}>Tạo đánh giá mới</Button>}
    </div>
    <PlanLifecycleActions plan={plan} hasEvaluation={rows.length > 0} disabled={loading || busy} onSaved={onPlanChanged} />
    <Modal className="ip-plan-assessment-modal" width={760} title={editing === "new" ? "Đánh giá kế hoạch" : "Chỉnh sửa đánh giá kế hoạch"} open={!!editing} onCancel={() => { if (!busy) { setEditing(null); setError(""); } }} onOk={() => void save()} okText="Lưu đánh giá kế hoạch" confirmLoading={busy} okButtonProps={{ disabled: !notes.trim() || busy, "aria-label": "Lưu đánh giá kế hoạch" }}>
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
      {copyNotice && <Alert type="info" showIcon message={copyNotice} style={{ marginBottom: 20 }} />}
      <div className="ip-plan-assessment-fields">
        <label>Tên kế hoạch<Input aria-label="Tên kế hoạch đánh giá" value={plan.name} readOnly /></label>
        <label>Tên học sinh<Input aria-label="Tên học sinh đánh giá" value={studentName} readOnly /></label>
        <label>Ngày áp dụng kế hoạch<Input aria-label="Ngày áp dụng kế hoạch" type="date" value={plan.startDate} readOnly /></label>
        <label>Ngày đánh giá gần nhất<Input aria-label="Ngày đánh giá gần nhất" value={latest ? dateLabel(latest.updatedAt) : "Chưa có đánh giá trước đó"} readOnly /></label>
        <label className="ip-plan-assessment-author">Tên người đánh giá<Input aria-label="Tên người đánh giá" value={editing && editing !== "new" ? editing.evaluatedByName : user?.displayName ?? ""} readOnly /></label>
      </div>
      <label className="ip-monthly-editor">Nhận xét giáo viên<Input.TextArea aria-label="Nhận xét giáo viên về kế hoạch" rows={8} value={notes} onChange={e => setNotes(e.target.value)} maxLength={16000} showCount placeholder="Ghi lại tiến bộ, khó khăn và hướng đồng hành trong tháng…" /></label>
    </Modal>
  </section>;
}

export function ParentPlanReports() {
  const [rows, setRows] = useState<PlanEvaluation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    planEvaluationApi.forParent().then(data => { if (active) setRows(data); })
      .catch((cause: Error) => { if (active) setError(cause.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  return <div className="ip-parent-reports">
    <div className="page-heading"><div><div className="eyebrow">ĐỒNG HÀNH CÙNG CON</div><h1>Nhận xét kế hoạch học tập</h1><p>Những nhận xét giáo viên đã công bố trong cổng phụ huynh.</p></div></div>
    {error && <Alert type="error" showIcon message={error} />}
    {loading ? <Spin /> : rows.length === 0 ? <div className="panel ip-monthly-empty">Chưa có nhận xét nào được công bố.</div> :
      <div className="ip-monthly-list">{rows.map(row => <article key={row.id} className="panel ip-monthly-card">
        <div className="ip-monthly-card-heading"><strong>{row.studentName} · {monthLabel(row.evaluatedAt)}</strong><Tag color="green">Đã công bố</Tag></div>
        <small>{row.planName} · {row.evaluatedByName} · Công bố {dateLabel(row.sentAt ?? row.evaluatedAt)}</small>
        <p>{row.evaluationNotes}</p>
      </article>)}</div>}
  </div>;
}
