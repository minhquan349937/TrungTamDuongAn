import { useConfirm } from "../hooks/useConfirm";
import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { Alert, App, Button, Form, Input, InputNumber, Select, Modal, Spin } from "antd";
import { Plus, Wallet, CheckCircle2, Clock, Receipt } from "lucide-react";
import { useAuth } from "../auth/AuthProvider";
import { DataTable, StatCard, Badge } from "../components/ui";
import { feeApi, type CreateFee, type FeeRecord, type FeeStudent } from "../services/feeApi";
import { money, today } from "../utils/format";
export function Fees() {
  const paymentDate = (value: string) => new Date(`${value}Z`).toLocaleDateString("vi-VN", { timeZone: "Asia/Ho_Chi_Minh" });
  const modal = useConfirm();
  const { user } = useAuth();
  const role = user?.role;
  const loc = useLocation();
  const { message } = App.useApp();
  const [form] = Form.useForm<CreateFee>();
  const [rows, setRows] = useState<FeeRecord[]>([]);
  const [students, setStudents] = useState<FeeStudent[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const generation = useRef(0);
  const load = useCallback(async () => {
    const request = ++generation.current;
    try {
      const [fees, profiles] = await Promise.all([feeApi.list(), feeApi.students()]);
      if (generation.current !== request) return;
      setRows(fees); setStudents(profiles); setError("");
    } catch (reason) {
      if (generation.current === request) setError((reason as Error).message);
    } finally { if (generation.current === request) setLoading(false); }
  }, []);
  useEffect(() => {
    setRows([]); setStudents([]); setLoading(true);
    void load();
    const refresh = () => { if (document.visibilityState === "visible") void load(); };
    const interval = window.setInterval(refresh, 15000);
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      generation.current++;
      window.clearInterval(interval);
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [load, user?.id]);
  const [month, setMonth] = useState(""),
    [student, setStudent] = useState("all"),
    [add, setAdd] = useState(false),
    [receipt, setReceipt] = useState<FeeRecord>();
  const history = loc.pathname === "/payments";
  const fees = rows.filter(
    (f) =>
      (!month || f.month === month) &&
      (student === "all" || f.studentId === student) &&
      (!history || f.paidAmount > 0),
  );
  const paid = fees.reduce((s, f) => s + f.paidAmount, 0),
    unpaid = fees.reduce((s, f) => s + Math.max(0, f.outstandingAmount), 0);
  const status = (fee: FeeRecord) => fee.outstandingAmount <= 0 ? "Đã thanh toán" : fee.paidAmount > 0 ? "Thanh toán một phần" : "Chưa thanh toán";
  async function createFee(input: CreateFee) {
    setSaving(true);
    try {
      await feeApi.create({ ...input, title: input.title.trim() });
      setAdd(false); form.resetFields(); setMonth(""); setStudent("all");
      message.success("Đã lưu khoản thu. Phụ huynh của học sinh có thể xem trong mục Học phí.");
      await load();
    } catch (reason) { message.error((reason as Error).message); }
    finally { setSaving(false); }
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">QUẢN LÝ CƠ SỞ</div>
          <h1>{history ? "Lịch sử thanh toán" : "Học phí & khoản thu"}</h1>
          <p>Theo dõi học phí minh bạch, đồng hành cùng phụ huynh.</p>
        </div>
        {role === "ADMIN" && (
          <Button
            type="primary"
            icon={<Plus size={16} />}
            onClick={() => { form.resetFields(); form.setFieldsValue({ month: today().slice(0, 7) }); setAdd(true); }}
          >
            Tạo khoản thu
          </Button>
        )}
      </div>
      {error && <Alert type="error" showIcon message={error} action={<Button onClick={() => void load()}>Thử lại</Button>} />}
      {loading && <Spin tip="Đang tải học phí…"><div style={{ height: 48 }} /></Spin>}
      <div className="stats-grid three">
        <StatCard
          label="Tổng khoản thu"
          value={money(paid + unpaid)}
          icon={<Wallet size={22} />}
          delta={fees.length + " khoản"}
          caption="trong kỳ đã chọn"
        />
        <StatCard
          label="Đã thanh toán"
          value={money(paid)}
          icon={<CheckCircle2 size={22} />}
          delta="Đã ghi nhận"
          caption="đã lưu trên hệ thống"
        />
        <StatCard
          label="Chưa thanh toán"
          value={money(unpaid)}
          icon={<Clock size={22} />}
          delta={fees.filter((f) => f.outstandingAmount > 0).length + " khoản"}
          caption="cần theo dõi"
        />
      </div>
      <div className="panel filter-bar">
        <label>
          Tháng học phí
          <Input
            aria-label="Tháng học phí"
            type="month"
            value={month}
            onChange={(e) => setMonth(e.target.value)}
          />
        </label>
        <label>
          Học sinh
          <Select
            aria-label="Lọc học phí theo học sinh"
            value={student}
            onChange={setStudent}
            options={[
              { value: "all", label: "Tất cả học sinh" },
              ...students.map((s) => ({ value: s.id, label: s.fullName })),
            ]}
          />
        </label>
        {!history && (
          <Button
            onClick={() => {
              setMonth("");
              setStudent("all");
            }}
          >
            Xem tất cả các tháng
          </Button>
        )}
        <Button onClick={() => void load()}>Cập nhật</Button>
      </div>
      <DataTable
        title={history ? "Các khoản đã thanh toán" : "Danh sách học phí"}
        rows={fees}
        searchText={(f) =>
          f.title +
          " " +
          f.studentName +
          " " +
          f.id
        }
        statuses={["Đã thanh toán", "Thanh toán một phần", "Chưa thanh toán"]}
        statusOf={status}
        columns={[
          {
            label: "Học sinh",
            render: (f) => (
              <b>{f.studentName}</b>
            ),
          },
          {
            label: "Khoản thu",
            render: (f) => (
              <div>
                {f.title}
                <small className="block-muted">
                  {f.code} · {f.month}
                </small>
              </div>
            ),
          },
          { label: "Số tiền", render: (f) => <b>{money(f.amount)}</b> },
          {
            label: "Trạng thái",
            render: (f) => (
              <Badge>{status(f)}</Badge>
            ),
          },
          { label: "Còn phải thu", render: (f) => money(Math.max(0, f.outstandingAmount)) },
          {
            label: "Ngày thanh toán",
            render: (f) => (f.paidAt ? paymentDate(f.paidAt) : "—"),
          },
          {
            label: "Thao tác",
            render: (f) =>
              f.paidAt ? (
                <Button
                  size="small"
                  icon={<Receipt size={14} />}
                  onClick={() => setReceipt(f)}
                >
                  Biên nhận
                </Button>
              ) : role === "ADMIN" ? (
                <Button
                  size="small"
                  type="primary"
                  onClick={() =>
                    modal.confirm({
                      title: "Ghi nhận thanh toán?",
                      content: f.title + " · " + money(f.outstandingAmount),
                      okText: "Xác nhận đã thu",
                      cancelText: "Hủy",
                      onOk: async () => {
                        try {
                          await feeApi.pay(f.id);
                          message.success("Đã ghi nhận thanh toán học phí");
                          await load();
                        } catch (reason) { message.error((reason as Error).message); throw reason; }
                      },
                    })
                  }
                >
                  Ghi nhận thu
                </Button>
              ) : (
                <span className="text-muted">Liên hệ trung tâm</span>
              ),
          },
        ]}
      />
      <Modal open={add} title="Tạo khoản thu" onCancel={() => { if (!saving) setAdd(false); }}
        footer={null} closable={!saving} maskClosable={!saving}>
        <Form form={form} layout="vertical" onFinish={createFee}>
          <Form.Item name="studentId" label="Học sinh" rules={[{ required: true, message: "Vui lòng chọn học sinh." }]}>
            <Select showSearch optionFilterProp="label" options={students.map(s => ({ value: s.id, label: `${s.fullName} · ${s.code}` }))} />
          </Form.Item>
          <Form.Item name="title" label="Nội dung khoản thu" rules={[{ required: true, whitespace: true, message: "Vui lòng nhập nội dung khoản thu." }]}>
            <Input maxLength={250} />
          </Form.Item>
          <Form.Item name="month" label="Tháng học phí" rules={[{ required: true, message: "Vui lòng chọn tháng học phí." }]}><Input type="month" /></Form.Item>
          <Form.Item name="amount" label="Số tiền (đồng)" rules={[{ required: true, message: "Vui lòng nhập số tiền." }, { type: "number", min: 1, max: 99999999999999 }]}>
            <InputNumber min={1} max={99999999999999} precision={0} style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item name="dueOn" label="Hạn thanh toán"><Input type="date" min={today()} /></Form.Item>
          <Button type="primary" htmlType="submit" loading={saving}>Lưu khoản thu</Button>
        </Form>
      </Modal>
      <Modal
        open={!!receipt}
        title="Thông tin thanh toán"
        onCancel={() => setReceipt(undefined)}
        footer={<Button onClick={() => setReceipt(undefined)}>Đóng</Button>}
      >
        {receipt && (
          <div className="receipt">
            <Receipt size={36} />
            <h2>{money(receipt.paidAmount)}</h2>
            <Badge>Đã thanh toán</Badge>
            <p>{receipt.studentName}</p>
            <p>{receipt.title}</p>
            <p>Ngày ghi nhận: {paymentDate(receipt.paidAt!)}</p>
            <small>Mã khoản thu: {receipt.code}</small>
          </div>
        )}
      </Modal>
    </>
  );
}
