import { useEffect, useState } from "react";
import { Alert, Button, Checkbox, Empty, Input, Spin } from "antd";
import { ArrowLeft, Check, Sparkles, X } from "lucide-react";
import { useConfirm } from "../hooks/useConfirm";
import { goalBankApi, type BankEntry, type BankReview, type Domain, type Goal, type GoalDetail,
  type GoalSelection, type Page, type Program, type ProgramLevel, type SpecificGoal } from "../services/goalBankApi";

const targetKey = (goalId: string, specificId: string | null) => specificId ? `S:${specificId}` : `G:${goalId}`;

export function StudentGoalBank({ studentId, studentName, programs, onAssign }: {
  studentId: string; studentName: string; programs: Program[]; onAssign?: (target: GoalSelection) => Promise<void>;
}) {
  const modal = useConfirm();
  const [bank, setBank] = useState<BankEntry[]>([]);
  const [step, setStep] = useState<"bank" | "program" | "select" | "review">("bank");
  const [program, setProgram] = useState<Program | null>(null);
  const [levels, setLevels] = useState<ProgramLevel[]>([]);
  const [levelId, setLevelId] = useState("");
  const [domains, setDomains] = useState<Domain[]>([]);
  const [domainId, setDomainId] = useState("");
  const [goals, setGoals] = useState<Page<Goal> | null>(null);
  const [goalId, setGoalId] = useState("");
  const [detail, setDetail] = useState<GoalDetail | null>(null);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<GoalSelection[]>([]);
  const [review, setReview] = useState<BankReview | null>(null);
  const [pending, setPending] = useState(0);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const busy = pending > 0;

  async function run(action: () => Promise<void>) {
    setPending((n) => n + 1); setError("");
    try { await action(); } catch (cause) { setError((cause as Error).message); }
    finally { setPending((n) => n - 1); }
  }
  useEffect(() => { void run(async () => setBank(await goalBankApi.bank(studentId))); }, [studentId]);
  useEffect(() => {
    if (!program) return;
    let active = true;
    setPending((n) => n + 1);
    void goalBankApi.levels(program.id).then((rows) => { if (active) setLevels(rows); })
      .catch((cause: Error) => { if (active) setError(cause.message); }).finally(() => setPending((n) => n - 1));
    return () => { active = false; };
  }, [program]);
  useEffect(() => {
    if (!levelId) return;
    let active = true;
    setPending((n) => n + 1);
    void goalBankApi.domains(levelId).then((rows) => { if (active) setDomains(rows); })
      .catch((cause: Error) => { if (active) setError(cause.message); }).finally(() => setPending((n) => n - 1));
    return () => { active = false; };
  }, [levelId]);
  useEffect(() => {
    if (!domainId) return;
    let active = true;
    setGoals(null); setPending((n) => n + 1);
    void goalBankApi.goals(domainId, query, page).then((rows) => { if (active) setGoals(rows); })
      .catch((cause: Error) => { if (active) setError(cause.message); }).finally(() => setPending((n) => n - 1));
    return () => { active = false; };
  }, [domainId, query, page]);
  useEffect(() => {
    if (!goalId) return;
    let active = true;
    setPending((n) => n + 1);
    void goalBankApi.goal(goalId).then((row) => { if (active) setDetail(row); })
      .catch((cause: Error) => { if (active) setError(cause.message); }).finally(() => setPending((n) => n - 1));
    return () => { active = false; };
  }, [goalId]);

  function resetDomain() { setDomainId(""); setGoals(null); setGoalId(""); setDetail(null); setQuery(""); setPage(0); }
  function chooseProgram(row: Program) {
    setProgram({ ...row }); setLevels([]); setLevelId(""); setDomains([]); resetDomain();
    setSelected([]); setReview(null); setError("");
  }
  function toggle(child: SpecificGoal | null) {
    if (!detail) return;
    const key = targetKey(detail.id, child?.id ?? null);
    if (selected.some((row) => targetKey(row.goalId, row.specificGoalId) === key)) {
      setSelected((rows) => rows.filter((row) => targetKey(row.goalId, row.specificGoalId) !== key)); return;
    }
    setSelected((rows) => [...rows, {
      goalId: detail.id, specificGoalId: child?.id ?? null, targetType: child ? "SPECIFIC_GOAL" : "GOAL",
      code: child?.code ?? detail.code, name: detail.name, description: child?.description ?? detail.description,
      location: detail.location, ageMinMonths: child?.ageMinMonths ?? detail.location.ageMinMonths,
      ageMaxMonths: child?.ageMaxMonths ?? detail.location.ageMaxMonths,
    }]);
  }
  function checkbox(child: SpecificGoal | null) {
    if (!detail) return null;
    const key = targetKey(detail.id, child?.id ?? null);
    const saved = bank.some((row) => targetKey(row.goalSnapshot.goalId, row.goalSnapshot.specificGoalId) === key);
    return <Checkbox disabled={saved} checked={saved || selected.some((row) => targetKey(row.goalId, row.specificGoalId) === key)}
      onChange={() => toggle(child)}>{saved ? "Đã có trong ngân hàng" : child ? `Chọn ${child.code}` : "Chọn mục tiêu chính"}</Checkbox>;
  }
  function exit() {
    const close = () => { setStep("bank"); setSelected([]); setReview(null); setError(""); };
    if (!selected.length) close();
    else modal.confirm({ title: "Thoát khi chưa hoàn tất?", content: "Các lựa chọn chưa lưu sẽ được bỏ.",
      okText: "Thoát", cancelText: "Tiếp tục chọn", onOk: close });
  }
  function selectionsView(rows: GoalSelection[], removable = false) {
    const groups = [...new Set(rows.map((row) => `${row.location.levelId}:${row.location.domainId}`))];
    return <div className="goal-groups">{groups.map((group) => {
      const items = rows.filter((row) => `${row.location.levelId}:${row.location.domainId}` === group);
      return <section className="real-bank-group" key={group}>
        <h3>{items[0].location.domainName} <small>· {items[0].location.levelName}</small></h3>
        {items.map((row) => <article className="real-bank-item" key={targetKey(row.goalId, row.specificGoalId)}>
          <div><b>{row.code} · {row.name}</b>{row.description && <p>{row.description}</p>}
            <small>{row.location.programName} · {row.ageMinMonths}–{row.ageMaxMonths} tháng</small></div>
          {removable && <Button aria-label={`Bỏ ${row.code}`} icon={<X size={15} />} onClick={() => {
            setSelected((current) => current.filter((item) => targetKey(item.goalId, item.specificGoalId) !== targetKey(row.goalId, row.specificGoalId)));
            setReview(null); setStep("select");
          }} />}
          {step === "bank" && onAssign && <Button onClick={() => void run(() => onAssign(row))}>Thêm vào kế hoạch</Button>}
        </article>)}
      </section>;
    })}</div>;
  }

  return <section className="panel real-section student-bank">
    <div className="goal-section-heading"><div><h2>Ngân hàng mục tiêu của {studentName}</h2>
      <p>Chọn chương trình, độ tuổi và mục tiêu theo từng lĩnh vực, sau đó xem lại và hoàn tất.</p></div>
      {busy && <Spin aria-label="Đang tải ngân hàng" />}</div>
    {error && <Alert type="error" showIcon message={error} />}
    {notice && <Alert type="success" showIcon message={notice} />}
    {step === "bank" ? <>
      <Button type="primary" icon={<Sparkles size={16} />} onClick={() => { setStep("program"); setNotice(""); }}>Gợi ý mục tiêu</Button>
      {!bank.length ? <Empty description="Học sinh chưa có mục tiêu trong ngân hàng" /> : selectionsView(bank.map((row) => row.goalSnapshot))}
    </> : <>
      <ol className="goal-steps" aria-label="Tiến trình chọn mục tiêu">{["Thư viện mục tiêu", "Lĩnh vực và mục tiêu", "Xem lại và hoàn tất"].map((label, index) =>
        <li key={label} className={index === ["program", "select", "review"].indexOf(step) ? "current" : ""}><span>{index + 1}</span>{label}</li>)}</ol>
      {step === "program" && <>
        <div className="real-choices">{programs.map((row) => <button key={row.id} className={program?.id === row.id ? "selected" : ""}
          onClick={() => chooseProgram(row)}><b>{row.name}</b><small>{row.code}</small></button>)}</div>
        {!programs.length && <Empty description="Chưa có chương trình. Hãy nhập dữ liệu nguồn vào MySQL." />}
        <Button type="primary" disabled={!program} onClick={() => setStep("select")}>Tiếp tục: Lĩnh vực can thiệp</Button>
      </>}
      {step === "select" && <>
        <div className="real-picker">
          <label>Cấp độ / độ tuổi<select value={levelId} onChange={(e) => { setLevelId(e.target.value); setDomains([]); resetDomain(); }}>
            <option value="">Chọn cấp độ</option>{levels.map((row) => <option key={row.id} value={row.id}>{row.name} · {row.ageMinMonths}–{row.ageMaxMonths} tháng</option>)}</select></label>
          <label>Lĩnh vực can thiệp<select value={domainId} disabled={!levelId} onChange={(e) => { setDomainId(e.target.value); setGoals(null); setGoalId(""); setDetail(null); setQuery(""); setPage(0); }}>
            <option value="">Chọn lĩnh vực</option>{domains.map((row) => <option key={row.id} value={row.levelDomainId}>{row.name}</option>)}</select></label>
        </div>
        {domainId && <>
          <Input aria-label="Tìm mục tiêu ngân hàng" placeholder="Tìm mã hoặc tên mục tiêu" value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }} />
          <div className="real-goal-list">{goals?.content.map((row) => <button key={row.id} onClick={() => { if (goalId !== row.id) { setGoalId(row.id); setDetail(null); } }}><b>{row.code}</b><span>{row.name}</span></button>)}</div>
          {goals?.totalElements === 0 && <Empty description="Không có mục tiêu phù hợp" />}
          {goals && goals.totalPages > 1 && <div className="real-pager"><Button disabled={page === 0} onClick={() => setPage(page - 1)}>Trước</Button><span>{page + 1} / {goals.totalPages}</span><Button disabled={page + 1 >= goals.totalPages} onClick={() => setPage(page + 1)}>Sau</Button></div>}
        </>}
        {detail && <div className="real-goal-detail"><h3>{detail.code} · {detail.name}</h3>{detail.description && <p>{detail.description}</p>}
          {detail.canSelectGeneralGoal && checkbox(null)}
          {detail.supportsSpecificGoals && detail.specificGoals.length > 0 && <div className="real-specific-list"><h4>Mục tiêu cụ thể</h4>
            {detail.specificGoals.map((child) => <div key={child.id}><p><b>{child.code}</b> · {child.description}</p><small>{child.ageMinMonths}–{child.ageMaxMonths} tháng</small>{checkbox(child)}</div>)}
          </div>}
        </div>}
        <div className="real-bank-actions"><span>Đã chọn {selected.length} mục tiêu; lựa chọn được giữ khi đổi lĩnh vực hoặc độ tuổi.</span>
          <Button type="primary" loading={busy} disabled={!selected.length} onClick={() => void run(async () => {
            const result = await goalBankApi.previewBank(studentId, program!.id, selected.map(({ goalId, specificGoalId }) => ({ goalId, specificGoalId })));
            setReview(result); setStep("review");
          })}>Xem lại lựa chọn</Button></div>
      </>}
      {step === "review" && review && <>
        <p>{review.total} mục tiêu sẵn sàng lưu vào ngân hàng.</p>{selectionsView(review.items, true)}
        <div className="real-bank-actions"><Button onClick={() => setStep("select")}>Quay lại chọn mục tiêu</Button>
          <Button type="primary" icon={<Check size={16} />} loading={busy} onClick={() => void run(async () => {
            const result = await goalBankApi.completeBank(studentId, review);
            setBank(result.bank); setSelected([]); setReview(null); setStep("bank");
            setNotice(`Đã lưu ${result.addedCount} mục tiêu vào ngân hàng${result.alreadyPresentCount ? `; ${result.alreadyPresentCount} mục tiêu đã có sẵn` : ""}.`);
          })}>Hoàn tất</Button></div>
      </>}
      <Button icon={<ArrowLeft size={15} />} onClick={exit}>Về ngân hàng</Button>
    </>}
  </section>;
}
