import { useEffect, useState } from "react";
import { Alert, Button, Input, Modal, Select } from "antd";
import { Plus, RefreshCw } from "lucide-react";
import { goalBankApi, type Domain, type Goal, type GoalDetail, type Program, type ProgramLevel, type StrategyChoice } from "../services/goalBankApi";
import { interventionApi } from "../services/interventionApi";
import type { StrategyOption } from "./GoalStrategyPicker";

const selectLayout = { virtual: false, popupClassName: "ip-add-goal-options" };

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
  const [strategyOptions, setStrategyOptions] = useState<StrategyOption[]>([]);
  const [strategies, setStrategies] = useState<StrategyChoice[] | null>(null);
  const [strategyLoading, setStrategyLoading] = useState(false);
  const [strategyError, setStrategyError] = useState("");
  const [strategyRefresh, setStrategyRefresh] = useState(0);
  useEffect(() => {
    if (!open) return;
    let active = true;
    setStrategyLoading(true); setStrategyError(""); setStrategyOptions([]);
    void interventionApi.strategies().then(rows => { if (active) setStrategyOptions(rows); })
      .catch((cause: Error) => { if (active) setStrategyError(cause.message); })
      .finally(() => { if (active) setStrategyLoading(false); });
    return () => { active = false; };
  }, [open, strategyRefresh]);
  useEffect(() => { setStrategies(null); }, [program, level, domain, goal, specific]);
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
    if (!open || !domain) { setGoals([]); return; }
    let active = true;
    setGoals([]);
    goalBankApi.availableGoals(planId, domain, search, 0).then(page => { if (active) setGoals(page.content); })
      .catch((cause: Error) => { if (active) setError(cause.message); });
    return () => { active = false; };
  }, [open, planId, domain, search]);
  useEffect(() => {
    setDetail(null);
    if (!open || !goal) return;
    let active = true;
    goalBankApi.availableGoal(planId, goal).then(row => { if (active) setDetail(row); })
      .catch((cause: Error) => { if (active) setError(cause.message); });
    return () => { active = false; };
  }, [open, planId, goal]);
  async function save() {
    if (!goal || !detail || (!detail.canSelectGeneralGoal && !specific)) return;
    setBusy(true); setError("");
    try {
      if (replaceGoalId) await goalBankApi.replace(planId, replaceGoalId, goal, specific, strategies);
      else await goalBankApi.assign(planId, goal, specific, strategies);
      setOpen(false); onSaved();
    } catch (cause) { setError((cause as Error).message); }
    finally { setBusy(false); }
  }
  return <>
    <Button type={replaceGoalId ? "default" : "primary"} icon={replaceGoalId ? <RefreshCw size={15} /> : <Plus size={16} />} onClick={() => { setOpen(true); setError(""); setProgram(undefined); setLevel(undefined); setDomain(undefined); setGoal(undefined); setSpecific(null); setDetail(null); setStrategies(null); }}>{replaceGoalId ? "Thay mục tiêu" : "Thêm mục tiêu vào kế hoạch"}</Button>
    <Modal className="ip-add-goal-modal" width={640} styles={{ body: { maxHeight: "65vh", overflowY: "auto" } }} title={replaceGoalId ? "Thay mục tiêu trong kế hoạch" : "Thêm mục tiêu vào kế hoạch"} open={open} onCancel={() => setOpen(false)} onOk={() => void save()}
      okText={replaceGoalId ? "Lưu mục tiêu thay thế" : "Lưu mục tiêu"} okButtonProps={{ disabled: !goal || !detail || (!detail.canSelectGeneralGoal && !specific) || busy }} confirmLoading={busy}>
      <div className="ip-add-goal-form">
        {error && <Alert type="error" showIcon message={error} />}
        <label>Chương trình<Select {...selectLayout} aria-label="Chương trình mục tiêu" value={program} options={programs.map(p => ({ value: p.id, label: p.name }))}
          onChange={value => { setProgram(value); setLevel(undefined); setDomain(undefined); setGoal(undefined); setSpecific(null); }} /></label>
        <label>Tháng tuổi<Select {...selectLayout} aria-label="Tháng tuổi mục tiêu" value={level} disabled={!program} options={levels.map(l => ({ value: l.id, label: `${l.ageMinMonths}–${l.ageMaxMonths} tháng` }))}
          onChange={value => { setLevel(value); setDomain(undefined); setGoal(undefined); setSpecific(null); }} /></label>
        <label>Lĩnh vực<Select {...selectLayout} aria-label="Lĩnh vực mục tiêu" value={domain} disabled={!level} options={domains.map(d => ({ value: d.levelDomainId, label: d.name }))}
          onChange={value => { setDomain(value); setGoal(undefined); setSpecific(null); setSearch(""); }} /></label>
        {/* <label>Tìm mục tiêu<Input aria-label="Tìm mục tiêu" value={search} disabled={!domain} onChange={event => { setSearch(event.target.value); setGoal(undefined); setSpecific(null); }} placeholder="Mã hoặc tên mục tiêu" /></label> */}
        <label>Mục tiêu<Select {...selectLayout} aria-label="Mục tiêu thêm vào kế hoạch" value={goal} disabled={!domain} showSearch optionFilterProp="label"
          notFoundContent="Không còn mục tiêu chưa đánh giá"
          options={goals.map(g => ({ value: g.id, label: `${g.code} · ${g.name}` }))}
          onChange={value => { setGoal(value); setSpecific(null); setDetail(null); }} /></label>
        {detail?.specificGoals.length ? <label>{detail.canSelectGeneralGoal ? "Mục tiêu cụ thể (tùy chọn)" : "Mục tiêu cụ thể"}<Select {...selectLayout} aria-label="Mục tiêu cụ thể thêm vào kế hoạch" value={specific ?? undefined} allowClear={detail.canSelectGeneralGoal}
          options={detail.specificGoals.map(g => ({ value: g.id, label: `${g.code} · ${g.description}` }))}
          onChange={value => setSpecific(value ?? null)} placeholder={detail.canSelectGeneralGoal ? "Chọn mục tiêu chính" : "Chọn mục tiêu cụ thể chưa đánh giá"} /></label> : null}
        <label>Chiến lược (tùy chọn)<Select {...selectLayout} mode="multiple" aria-label="Chiến lược mục tiêu"
          value={strategies?.map(row => row.strategyId) ?? []} disabled={!detail || busy || !!strategyError}
          loading={strategyLoading} allowClear maxTagCount="responsive" placeholder="Không chọn chiến lược"
          options={strategyOptions.map(row => ({ value: row.id, label: row.name }))}
          onChange={(ids: string[]) => setStrategies(ids.length ? ids.map(id => strategies?.find(row => row.strategyId === id)
            ?? { strategyId: id, name: strategyOptions.find(row => row.id === id)?.name, description: null }) : null)} /></label>
        {strategyError && <Alert type="error" message={strategyError} action={<Button size="small" onClick={() => setStrategyRefresh(value => value + 1)}>Thử lại</Button>} />}
        {strategies?.map(strategy => <label key={strategy.strategyId}>Cách áp dụng {strategy.name ?? strategy.strategyId} (tùy chọn)
          <Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} disabled={busy}
            aria-label={`Cách áp dụng ${strategy.name ?? strategy.strategyId}`} value={strategy.description ?? ""}
            onChange={event => setStrategies(rows => rows?.map(row => row.strategyId === strategy.strategyId
              ? { ...row, description: event.target.value || null } : row) ?? null)} />
        </label>)}
      </div>
    </Modal>
  </>;
}
