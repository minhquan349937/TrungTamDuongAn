import { useEffect, useState } from "react";
import { Alert, Button, Input, Modal, Select } from "antd";
import { Plus, RefreshCw } from "lucide-react";
import { goalBankApi, type Domain, type Goal, type GoalDetail, type Program, type ProgramLevel } from "../services/goalBankApi";

export function PlanGoalAdder({ planId, onSaved, replaceGoalId }: { planId: string; onSaved: () => void; replaceGoalId?: string }) {
  const [open, setOpen] = useState(false);
  const [programs, setPrograms] = useState<Program[]>([]);
  const [levels, setLevels] = useState<ProgramLevel[]>([]);
  const [domains, setDomains] = useState<Domain[]>([]);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [detail, setDetail] = useState<GoalDetail | null>(null);
  const [program, setProgram] = useState<string>();
  const [level, setLevel] = useState<string>();
  const [domain, setDomain] = useState<string>();
  const [goal, setGoal] = useState<string>();
  const [specific, setSpecific] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!open) return;
    goalBankApi.programs().then(setPrograms).catch((cause: Error) => setError(cause.message));
  }, [open]);
  useEffect(() => {
    if (!program) { setLevels([]); return; }
    goalBankApi.levels(program).then(setLevels).catch((cause: Error) => setError(cause.message));
  }, [program]);
  useEffect(() => {
    if (!level) { setDomains([]); return; }
    goalBankApi.domains(level).then(setDomains).catch((cause: Error) => setError(cause.message));
  }, [level]);
  useEffect(() => {
    if (!domain) { setGoals([]); return; }
    goalBankApi.goals(domain, search, 0).then(page => setGoals(page.content)).catch((cause: Error) => setError(cause.message));
  }, [domain, search]);
  useEffect(() => {
    if (!goal) { setDetail(null); return; }
    goalBankApi.goal(goal).then(setDetail).catch((cause: Error) => setError(cause.message));
  }, [goal]);
  async function save() {
    if (!goal) return;
    setBusy(true); setError("");
    try {
      if (replaceGoalId) await goalBankApi.replace(planId, replaceGoalId, goal, specific);
      else await goalBankApi.assign(planId, goal, specific);
      setOpen(false); onSaved();
    } catch (cause) { setError((cause as Error).message); }
    finally { setBusy(false); }
  }
  return <>
    <Button type={replaceGoalId ? "default" : "primary"} icon={replaceGoalId ? <RefreshCw size={15} /> : <Plus size={16} />} onClick={() => { setOpen(true); setError(""); }}>{replaceGoalId ? "Thay mục tiêu" : "Thêm mục tiêu vào kế hoạch"}</Button>
    <Modal title={replaceGoalId ? "Thay mục tiêu trong kế hoạch" : "Thêm mục tiêu vào kế hoạch"} open={open} onCancel={() => setOpen(false)} onOk={() => void save()}
      okText={replaceGoalId ? "Lưu mục tiêu thay thế" : "Lưu mục tiêu"} okButtonProps={{ disabled: !goal || busy }} confirmLoading={busy}>
      <div className="ip-add-goal-form">
        {error && <Alert type="error" showIcon message={error} />}
        <label>Chương trình<Select aria-label="Chương trình mục tiêu" value={program} options={programs.map(p => ({ value: p.id, label: p.name }))}
          onChange={value => { setProgram(value); setLevel(undefined); setDomain(undefined); setGoal(undefined); setSpecific(null); }} /></label>
        <label>Cấp độ<Select aria-label="Cấp độ mục tiêu" value={level} disabled={!program} options={levels.map(l => ({ value: l.id, label: l.name }))}
          onChange={value => { setLevel(value); setDomain(undefined); setGoal(undefined); setSpecific(null); }} /></label>
        <label>Lĩnh vực<Select aria-label="Lĩnh vực mục tiêu" value={domain} disabled={!level} options={domains.map(d => ({ value: d.levelDomainId, label: d.name }))}
          onChange={value => { setDomain(value); setGoal(undefined); setSpecific(null); setSearch(""); }} /></label>
        <label>Tìm mục tiêu<Input aria-label="Tìm mục tiêu" value={search} disabled={!domain} onChange={event => { setSearch(event.target.value); setGoal(undefined); setSpecific(null); }} placeholder="Mã hoặc tên mục tiêu" /></label>
        <label>Mục tiêu<Select aria-label="Mục tiêu thêm vào kế hoạch" value={goal} disabled={!domain} showSearch optionFilterProp="label"
          options={goals.map(g => ({ value: g.id, label: `${g.code} · ${g.name}` }))}
          onChange={value => { setGoal(value); setSpecific(null); }} /></label>
        {detail?.specificGoals.length ? <label>Mục tiêu cụ thể (tùy chọn)<Select aria-label="Mục tiêu cụ thể thêm vào kế hoạch" value={specific ?? undefined} allowClear
          options={detail.specificGoals.map(g => ({ value: g.id, label: `${g.code} · ${g.description}` }))}
          onChange={value => setSpecific(value ?? null)} placeholder="Chọn mục tiêu chính" /></label> : null}
      </div>
    </Modal>
  </>;
}
