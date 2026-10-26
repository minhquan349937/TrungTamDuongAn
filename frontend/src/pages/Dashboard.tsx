import { useState } from "react";
import { Button, Select } from "antd";
import {
  Users,
  UserRoundCheck,
  GraduationCap,
  School,
  Plus,
  CalendarDays,
  ArrowRight,
  Clock,
  Wallet,
  CalendarCheck,
  ClipboardCheck,
} from "lucide-react";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from "recharts";
import { useNavigate } from "react-router-dom";
import { useDemo } from "../hooks/useDemo";
import { StatCard, SectionTitle, Badge } from "../components/ui";
import { StudentsTable, StaffTable } from "../features/PeopleTables";
import { RecordEditor } from "../features/RecordEditor";
import { today, dateLabel, money } from "../utils/format";
import { mockService } from "../services/mockService";
export function Charts() {
  const { view } = useDemo(),
    [range, setRange] = useState("6");
  const month = Number(today().slice(5, 7));
  const data = Array.from({ length: Number(range) }, (_, i) => {
    const m = ((month - Number(range) + i + 12) % 12) + 1;
    return {
      name: "Thg " + m,
      value: view.students.filter((s) => Number(s.joined.slice(5, 7)) === m)
        .length,
      previous: Math.max(0, 2 + ((i * 3) % 5)),
    };
  });
  const distribution = ["Cá nhân", "Lớp nhóm", "Bán trú"].map((name) => ({
    name,
    value: view.classes
      .filter((c) => c.type === name)
      .reduce((sum, c) => sum + c.studentIds.length, 0),
  }));
  const total = distribution.reduce((s, d) => s + d.value, 0),
    colors = ["#2563eb", "#93b4f8", "#b8d7cc"];
  return (
    <div className="charts-grid">
      <section className="panel chart-panel">
        <SectionTitle
          title="Học sinh nhập học"
          subtitle="Cùng nhìn lại hành trình phát triển"
          action={
            <Select
              aria-label="Khoảng thời gian biểu đồ"
              size="small"
              value={range}
              onChange={setRange}
              options={[
                { value: "6", label: "6 tháng gần đây" },
                { value: "3", label: "3 tháng gần đây" },
              ]}
            />
          }
        />
        <div className="chart-legend">
          <span>
            <i />
            Năm nay
          </span>
          <span>
            <i className="muted-dot" />
            Năm trước (minh họa)
          </span>
        </div>
        <div className="line-chart">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart
              data={data}
              margin={{ top: 12, right: 12, left: -25, bottom: 0 }}
            >
              <defs>
                <linearGradient id="blueFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#2563eb" stopOpacity={0.12} />
                  <stop offset="100%" stopColor="#2563eb" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid
                strokeDasharray="3 5"
                vertical={false}
                stroke="#edf0f5"
              />
              <XAxis
                dataKey="name"
                tickLine={false}
                axisLine={false}
                tick={{ fontSize: 11, fill: "#94a3b8" }}
                dy={6}
              />
              <YAxis
                allowDecimals={false}
                tickLine={false}
                axisLine={false}
                tick={{ fontSize: 11, fill: "#94a3b8" }}
              />
              <Tooltip
                contentStyle={{
                  borderRadius: 12,
                  border: "1px solid #e2e8f0",
                  fontSize: 12,
                }}
              />
              <Area
                isAnimationActive={false}
                name="Năm trước (minh họa)"
                type="monotone"
                dataKey="previous"
                stroke="#c3cfe3"
                strokeDasharray="4 4"
                fill="transparent"
                strokeWidth={2}
              />
              <Area
                isAnimationActive={false}
                name="Học sinh nhập học"
                type="monotone"
                dataKey="value"
                stroke="#2563eb"
                fill="url(#blueFill)"
                strokeWidth={2.5}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </section>
      <section className="panel chart-panel">
        <SectionTitle
          title="Phân bổ hình thức học"
          subtitle="Mỗi hành trình, một cách đồng hành"
        />
        <div className="donut-content">
          <div className="donut">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  isAnimationActive={false}
                  data={distribution}
                  innerRadius="67%"
                  outerRadius="90%"
                  paddingAngle={4}
                  dataKey="value"
                  stroke="none"
                >
                  {distribution.map((d, i) => (
                    <Cell key={d.name} fill={colors[i]} />
                  ))}
                </Pie>
                <Tooltip formatter={(v) => [v, "Lượt ghi danh"]} />
              </PieChart>
            </ResponsiveContainer>
            <div className="donut-center">
              <strong>{total}</strong>
              <span>Lượt ghi danh</span>
            </div>
          </div>
          <div className="donut-legend">
            {distribution.map((d, i) => (
              <div key={d.name}>
                <span>
                  <i style={{ background: colors[i] }} />
                  {d.name}
                </span>
                <b>{total ? Math.round((d.value / total) * 100) : 0}%</b>
              </div>
            ))}
          </div>
        </div>
        <p className="chart-footnote">
          Một học sinh có thể tham gia nhiều hình thức học.
        </p>
      </section>
    </div>
  );
}
export function Dashboard() {
  const { db, view, role, run } = useDemo(),
    nav = useNavigate(),
    [add, setAdd] = useState(false);
  const admin = role === "ADMIN",
    teacher = role === "TEACHER",
    day = today();
  const sessions = view.sessions.filter((s) => s.date === day);
  const unpaid = view.fees.filter((f) => !f.paidAt);
  const attendance = view.sessions.flatMap((s) => Object.values(s.attendance)),
    rate = attendance.length
      ? Math.round(
          (attendance.filter((v) => v === "Có mặt").length /
            attendance.length) *
            100,
        )
      : 0;
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">
            {admin
              ? "TỔNG QUAN TRUNG TÂM"
              : teacher
                ? "KHÔNG GIAN GIÁO VIÊN"
                : "KHÔNG GIAN PHỤ HUYNH"}
          </div>
          <h1>
            {admin
              ? "Một ngày mới, những bước tiến mới"
              : teacher
                ? "Chào cô Thu Hà, ngày mới tốt lành!"
                : "Cùng con lớn lên mỗi ngày"}
            <span className="greeting-sun">☀</span>
          </h1>
          <p>
            {admin
              ? "Chào Minh Anh! Cùng nhìn lại hoạt động của trung tâm hôm nay."
              : teacher
                ? "Lịch dạy, học sinh và công việc của cô, tất cả ở đây."
                : "Theo dõi lịch học và những hoạt động của con tại Dương An."}
          </p>
        </div>
        <div className="heading-actions">
          <span className="date-chip">
            <CalendarDays size={15} />
            {dateLabel(day)}
          </span>
          {admin && (
            <Button
              type="primary"
              icon={<Plus size={16} />}
              onClick={() => setAdd(true)}
            >
              Thêm học sinh
            </Button>
          )}
        </div>
      </div>
      <div className="stats-grid">
        {admin ? (
          <>
            <StatCard
              label="Tổng học sinh"
              value={db.students.length.toString().padStart(2, "0")}
              icon={<Users size={22} />}
              delta="+5 học sinh"
              caption="so với tháng trước*"
            />
            <StatCard
              label="Học sinh đang theo học"
              value={db.students.filter((s) => s.status === "Đang học").length}
              icon={<UserRoundCheck size={22} />}
              delta="90%"
              caption="tỷ lệ đang theo học*"
            />
            <StatCard
              label="Giáo viên & nhân viên"
              value={db.staff.length.toString().padStart(2, "0")}
              icon={<GraduationCap size={23} />}
              delta="+2 nhân viên"
              caption="so với tháng trước*"
            />
            <StatCard
              label="Lớp đang hoạt động"
              value={db.classes
                .filter((c) => c.status === "Đang hoạt động")
                .length.toString()
                .padStart(2, "0")}
              icon={<School size={22} />}
              delta="3 hình thức"
              caption="linh hoạt đồng hành"
            />
          </>
        ) : (
          <>
            <StatCard
              label={teacher ? "Học sinh phụ trách" : "Lớp con đang tham gia"}
              value={teacher ? view.students.length : view.classes.length}
              icon={<Users size={22} />}
              delta="Đã liên kết"
              caption="hồ sơ minh họa"
            />
            <StatCard
              label={teacher ? "Buổi dạy hôm nay" : "Buổi học hôm nay"}
              value={sessions.length}
              icon={<CalendarDays size={22} />}
              delta={dateLabel(day)}
              caption="lịch đã phân công"
            />
            <StatCard
              label="Tỷ lệ chuyên cần"
              value={rate + "%"}
              icon={<ClipboardCheck size={22} />}
              delta={attendance.length + " lượt"}
              caption="đã điểm danh"
            />
            <StatCard
              label={
                teacher ? "Ngày đã chấm công" : "Khoản thu chưa thanh toán"
              }
              value={teacher ? (db.checkins.NV001 ?? []).length : unpaid.length}
              icon={
                teacher ? <CalendarCheck size={22} /> : <Wallet size={22} />
              }
              delta={
                teacher
                  ? "Trong dữ liệu demo"
                  : money(unpaid.reduce((s, f) => s + f.amount, 0))
              }
              caption={teacher ? "Chấm công mỗi ngày" : "tổng còn lại"}
            />
          </>
        )}
      </div>
      {admin ? (
        <>
          <Charts />
          <StudentsTable compact />
          <StaffTable compact />
          <div className="dashboard-bottom">
            <button onClick={() => nav("/schedule")}>
              <span className="mini-icon">
                <CalendarDays size={19} />
              </span>
              <div>
                <b>{sessions.length} buổi học hôm nay</b>
                <small>Xem lịch và điều phối</small>
              </div>
              <ArrowRight size={17} />
            </button>
            <button onClick={() => nav("/fees")}>
              <span className="mini-icon amber-icon">
                <Wallet size={19} />
              </span>
              <div>
                <b>{unpaid.length} khoản học phí chưa thu</b>
                <small>Theo dõi thanh toán</small>
              </div>
              <ArrowRight size={17} />
            </button>
            <button onClick={() => nav("/leave")}>
              <span className="mini-icon">
                <Clock size={19} />
              </span>
              <div>
                <b>
                  {db.leaves.filter((l) => l.status === "Chờ duyệt").length} đơn
                  nghỉ phép chờ duyệt
                </b>
                <small>Quản lý công việc đội ngũ</small>
              </div>
              <ArrowRight size={17} />
            </button>
          </div>
          <p className="mock-footnote">
            * Chỉ báo biến động minh họa. Số liệu tổng cập nhật theo dữ liệu
            demo.
          </p>
        </>
      ) : (
        <>
          <section className="panel">
            <SectionTitle
              title={teacher ? "Lịch dạy hôm nay" : "Lịch học hôm nay"}
              action={
                <Button onClick={() => nav("/schedule")}>
                  Xem tất cả <ArrowRight size={15} />
                </Button>
              }
            />
            <div className="today-sessions">
              {sessions.length ? (
                sessions.map((s) => (
                  <div key={s.id}>
                    <span className="session-time">
                      {s.start}
                      <small>{s.end}</small>
                    </span>
                    <div>
                      <h3>
                        {view.classes.find((c) => c.id === s.classId)?.name}
                      </h3>
                      <p>
                        {s.room} ·{" "}
                        {db.staff.find((t) => t.id === s.teacherId)?.name}
                      </p>
                    </div>
                    <Badge>
                      {Object.keys(s.attendance).length
                        ? "Đã điểm danh"
                        : "Chưa điểm danh"}
                    </Badge>
                    {teacher && (
                      <Button onClick={() => nav("/attendance")}>
                        Điểm danh
                      </Button>
                    )}
                  </div>
                ))
              ) : (
                <p className="empty-inline">
                  Hôm nay chưa có lịch học. Xem thời khóa biểu để theo dõi các
                  buổi tiếp theo.
                </p>
              )}
            </div>
          </section>
          {teacher && (
            <div className="action-banner">
              <div>
                <h3>Bắt đầu một ngày đồng hành</h3>
                <p>Chấm công và quản lý lịch nghỉ của bạn.</p>
              </div>
              <Button
                type="primary"
                disabled={(db.checkins.NV001 ?? []).includes(day)}
                onClick={() =>
                  run(
                    (d) => mockService.checkin(d, role),
                    "Chấm công thành công",
                  )
                }
              >
                {(db.checkins.NV001 ?? []).includes(day)
                  ? "Đã chấm công hôm nay"
                  : "Chấm công hôm nay"}
              </Button>
              <Button onClick={() => nav("/leave")}>Đăng ký nghỉ phép</Button>
            </div>
          )}
          <StudentsTable />
          <section className="panel">
            <SectionTitle title="Bảng tin Dương An" />
            {view.notices.map((n) => (
              <div className="notice-row" key={n.id}>
                <span className="mini-icon">
                  <CalendarDays size={18} />
                </span>
                <div>
                  <h3>{n.title}</h3>
                  <p>{n.body}</p>
                  <small>{dateLabel(n.date)}</small>
                </div>
              </div>
            ))}
          </section>
        </>
      )}
      {add && <RecordEditor kind="students" onClose={() => setAdd(false)} />}
    </>
  );
}
