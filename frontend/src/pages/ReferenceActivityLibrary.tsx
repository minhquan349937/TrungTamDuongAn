import { useState } from "react";
import { Alert, Button, Input } from "antd";
import { suggestedActivityApi } from "../services/suggestedActivityApi";

export function ReferenceActivityLibrary() {
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  async function add() {
    const value = name.trim();
    if (!value) return;
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      await suggestedActivityApi.createLibrary(value);
      setSuccess(`Đã lưu hoạt động gợi ý: ${value}`);
      setName("");
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return <div className="ip-page">
    <div className="page-heading"><div><div className="eyebrow">THƯ VIỆN THAM KHẢO</div>
      <h1>Thêm hoạt động gợi ý</h1></div></div>
    <section className="panel" style={{ padding: 24, display: "grid", gap: 16, maxWidth: 720 }}>
      {error && <Alert type="error" message={error} closable onClose={() => setError("")} />}
      {success && <Alert type="success" message={success} closable onClose={() => setSuccess("")} />}
      <Input aria-label="Tên hoạt động gợi ý" maxLength={255} value={name}
        onChange={event => { setName(event.target.value); setSuccess(""); }}
        onPressEnter={() => void add()} placeholder="Nhập hoạt động gợi ý" />
      <Button type="primary" loading={busy} disabled={!name.trim()} onClick={() => void add()}>
        Thêm hoạt động gợi ý
      </Button>
    </section>
  </div>;
}
