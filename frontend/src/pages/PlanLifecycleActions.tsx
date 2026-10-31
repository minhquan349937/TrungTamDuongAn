import { useEffect, useState } from "react";
import { App, Button } from "antd";
import { CheckCircle2, PauseCircle } from "lucide-react";
import { useConfirm } from "../hooks/useConfirm";
import { goalRequest, type Plan } from "../services/goalBankApi";

export function PlanLifecycleActions({ plan, hasEvaluation, disabled, onSaved }: {
  plan: Plan; hasEvaluation: boolean; disabled: boolean; onSaved: () => void;
}) {
  const modal = useConfirm();
  const { message } = App.useApp();
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState(plan.status);
  useEffect(() => setStatus(plan.status), [plan.status]);
  const ended = status === "COMPLETED" || status === "CANCELLED";
  function confirm(next: "PAUSED" | "COMPLETED") {
    const completing = next === "COMPLETED";
    modal.confirm({
      title: completing ? "Hoàn thành kế hoạch?" : "Tạm dừng kế hoạch?",
      content: `Bạn xác nhận ${completing ? "hoàn thành" : "tạm dừng"} kế hoạch “${plan.name}”?`,
      okText: completing ? "Xác nhận hoàn thành" : "Xác nhận tạm dừng", cancelText: "Hủy",
      okButtonProps: completing ? { className: "ip-complete-plan-button" } : { danger: true },
      async onOk() {
        setBusy(true);
        try {
          await goalRequest(`/api/plans/${plan.id}/status`, { status: next }, "PATCH");
          setStatus(next); onSaved();
          message.success(completing ? "Đã hoàn thành kế hoạch" : "Đã tạm dừng kế hoạch");
        } catch (cause) {
          message.error((cause as Error).message); throw cause;
        } finally { setBusy(false); }
      },
    });
  }
  return <div className="ip-plan-lifecycle">
    <div className="ip-plan-lifecycle-buttons">
      <Button danger type="primary" icon={<PauseCircle size={17} />} disabled={disabled || busy || ended || status === "PAUSED"} onClick={() => confirm("PAUSED")}>Tạm dừng kế hoạch</Button>
      <Button type="primary" className="ip-complete-plan-button" icon={<CheckCircle2 size={17} />} disabled={disabled || busy || ended || !hasEvaluation} onClick={() => confirm("COMPLETED")}>Hoàn thành kế hoạch</Button>
    </div>
    {!hasEvaluation && <small>Cần lưu đánh giá cả kế hoạch trước khi hoàn thành.</small>}
  </div>;
}
