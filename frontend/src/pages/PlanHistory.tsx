import { useEffect, useState } from "react";
import { Alert, Button, Empty, Modal, Select, Spin, Tag } from "antd";
import { CheckCheck } from "lucide-react";
import type { PlanView } from "../services/interventionApi";
import { planEvaluationApi, type PlanEvaluation } from "../services/planEvaluationApi";

const day = (value: string) => new Date(`${value}T00:00:00`).toLocaleDateString("vi-VN");
const timestamp = (value?: string) => value ? new Date(`${value}Z`).toLocaleString("vi-VN", { timeZone: "Asia/Ho_Chi_Minh" }) : "Chưa có thông tin";
const labels: Record<string,string> = { ACTIVE: "KH hiện tại", COMPLETED: "Đã hoàn thành", PAUSED: "Tạm dừng", DRAFT: "Bản nháp", CANCELLED: "Đã hủy" };

export function PlanHistory({ plans, studentName }: { plans: PlanView[]; studentName: string }) {
  const [view, setView] = useState<PlanView | null>(null);
  const [reports, setReports] = useState<PlanEvaluation[]>([]);
  const [reportId, setReportId] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const planId = view?.plan.id;
  useEffect(() => {
    if (!planId) return;
    let active = true;
    setReports([]); setReportId(undefined); setError(""); setLoading(true);
    planEvaluationApi.list(planId).then(rows => { if (active) setReports(rows); })
      .catch((cause: Error) => { if (active) setError(cause.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [planId]);
  const report = reports.find(row => row.id === reportId) ?? reports[0];
  function period(row: PlanView) {
    const { startDate, endDate } = row.plan;
    if (!endDate) return `Từ ${day(startDate)}`;
    const days = Math.round((Date.parse(`${endDate}T00:00:00Z`) - Date.parse(`${startDate}T00:00:00Z`)) / 86400000) + 1;
    return `${day(startDate)} – ${day(endDate)} (${days} ngày)`;
  }
  if (!plans.length) return <Empty description="Chưa có kế hoạch học tập" />;
  return <>
    <div className="panel ip-history-table-wrap">
      <table className="ip-table ip-history-table">
        <thead><tr><th>Tên kế hoạch</th><th>Thời gian dạy</th><th>Trạng thái</th><th>Gửi PH</th><th>Báo cáo KH</th></tr></thead>
        <tbody>{plans.map(row => <tr key={row.plan.id} className={row.plan.status === "ACTIVE" ? "ip-history-current" : ""}>
          <td>{row.plan.name}</td><td>{period(row)}</td>
          <td><span className={`ip-history-status ${row.plan.status.toLowerCase()}`}><CheckCheck size={18} />{labels[row.plan.status] ?? row.plan.status}</span></td>
          <td>{row.plan.sentToParent ? "Đã công bố" : "Chưa công bố"}</td>
          <td><Button type="link" className="ip-view-plan-report" disabled={!row.plan.hasEvaluation} onClick={() => setView(row)}>Xem đánh giá</Button>
            {!row.plan.hasEvaluation && <small className="ip-history-no-report">Chưa đánh giá</small>}</td>
        </tr>)}</tbody>
      </table>
    </div>
    <Modal title={`Đánh giá · ${view?.plan.name ?? ""}`} open={!!view} onCancel={() => setView(null)} width={1280}
      className="ip-history-report-modal" footer={<Button onClick={() => setView(null)}>Đóng</Button>}>
      {loading ? <Spin /> : error ? <Alert type="error" showIcon message={error} /> : report ? <>
        {reports.length > 1 && <label className="ip-history-report-picker">Lần đánh giá
          <Select aria-label="Lần đánh giá kế hoạch" value={report.id} onChange={setReportId} options={reports.map(row => ({ value: row.id,
            label: `${timestamp(row.evaluatedAt)} · ${row.evaluatedByName}${row.sentToParent ? " · Đã công bố" : " · Bản nháp"}` }))} />
        </label>}
        <section className="ip-history-report-summary">
          <dl><div><dt>Ngày tạo kế hoạch</dt><dd>{timestamp(view?.plan.createdAt)}</dd></div>
            <div><dt>Tên học sinh</dt><dd>{studentName}</dd></div>
            <div><dt>Tên giáo viên</dt><dd>{report.evaluatedByName}</dd></div>
            <div><dt>Ngày đánh giá</dt><dd>{timestamp(report.evaluatedAt)}</dd></div></dl>
          <h3>Đánh giá của giáo viên</h3><p>{report.evaluationNotes}</p>
          <Tag color={report.sentToParent ? "green" : "gold"}>{report.sentToParent ? "Đã công bố cho phụ huynh" : "Bản nháp"}</Tag>
        </section>
        <h3 className="ip-history-goals-heading">Mục tiêu của kế hoạch · Kết quả hiện tại</h3>
        <div className="ip-history-table-wrap">
          <table className="ip-table ip-history-goals-table"><thead><tr><th>STT</th><th>Chương trình / Cấp độ</th><th>Lĩnh vực</th><th>Mục tiêu</th><th>Tỷ lệ hoàn thành</th><th>Đánh giá</th></tr></thead>
            <tbody>{view?.goals.map((goal, index) => {
              const s = goal.goalSnapshot, percent = goal.achievementPercent;
              return <tr key={goal.id}><td>{index + 1}</td><td>{s.program_name}<small>{s.level_name ?? `Cấp độ ${s.level_number ?? ""}`}</small></td><td>{s.domain_name}</td>
                <td><b>{s.code} · {s.name}</b>{goal.specificGoalId && <small>Mục tiêu chính: {s.goal_code} · {s.goal_name}</small>}{s.description && s.description !== s.name && <p>{s.description}</p>}</td>
                <td>{percent == null ? "Chưa thực hiện" : `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(percent)}%`}</td>
                <td><Tag color={percent == null ? "default" : percent >= 80 ? "green" : "orange"}>{percent == null ? "Chưa thực hiện" : percent >= 80 ? "Đã hoàn thành" : "Lặp lại mục tiêu"}</Tag></td></tr>;
            })}</tbody>
          </table>
          {!view?.goals.length && <Empty description="Kế hoạch chưa có mục tiêu" />}
        </div>
      </> : <Empty description="Kế hoạch chưa có đánh giá" />}
    </Modal>
  </>;
}
