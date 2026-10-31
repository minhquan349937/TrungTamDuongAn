import { useState } from "react";
import { Empty, Select, Tag } from "antd";
import type { PlanView } from "../services/interventionApi";
import { PlanProgress } from "./PlanProgress";
import { PlanMonthlyEvaluations } from "./PlanMonthlyEvaluations";

export function PlanAssessmentWorkspace({ plans, studentName, onSaved }: {
  plans: PlanView[]; studentName: string; onSaved: () => void;
}) {
  const [selectedId, setSelectedId] = useState<string>();
  const selected = plans.find(row => row.plan.id === selectedId);
  const date = (value: string) => new Date(`${value}T00:00:00`).toLocaleDateString("vi-VN");
  return <section className="ip-assessment-workspace">
    <div className="ip-section-heading"><div><h2>Đánh giá kế hoạch</h2><p>Chọn một kế hoạch của {studentName} để đánh giá từng mục tiêu và nhận xét cả kế hoạch.</p></div></div>
    <label className="ip-assessment-picker">Kế hoạch cần đánh giá
      <Select aria-label="Kế hoạch cần đánh giá" placeholder="Chọn kế hoạch học tập" value={selected?.plan.id}
        showSearch optionFilterProp="label" onChange={setSelectedId}
        options={plans.map(({ plan }) => ({ value: plan.id,
          label: `${plan.name} · Áp dụng ${date(plan.startDate)}${plan.status === "ACTIVE" ? " · Hiện tại" : ""}` }))} />
    </label>
    {!plans.length ? <Empty description="Học sinh chưa có kế hoạch học tập" /> : selected ?
      <section className="ip-plan ip-assessment-plan" key={selected.plan.id}>
        <div className="ip-plan-heading"><div><h2>{selected.plan.name}</h2><p>{studentName} · Áp dụng {date(selected.plan.startDate)} · {selected.goals.length} mục tiêu</p></div>
          <Tag color={selected.plan.status === "COMPLETED" ? "green" : selected.plan.status === "PAUSED" ? "red" : "blue"}>{({ ACTIVE: "Đang thực hiện", PAUSED: "Tạm dừng", COMPLETED: "Đã hoàn thành", DRAFT: "Bản nháp", CANCELLED: "Đã hủy" } as Record<string,string>)[selected.plan.status]}</Tag>
        </div>
        {selected.goals.length ? <PlanProgress mode="evaluate" plan={selected.plan} goals={selected.goals} onSaved={onSaved} /> : <Empty description="Kế hoạch chưa có mục tiêu" />}
        <PlanMonthlyEvaluations plan={selected.plan} studentName={studentName} onPlanChanged={onSaved} />
      </section> : <Empty description="Chọn kế hoạch để xem mục tiêu và bắt đầu đánh giá" />}
  </section>;
}
