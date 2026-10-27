import { useEffect, useState } from "react";
import { Alert, Button, Empty, Form, Input, InputNumber, Modal, Pagination, Select, Spin } from "antd";
import { useAuth } from "../auth/AuthProvider";
import { goalBankApi, goalRequest, type Program, type ProgramLevel, type Domain, type Goal, type GoalDetail, type Page } from "../services/goalBankApi";

type SharedDomain = { id: string; code: string; name: string };
type Editor = "program" | "level" | "domain" | "link" | "goal" | "editGoal" | "child";
export function GoalCatalogManager() {
  const { user } = useAuth();
  const admin = user?.role === "ADMIN";
  const [programs, setPrograms] = useState<Program[]>([]);
  const [program, setProgram] = useState("");
  const [levels, setLevels] = useState<ProgramLevel[]>([]);
  const [level, setLevel] = useState("");
  const [shared, setShared] = useState<SharedDomain[]>([]);
  const [domains, setDomains] = useState<Domain[]>([]);
  const [link, setLink] = useState("");
  const [goals, setGoals] = useState<Page<Goal> | null>(null);
  const [detail, setDetail] = useState<GoalDetail | null>(null);
  const [goal, setGoal] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [editor, setEditor] = useState<Editor | null>(null);
  const [refresh, setRefresh] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [form] = Form.useForm();
  useEffect(() => {
    let active = true;
    void goalBankApi.programs().then(rows => { if (active) setPrograms(rows); }).catch((e: Error) => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [refresh]);
  useEffect(() => {
    if (!program) return;
    let active = true;
    void Promise.all([goalBankApi.levels(program), goalRequest<SharedDomain[]>(`/api/programs/${program}/domains`)])
      .then(([a, b]) => { if (active) { setLevels(a); setShared(b); } }).catch((e: Error) => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [program, refresh]);
  useEffect(() => {
    if (!level) return;
    let active = true;
    void goalBankApi.domains(level).then(rows => { if (active) setDomains(rows); }).catch((e: Error) => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [level, refresh]);
  useEffect(() => {
    if (!link) return;
    let active = true;
    setGoals(null);
    void goalBankApi.goals(link, query, page).then(rows => { if (active) setGoals(rows); }).catch((e: Error) => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [link, query, page, refresh]);
  useEffect(() => {
    if (!goal) return;
    let active = true; setDetail(null);
    void goalBankApi.goal(goal).then(row => { if (active) setDetail(row); }).catch((e: Error) => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [goal, refresh]);
  const show = (kind: Editor) => {
    form.resetFields();
    form.setFieldsValue(kind === "editGoal" ? { ...detail, displayOrder: goals?.content.find(row => row.id === goal)?.displayOrder ?? 1 } : { displayOrder: 1, levelNumber: levels.length + 1, ageMinMonths: 0, ageMaxMonths: 48 });
    setError(""); setEditor(kind);
  };
  async function submit() {
    const values = await form.validateFields();
    setBusy(true); setError("");
    try {
      const endpoint = editor === "program" ? "/api/programs" : editor === "level" ? `/api/programs/${program}/levels`
        : editor === "domain" ? `/api/programs/${program}/domains` : editor === "link" ? `/api/levels/${level}/domain-links`
        : editor === "goal" ? `/api/level-domains/${link}/goals` : editor === "child" ? `/api/goals/${goal}/specific-goals` : `/api/goals/${goal}`;
      await goalRequest(endpoint, values, editor === "editGoal" ? "PUT" : "POST");
      setEditor(null); setRefresh(n => n + 1);
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  function archive(id: string, child = false) {
    Modal.confirm({ title: "Ngừng sử dụng mục tiêu?", content: "Các kế hoạch và quan sát đã lưu vẫn được giữ nguyên.", okText: "Ngừng sử dụng", cancelText: "Hủy",
      onOk: async () => { try { await goalRequest(child ? `/api/goals/${goal}/specific-goals/${id}` : `/api/goals/${id}`, {}, "DELETE");
        if (!child) { setGoal(""); setDetail(null); } setRefresh(n => n + 1);
      } catch (e) { setError((e as Error).message); } } });
  }
  const selectOptions = (rows: { id: string; name: string }[]) => rows.map(row => ({ value: row.id, label: row.name }));
  return <div className="ip-page">
    <div className="page-heading"><div><div className="eyebrow">THƯ VIỆN</div><h1>Ngân hàng mục tiêu</h1><p>Chương trình → cấp độ → lĩnh vực → mục tiêu.</p></div>
      {admin && <Button type="primary" onClick={() => show("program")}>Thêm chương trình</Button>}</div>
    {error && !editor && <Alert type="error" message={error} closable onClose={() => setError("")} />}
    <section className="panel" style={{ padding: 24, display: "grid", gap: 16 }}>
      <label>Chương trình<Select aria-label="Chương trình thư viện" style={{ width: "100%" }} value={program || undefined} options={selectOptions(programs)} placeholder="Chọn chương trình"
        onChange={id => { setProgram(id); setLevels([]); setShared([]); setLevel(""); setDomains([]); setLink(""); setGoal(""); setDetail(null); setGoals(null); setPage(0); setQuery(""); }} /></label>
      <label>Cấp độ<Select aria-label="Cấp độ thư viện" disabled={!program} style={{ width: "100%" }} value={level || undefined} options={levels.map(row => ({ value: row.id, label: `${row.name} · ${row.ageMinMonths}–${row.ageMaxMonths} tháng` }))} placeholder="Chọn cấp độ"
        onChange={id => { setLevel(id); setDomains([]); setLink(""); setGoal(""); setDetail(null); setGoals(null); setPage(0); setQuery(""); }} /></label>
      <label>Lĩnh vực<Select aria-label="Lĩnh vực thư viện" disabled={!level} style={{ width: "100%" }} value={link || undefined} options={domains.map(row => ({ value: row.levelDomainId, label: row.name }))} placeholder="Chọn lĩnh vực"
        onChange={id => { setLink(id); setGoal(""); setDetail(null); setGoals(null); setPage(0); setQuery(""); }} /></label>
      {admin && <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
        <Button disabled={!program} onClick={() => show("level")}>Thêm cấp độ</Button>
        <Button disabled={!program} onClick={() => show("domain")}>Thêm lĩnh vực vào chương trình</Button>
        <Button disabled={!level} onClick={() => show("link")}>Gắn lĩnh vực vào cấp độ</Button>
        <Button disabled={!link} onClick={() => show("goal")}>Thêm mục tiêu</Button>
      </div>}
    </section>
    {link && <section className="panel" style={{ padding: 24, marginTop: 20 }}>
      <Input.Search placeholder="Tìm mã hoặc tên mục tiêu" value={query} onChange={e => { setQuery(e.target.value); setPage(0); }} />
      {goals ? goals.content.map(row => <article className="ip-progress-row" key={row.id}>
        <div><b>{row.code}</b><p>{row.name}</p></div><Button onClick={() => { setGoal(row.id); setDetail(null); }}>Xem chi tiết</Button>
      </article>) : <Spin />}
      {goals?.totalElements === 0 && <Empty description="Chưa có mục tiêu" />}
      <Pagination current={page + 1} pageSize={20} total={goals?.totalElements ?? 0} showSizeChanger={false} onChange={n => setPage(n - 1)} />
    </section>}
    {detail && <section className="panel" style={{ padding: 24, marginTop: 20 }}><h2>{detail.code} · {detail.name}</h2><p>{detail.description}</p>
      {admin && <><Button onClick={() => show("editGoal")}>Sửa mục tiêu</Button> <Button danger onClick={() => archive(detail.id)}>Ngừng sử dụng</Button></>}
      {detail.supportsSpecificGoals && <><h3>Mục tiêu cụ thể</h3>{admin && <Button onClick={() => show("child")}>Thêm mục tiêu cụ thể</Button>}
        {detail.specificGoals.map(row => <article className="ip-progress-row" key={row.id}><div><b>{row.code}</b><p>{row.description}</p><small>{row.ageMinMonths}–{row.ageMaxMonths} tháng</small></div>
          {admin && <Button danger onClick={() => archive(row.id, true)}>Ngừng sử dụng</Button>}</article>)}
        {!detail.specificGoals.length && <p>Mục tiêu này có thể được chọn trực tiếp.</p>}</>}
    </section>}
    <Modal title={editor === "link" ? "Gắn lĩnh vực dùng chung" : "Thông tin thư viện"} open={!!editor} onCancel={() => setEditor(null)} onOk={() => void submit()} confirmLoading={busy} destroyOnClose>
      {error && <Alert type="error" message={error} />}
      <Form form={form} layout="vertical">
        {editor === "link" ? <Form.Item name="domainId" label="Lĩnh vực cùng chương trình" rules={[{ required: true }]}><Select options={selectOptions(shared.filter(row => !domains.some(d => d.id === row.id)))} /></Form.Item>
          : <>{editor !== "level" && <Form.Item name="code" label="Mã" rules={[{ required: true, whitespace: true }]}><Input maxLength={editor === "goal" || editor === "editGoal" || editor === "child" ? 50 : 30} /></Form.Item>}
            {editor !== "child" && <Form.Item name="name" label="Tên" rules={[{ required: true, whitespace: true }]}><Input.TextArea /></Form.Item>}
            {["program", "goal", "editGoal", "child"].includes(editor ?? "") && <Form.Item name="description" label="Mô tả" rules={editor === "child" ? [{ required: true, whitespace: true }] : []}><Input.TextArea rows={4} /></Form.Item>}</>}
        {editor === "level" && <Form.Item name="levelNumber" label="Cấp độ" rules={[{ required: true }]}><InputNumber min={1} precision={0} /></Form.Item>}
        {(editor === "level" || editor === "child") && <><Form.Item name="ageMinMonths" label="Từ tháng tuổi" rules={[{ required: editor === "level" }]}><InputNumber min={0} precision={0} /></Form.Item>
          <Form.Item name="ageMaxMonths" label="Đến tháng tuổi" rules={[{ required: editor === "level" }]}><InputNumber min={0} precision={0} /></Form.Item></>}
        {editor !== "program" && <Form.Item name="displayOrder" label="Thứ tự" rules={[{ required: true }]}><InputNumber min={0} precision={0} /></Form.Item>}
      </Form>
    </Modal>
  </div>;
}
