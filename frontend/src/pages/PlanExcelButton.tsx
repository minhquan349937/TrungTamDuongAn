import { useState } from "react";
import { Alert, Button, Input, Modal } from "antd";
import { Download } from "lucide-react";
import { planExcelApi } from "../services/planExcelApi";
import { fileStem } from "../utils/downloadFileName";

export function PlanExcelButton({ planId, planName, studentName }: { planId: string; planName: string; studentName: string }) {
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(false);
  const proposedName = `${studentName} - ${planName}`;
  const [fileName, setFileName] = useState(proposedName);
  const [error, setError] = useState("");
  const cleanName = fileStem(fileName, "xlsx");
  async function download() {
    if (!cleanName) return;
    setBusy(true); setError("");
    try {
      const blob = await planExcelApi.download(planId);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `${cleanName}.xlsx`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setOpen(false);
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return <>
    <Button icon={<Download size={16} />} onClick={() => { setFileName(proposedName); setError(""); setOpen(true); }}>Xuất Excel</Button>
    <Modal title="Đặt tên tệp Excel" open={open} okText="Tải Excel" okButtonProps={{ disabled: !cleanName }}
      confirmLoading={busy} onOk={() => void download()} onCancel={() => { if (!busy) setOpen(false); }}>
      {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
      <Input autoFocus aria-label="Tên tệp Excel" value={fileName} maxLength={120}
        onChange={event => setFileName(event.target.value)} onPressEnter={() => void download()} addonAfter=".xlsx" />
    </Modal>
  </>;
}
