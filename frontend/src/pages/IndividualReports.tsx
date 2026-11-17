import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Alert, App, Button, Descriptions, Empty, Input, Modal, Space, Spin, Table, Tag } from "antd";
import { Download, FileText, ArrowLeft } from "lucide-react";
import { individualReportApi, type IndividualReport, type ReportEdit, type ReportStudent, type ReportSummary } from "../services/individualReportApi";
import "./individual-reports.css";

const root = "/individual/report";
const date = (value: string) => new Date(`${value.slice(0, 10)}T00:00:00`).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
const time = (value: string) => {
  const timestamp = new Date(`${value}Z`);
  const options = { timeZone: "Asia/Ho_Chi_Minh" };
  return `${timestamp.toLocaleDateString("vi-VN", { ...options, day: "2-digit", month: "2-digit", year: "numeric" })} · ${timestamp.toLocaleTimeString("vi-VN", { ...options, hour12: false })}`;
};
const period = (start: string, end: string | null) => `${date(start)} – ${end ? date(end) : "Chưa có ngày kết thúc"}`;
const month = (value: string) => new Date(`${value}Z`).toLocaleDateString("vi-VN", { month: "long", year: "numeric", timeZone: "Asia/Ho_Chi_Minh" });
const resultLabels: Record<string, string> = { ACHIEVED: "Đạt", NOT_ACHIEVED: "Không đạt", NOT_STARTED: "Chưa thực hiện", HISTORICAL_UNAVAILABLE: "Không đủ dữ liệu lịch sử" };
const editValues = (detail: IndividualReport): ReportEdit => ({
  strengths: detail.sections.strengths, difficulties: detail.sections.difficulties,
  supportDirections: detail.sections.supportDirections, expectedUpdatedAt: detail.report.updatedAt,
});

export function IndividualReports() {
  const { pathname } = useLocation();
  const parts = pathname.slice(root.length).split("/").filter(Boolean).map(decodeURIComponent);
  return parts[1] ? <ReportEditor reportId={parts[1]} studentId={parts[0]} /> : parts[0] ? <StudentReports studentId={parts[0]} /> : <ReportStudents />;
}

function ReportStudents() {
  const nav = useNavigate();
  const [rows, setRows] = useState<ReportStudent[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    const timer = setTimeout(() => {
      setLoading(true); setError("");
      individualReportApi.students(search).then(data => { if (active) setRows(data); })
        .catch((cause: Error) => { if (active) { setRows([]); setError(cause.message); } })
        .finally(() => { if (active) setLoading(false); });
    }, 250);
    return () => { active = false; clearTimeout(timer); };
  }, [search, retry]);
  return <>
    <div className="page-heading"><div><div className="eyebrow">QUẢN LÝ LỚP CÁ NHÂN</div><h1>Báo cáo cá nhân</h1><p>Xem, chỉnh sửa nhận xét đã lưu và xuất báo cáo đánh giá cá nhân.</p></div></div>
    <section className="panel ir-panel">
      <Input.Search aria-label="Tìm kiếm học sinh có báo cáo" placeholder="Tìm theo mã hoặc tên học sinh" value={search} onChange={e => setSearch(e.target.value)} allowClear style={{ maxWidth: 440, marginBottom: 20 }} />
      {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => setRetry(n => n + 1)}>Thử lại</Button>} />}
      <Table rowKey="id" dataSource={rows} loading={loading} locale={{ emptyText: "Chưa có học sinh có báo cáo phù hợp." }} pagination={{ pageSize: 10 }} columns={[
        { title: "Mã học sinh", dataIndex: "code" }, { title: "Họ tên", dataIndex: "fullName" },
        { title: "Số báo cáo đã đánh giá", dataIndex: "reportCount" },
        { title: "Thao tác", render: (_: unknown, row: ReportStudent) => <Button icon={<FileText size={16} />} onClick={() => nav(`${root}/${row.id}`)}>Xem báo cáo</Button> },
      ]} />
    </section>
  </>;
}

