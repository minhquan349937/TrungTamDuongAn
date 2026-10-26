import { useConfirm } from "../hooks/useConfirm";
import { useState } from "react";
import { useLocation } from "react-router-dom";
import { Button, Input, Select, Modal } from "antd";
import { Plus, Wallet, CheckCircle2, Clock, Receipt } from "lucide-react";
import { useDemo } from "../hooks/useDemo";
import { DataTable, StatCard, Badge } from "../components/ui";
import { RecordEditor } from "../features/RecordEditor";
import { mockService } from "../services/mockService";
import { money, dateLabel, today } from "../utils/format";
import type { Fee } from "../types";
export function Fees() {
  const modal = useConfirm();
  const { db, view, role, run } = useDemo(),
    loc = useLocation();
  const [month, setMonth] = useState(today().slice(0, 7)),
    [student, setStudent] = useState("all"),
    [add, setAdd] = useState(false),
    [receipt, setReceipt] = useState<Fee>();
  const history = loc.pathname === "/payments";
  const fees = view.fees.filter(
    (f) =>
      (!month || f.month === month) &&
      (student === "all" || f.studentId === student) &&
      (!history || f.paidAt),
  );
  const paid = fees.filter((f) => f.paidAt).reduce((s, f) => s + f.amount, 0),
    unpaid = fees.filter((f) => !f.paidAt).reduce((s, f) => s + f.amount, 0);
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
            onClick={() => setAdd(true)}
          >
            Tạo khoản thu
          </Button>
        )}
      </div>
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
          caption="trong dữ liệu demo"
        />
        <StatCard
          label="Chưa thanh toán"
          value={money(unpaid)}
          icon={<Clock size={22} />}
          delta={fees.filter((f) => !f.paidAt).length + " khoản"}
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
              ...view.students.map((s) => ({ value: s.id, label: s.name })),
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
      </div>
      <DataTable
        title={history ? "Các khoản đã thanh toán" : "Danh sách học phí"}
        rows={fees}
        searchText={(f) =>
          f.title +
          " " +
          db.students.find((s) => s.id === f.studentId)?.name +
          " " +
          f.id
        }
        statuses={["Đã thanh toán", "Chưa thanh toán"]}
        statusOf={(f) => (f.paidAt ? "Đã thanh toán" : "Chưa thanh toán")}
        columns={[
          {
            label: "Học sinh",
            render: (f) => (
              <b>{db.students.find((s) => s.id === f.studentId)?.name}</b>
            ),
          },
          {
            label: "Khoản thu",
            render: (f) => (
              <div>
                {f.title}
                <small className="block-muted">
                  {f.id} · {f.month}
                </small>
              </div>
            ),
          },
          { label: "Số tiền", render: (f) => <b>{money(f.amount)}</b> },
          {
            label: "Trạng thái",
            render: (f) => (
              <Badge>{f.paidAt ? "Đã thanh toán" : "Chưa thanh toán"}</Badge>
            ),
          },
          {
            label: "Ngày thanh toán",
            render: (f) => (f.paidAt ? dateLabel(f.paidAt) : "—"),
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
                      content: f.title + " · " + money(f.amount),
                      okText: "Xác nhận đã thu",
                      cancelText: "Hủy",
                      onOk: () =>
                        run(
                          (d) => mockService.pay(d, role, f.id),
                          "Đã ghi nhận thanh toán học phí",
                        ),
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
      {add && <RecordEditor kind="fees" onClose={() => setAdd(false)} />}
      <Modal
        open={!!receipt}
        title="Biên nhận thanh toán minh họa"
        onCancel={() => setReceipt(undefined)}
        footer={<Button onClick={() => setReceipt(undefined)}>Đóng</Button>}
      >
        {receipt && (
          <div className="receipt">
            <Receipt size={36} />
            <h2>{money(receipt.amount)}</h2>
            <Badge>Đã thanh toán</Badge>
            <p>{db.students.find((s) => s.id === receipt.studentId)?.name}</p>
            <p>{receipt.title}</p>
            <p>Ngày ghi nhận: {dateLabel(receipt.paidAt!)}</p>
            <small>Biên nhận demo · Không có giá trị giao dịch</small>
          </div>
        )}
      </Modal>
    </>
  );
}
