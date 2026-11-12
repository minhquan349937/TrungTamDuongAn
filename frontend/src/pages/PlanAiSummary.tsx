import { useState } from "react";
import { Alert, Button, Input, Modal } from "antd";
import { planEvaluationApi } from "../services/planEvaluationApi";

export function PlanAiSummary({ planId, notes, onUse, disabled, onBusy }: {
  planId: string; notes: string; onUse: (text: string) => void; disabled: boolean; onBusy: (busy: boolean) => void;
}) {
  const [keywords, setKeywords] = useState("");
  const [suggestion, setSuggestion] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [confirm, setConfirm] = useState(false);
  async function generate() {
    setBusy(true); onBusy(true); setError(""); setSuggestion(null);
    try { setSuggestion((await planEvaluationApi.aiSummary(planId, keywords)).generatedText); }
    catch (cause) { setError((cause as Error).message); }
    finally { setBusy(false); onBusy(false); }
  }
  return <div style={{ marginBottom: 20 }}>
    <p>AI chỉ gợi ý để giáo viên xem xét và chỉnh sửa. Không nhập tên, thông tin liên hệ hoặc thông tin định danh của học sinh trong ghi nhận. Ghi chú đánh giá mục tiêu cũng được gửi tới Gemini.</p>
    <label>Từ khóa / Ghi nhận của giáo viên
      <Input.TextArea aria-label="Từ khóa / Ghi nhận của giáo viên" rows={3} maxLength={2000} showCount
        value={keywords} disabled={disabled || busy} onChange={e => setKeywords(e.target.value)} />
    </label>
    <Button style={{ marginTop: 12 }} loading={busy} disabled={disabled || busy} onClick={() => void generate()}>AI viết nhận xét</Button>
    {busy && <p role="status">AI đang viết nhận xét…</p>}
    {error && <Alert style={{ marginTop: 12 }} type="error" showIcon message={error} />}
    {suggestion !== null && <div style={{ marginTop: 12 }}>
      <label>Nhận xét AI đề xuất
        <Input.TextArea aria-label="Nhận xét AI đề xuất" data-testid="ai-suggestion" rows={8} maxLength={16000} showCount
          value={suggestion} disabled={disabled || busy} onChange={e => setSuggestion(e.target.value)} />
      </label>
      <p>Bạn có thể chỉnh sửa nội dung rồi bấm “Sử dụng nhận xét này” để đưa vào nhận xét giáo viên.</p>
      <Button disabled={disabled || busy || !suggestion.trim()} onClick={() => { if (notes.trim()) setConfirm(true); else onUse(suggestion); }}>Sử dụng nhận xét này</Button>
    </div>}
    <Modal title="Thay thế nhận xét hiện tại?" open={confirm} okText="Thay thế nhận xét" cancelText="Giữ nhận xét hiện tại"
      onCancel={() => setConfirm(false)} onOk={() => { if (suggestion?.trim()) onUse(suggestion); setConfirm(false); }}>
      Nội dung đang nhập sẽ được thay bằng gợi ý AI. Bạn có thể chỉnh sửa trước khi lưu.
    </Modal>
  </div>;
}