function usePdf() {
  const [busy, setBusy] = useState(false);
  const [url, setUrl] = useState("");
  const [error, setError] = useState("");
  useEffect(() => () => { if (url) URL.revokeObjectURL(url); }, [url]);
  async function open(id: string, download: boolean) {
    setBusy(true); setError("");
    try {
      const blob = await individualReportApi.pdf(id, download);
      const objectUrl = URL.createObjectURL(blob);
      if (download) {
        const link = document.createElement("a"); link.href = objectUrl; link.download = `bao-cao-${id}.pdf`;
        document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
      } else setUrl(objectUrl);
    } catch (cause) { setError((cause as Error).message); }
    finally { setBusy(false); }
  }
  return { busy, error, open, preview: <Modal title="Xem trước PDF đã lưu" open={!!url} onCancel={() => setUrl("")} footer={null} width={1000}>
    {url && <iframe title="Bản xem trước báo cáo PDF" src={url} style={{ width: "100%", height: "72vh", border: 0 }} />}
  </Modal> };
}

function StudentReports({ studentId }: { studentId: string }) {
  const nav = useNavigate();
  const [student, setStudent] = useState<ReportStudent>();
  const [rows, setRows] = useState<ReportSummary[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  const pdf = usePdf();
  useEffect(() => {
    let active = true; setLoading(true); setError("");
    Promise.all([individualReportApi.student(studentId), individualReportApi.list(studentId)]).then(([info, data]) => { if (active) { setStudent(info); setRows(data); } })
      .catch((cause: Error) => { if (active) setError(cause.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [studentId, retry]);
  return <>
    <div className="page-heading"><div><h1>Báo cáo của {student?.fullName ?? "học sinh"}</h1><p>{student?.code} · Mỗi bản đánh giá được lưu thành một báo cáo riêng.</p></div><Button icon={<ArrowLeft size={16} />} onClick={() => nav(root)}>Danh sách học sinh</Button></div>
    {(error || pdf.error) && <Alert type="error" showIcon message={error || pdf.error} action={error ? <Button onClick={() => setRetry(n => n + 1)}>Thử lại</Button> : undefined} />}
    <section className="panel ir-panel"><Table rowKey="id" loading={loading} dataSource={rows} scroll={{ x: 950 }} pagination={{ pageSize: 10 }} locale={{ emptyText: "Học sinh chưa có báo cáo đánh giá kế hoạch." }} columns={[
      { title: "Tháng/năm đánh giá", dataIndex: "evaluatedAt", render: month },
      { title: "Tên kế hoạch", dataIndex: "planName" },
      { title: "Kỳ kế hoạch", render: (_: unknown, row: ReportSummary) => period(row.planStartDate, row.planEndDate) },
      { title: "Ngày đánh giá", dataIndex: "evaluatedAt", render: time },
      { title: "Giáo viên đánh giá", dataIndex: "evaluatedByName" },
      { title: "Thao tác", render: (_: unknown, row: ReportSummary) => <Space wrap>
        <Button onClick={() => nav(`${root}/${studentId}/${row.id}`)}>{row.editable ? "Chỉnh sửa báo cáo" : "Xem báo cáo"}</Button>
        <Button icon={<Download size={15} />} disabled={pdf.busy} onClick={() => void pdf.open(row.id, true)}>Tải xuống PDF</Button>
      </Space> },
    ]} /></section>{pdf.preview}
  </>;
}

function ReportEditor({ reportId, studentId }: { reportId: string; studentId: string }) {
  const nav = useNavigate();
  const { message } = App.useApp();
  const [detail, setDetail] = useState<IndividualReport>();
  const [form, setForm] = useState<ReportEdit>({ strengths: "", difficulties: "", supportDirections: "", expectedUpdatedAt: "" });
  const [converted, setConverted] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const pdf = usePdf();
  const dirty = !!detail && (converted || form.strengths !== detail.sections.strengths || form.difficulties !== detail.sections.difficulties || form.supportDirections !== detail.sections.supportDirections);
  useEffect(() => {
    let active = true; setLoading(true); setError("");
    individualReportApi.get(reportId).then(data => {
      if (!active) return;
      if (data.report.studentId !== studentId) throw new Error("Báo cáo không thuộc học sinh đã chọn.");
      setDetail(data); setForm(editValues(data)); setConverted(false);
    }).catch((cause: Error) => { if (active) setError(cause.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [reportId, studentId, retry]);
  useEffect(() => {
    if (!dirty) return;
    const beforeUnload = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    const onLink = (event: MouseEvent) => {
      const target = event.target instanceof Element ? event.target.closest("a[href]") : null;
      if (target && target.getAttribute("href") !== window.location.hash && !window.confirm("Bạn có chỉnh sửa chưa lưu. Rời khỏi báo cáo này?")) {
        event.preventDefault(); event.stopPropagation();
      }
    };
    window.addEventListener("beforeunload", beforeUnload); document.addEventListener("click", onLink, true);
    return () => { window.removeEventListener("beforeunload", beforeUnload); document.removeEventListener("click", onLink, true); };
  }, [dirty]);
  async function save() {
    setSaving(true); setError("");
    try {
      const updated = await individualReportApi.update(reportId, form);
      setDetail(updated); setForm(editValues(updated)); setConverted(false);
      message.success("Đã lưu chỉnh sửa báo cáo.");
    } catch (cause) { setError((cause as Error).message); }
    finally { setSaving(false); }
  }
  const back = () => { if (!dirty || window.confirm("Bạn có chỉnh sửa chưa lưu. Rời khỏi báo cáo này?")) nav(`${root}/${studentId}`); };
  if (loading) return <Spin size="large" />;
  if (!detail) return <><Alert type="error" message={error || "Không tìm thấy báo cáo."} action={<Button onClick={() => setRetry(n => n + 1)}>Thử lại</Button>} /><Button onClick={back}>Quay lại</Button></>;
  const fieldsEnabled = detail.editable && (detail.sections.structured || converted);
  return <>
    <div className="page-heading"><div><div className="eyebrow">BÁO CÁO CÁ NHÂN</div><h1>{detail.editable ? "Chỉnh sửa báo cáo" : "Xem báo cáo"}</h1><p>{detail.report.studentName} · {detail.studentCode}</p></div><Button onClick={back}>Quay lại</Button></div>
    {(error || pdf.error) && <Alert type="error" showIcon message={error || pdf.error} action={error ? <Button onClick={() => {
      if (!dirty || window.confirm("Tải lại sẽ thay nội dung đang chỉnh sửa bằng bản đã lưu. Tiếp tục?")) setRetry(n => n + 1);
    }}>Tải lại bản đã lưu</Button> : undefined} />}
    {!detail.editable && <Alert type="info" showIcon message={detail.report.sentToParent ? "Báo cáo lịch sử đã khóa chỉnh sửa." : "Bạn có thể xem báo cáo; chỉ người tạo hoặc quản trị viên được sửa nội dung."} />}
    <section className="panel ir-panel">
      <Descriptions column={{ xs: 1, md: 2 }} items={[
        { key: "student", label: "Họ tên học sinh", children: detail.report.studentName },
        { key: "plan", label: "Tên kế hoạch", children: detail.report.planName },
        { key: "period", label: "Kỳ kế hoạch", children: period(detail.planStartDate, detail.planEndDate) },
        { key: "author", label: "Người đánh giá", children: detail.report.evaluatedByName },
        { key: "date", label: "Ngày đánh giá", children: time(detail.report.evaluatedAt) },
        { key: "cutoff", label: "Kết quả tính đến", children: time(detail.resultsAsOf) },
      ]} />
      {detail.warnings.map(warning => <Alert key={warning} type="warning" showIcon message={warning} style={{ marginBottom: 12 }} />)}
      <h2>Kết quả đánh giá các mục tiêu</h2>
      {detail.goals.length === 0 ? <Empty description="Chưa có mục tiêu trong kỳ báo cáo." /> : [...new Set(detail.goals.map(goal => goal.domain))].map(domain => <div key={domain}>
        <h3>{domain}</h3><Table rowKey="id" pagination={false} dataSource={detail.goals.filter(goal => goal.domain === domain)} columns={[
          { title: "Mục tiêu", render: (_: unknown, goal: IndividualReport["goals"][number]) => `${goal.code} · ${goal.name}` },
          { title: "Kết quả", dataIndex: "status", render: (status: string) => <Tag color={status === "ACHIEVED" ? "green" : status === "NOT_ACHIEVED" ? "orange" : "default"}>{resultLabels[status] ?? "Chưa xác định"}</Tag> },
          { title: "Tỷ lệ hoàn thành", dataIndex: "achievementPercent", render: (value: number | null) => value === null ? "—" : `${value}%` },
          { title: "Ghi nhận", dataIndex: "notes" },
        ]} />
      </div>)}
    </section>
    <section className="panel ir-panel ir-editor">
      {!detail.sections.structured && <>
        <Alert type="info" showIcon message="Nhận xét cũ chưa có đủ ba phần. Văn bản gốc được giữ nguyên khi xem và tải PDF." />
        <label>Văn bản nhận xét gốc<Input.TextArea aria-label="Văn bản nhận xét gốc" value={detail.sections.originalText} readOnly rows={7} /></label>
        {detail.editable && !converted && <Button onClick={() => {
          setForm(current => ({ ...current, strengths: detail.sections.originalText })); setConverted(true);
        }}>Đưa văn bản gốc vào phần I để phân chia, chỉnh sửa</Button>}
      </>}
      {(detail.sections.structured || converted) && <>
        <label>I. Tiến bộ<Input.TextArea aria-label="I. Tiến bộ" rows={6} value={form.strengths} disabled={!fieldsEnabled || saving} onChange={e => setForm(current => ({ ...current, strengths: e.target.value }))} /></label>
        <label>II. Khó khăn cần hỗ trợ<Input.TextArea aria-label="II. Khó khăn cần hỗ trợ" rows={6} value={form.difficulties} disabled={!fieldsEnabled || saving} onChange={e => setForm(current => ({ ...current, difficulties: e.target.value }))} /></label>
        <label>III. Định hướng hỗ trợ<Input.TextArea aria-label="III. Định hướng hỗ trợ" rows={6} value={form.supportDirections} disabled={!fieldsEnabled || saving} onChange={e => setForm(current => ({ ...current, supportDirections: e.target.value }))} /></label>
      </>}
      {dirty && <Alert type="info" message="Có chỉnh sửa chưa lưu. Hãy lưu chỉnh sửa trước khi xem trước hoặc tải PDF." />}
      <Space wrap>
        {detail.editable && <Button aria-label="Lưu chỉnh sửa" type="primary" loading={saving} disabled={!fieldsEnabled || !dirty || ![form.strengths, form.difficulties, form.supportDirections].some(value => value.trim())} onClick={() => void save()}>Lưu chỉnh sửa</Button>}
        <Button disabled={dirty || saving || pdf.busy} onClick={() => void pdf.open(reportId, false)}>Xem trước PDF</Button>
        <Button icon={<Download size={16} />} disabled={dirty || saving || pdf.busy} onClick={() => void pdf.open(reportId, true)}>Tải xuống PDF</Button>
        <Button disabled={saving} onClick={back}>Quay lại</Button>
      </Space>
    </section>{pdf.preview}
  </>;
}
