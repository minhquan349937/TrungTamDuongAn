import { PlanProgress } from "./PlanProgress";
import { useEffect, useRef, useState } from "react";
import { Alert, Button, Empty, Input, Spin } from "antd";
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Check,
  ClipboardCheck,
  Search,
  Sparkles,
  Target,
  Users,
  X,
} from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { useConfirm } from "../hooks/useConfirm";
import type { AssignedGoal, GoalSelection } from "../services/goalBankApi";
import {
  interventionApi,
  type DomainGroup,
  type PlanReview,
  type PlanView,
  type ProgramSummary,
  type StudentSummary,
  type Workspace,
} from "../services/interventionApi";
import {
  InterventionDomainPicker,
  selectionKey,
} from "./InterventionDomainPicker";
import "./intervention.css";
import "./real-intervention.css";
import "./intervention-workflow.css";

const root = "/individual/plan";
const today = () =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
const ageText = (months: number) =>
  `${Math.floor(months / 12)} tuổi${months % 12 ? ` ${months % 12} tháng` : ""}`;
const statusText: Record<string, string> = {
  ACTIVE: "Đang thực hiện",
  DRAFT: "Bản nháp",
  COMPLETED: "Đã hoàn thành",
  PAUSED: "Tạm dừng",
  CANCELLED: "Đã hủy",
};
const tabs = [
  { id: "current", name: "Kế hoạch hiện tại" },
  { id: "history", name: "Lịch sử kế hoạch" },
  { id: "bank", name: "Ngân hàng mục tiêu" },
  { id: "achieved", name: "Mục tiêu đã hoàn thành" },
  { id: "paused", name: "Mục tiêu đã dừng" },
];

function fromAssigned(row: AssignedGoal): GoalSelection {
  const s = row.goalSnapshot;
  return {
    goalId: row.goalId,
    specificGoalId: row.specificGoalId,
    targetType: row.targetType,
    code: s.code,
    name: s.goal_name ?? s.name,
    description: s.description,
    ageMinMonths: s.age_min_months ?? 0,
    ageMaxMonths: s.age_max_months ?? 0,
    location: {
      programId: s.program_id ?? "",
      programName: s.program_name ?? "Chương trình đã lưu",
      domainId: s.domain_id ?? "",
      domainName: s.domain_name ?? "Mục tiêu đã lưu",
      levelId: s.program_level_id ?? "",
      levelName: s.level_number ? `Cấp độ ${s.level_number}` : "",
      ageMinMonths: s.age_min_months ?? 0,
      ageMaxMonths: s.age_max_months ?? 0,
    },
  };
}
function uniqueSelections(rows: GoalSelection[]) {
  const seen = new Set<string>();
  return rows.filter((row) => {
    const key = selectionKey(row);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
export function SelectionOutline({
  rows,
  remove,
}: {
  rows: GoalSelection[];
  remove?: (row: GoalSelection) => void;
}) {
  const groups = [
    ...new Set(
      rows.map((row) => `${row.location.programId}:${row.location.domainId}`),
    ),
  ];
  return (
    <ol className="ip-outline">
      {groups.map((group, index) => {
        const items = rows.filter(
          (row) =>
            `${row.location.programId}:${row.location.domainId}` === group,
        );
        const parents = [...new Set(items.map((row) => row.goalId))];
        return (
          <li key={group} className="panel ip-outline-domain">
            <h2>
              {index + 1}. {items[0].location.domainName}{" "}
              <span>({items.length} mục tiêu)</span>
            </h2>
            <ol>
              {parents.map((parentId) => {
                const children = items.filter((row) => row.goalId === parentId);
                return (
                  <li key={parentId}>
                    <small>{children[0].location.programName} · {children[0].location.levelName}</small>
                    <h3>
                      {children[0].specificGoalId
                        ? children[0].name
                        : `${children[0].code} · ${children[0].name}`}
                    </h3>
                    <ul>
                      {children.map((row) => (
                        <li
                          key={selectionKey(row)}
                          className="ip-outline-target"
                        >
                          <div>
                            {row.specificGoalId && <b>{row.code}</b>}
                            {row.description && <p>{row.description}</p>}
                            <small className="ip-age">
                              {row.ageMinMonths}–{row.ageMaxMonths} tháng
                            </small>
                          </div>
                          {remove && (
                            <Button
                              type="text"
                              aria-label={`Bỏ ${row.code}`}
                              icon={<X size={16} />}
                              onClick={() => remove(row)}
                            />
                          )}
                        </li>
                      ))}
                    </ul>
                  </li>
                );
              })}
            </ol>
          </li>
        );
      })}
    </ol>
  );
}
function Plans({ rows, onSaved }: { rows: PlanView[]; onSaved: () => void }) {
  if (!rows.length)
    return <Empty description="Chưa có kế hoạch trong mục này" />;
  return (
    <div className="ip-plans">
      {rows.map(({ plan, goals }) => (
        <section key={plan.id} className="ip-plan">
          <div className="ip-plan-heading">
            <div>
              <h2>{plan.name}</h2>
              <p>
                Bắt đầu{" "}
                {new Date(`${plan.startDate}T00:00:00`).toLocaleDateString(
                  "vi-VN",
                )}{" "}
                · {goals.length} mục tiêu
              </p>
            </div>
            <span className={`ip-status ${plan.status.toLowerCase()}`}>
              {statusText[plan.status] ?? plan.status}
            </span>
          </div>
          <PlanProgress plan={plan} goals={goals} onSaved={onSaved} />
          {goals.length ? (
            <SelectionOutline rows={goals.map(fromAssigned)} />
          ) : (
            <Empty description="Kế hoạch chưa có mục tiêu" />
          )}
        </section>
      ))}
    </div>
  );
}

export function RealInterventionPlans() {
  const { user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const modal = useConfirm();
  const parts = location.pathname.slice(root.length).split("/").filter(Boolean);
  const studentId = parts[0] ?? "";
  const step = parts[1] === "new" ? (parts[2] ?? "program") : "workspace";
  const tab = new URLSearchParams(location.search).get("tab") ?? "current";
  const base = `${root}/${studentId}`;
  const [students, setStudents] = useState<StudentSummary[]>([]);
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [programs, setPrograms] = useState<ProgramSummary[]>([]);
  const [program, setProgram] = useState<ProgramSummary | null>(null);
  const [domains, setDomains] = useState<DomainGroup[]>([]);
  const [ages, setAges] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<GoalSelection[]>([]);
  const [requestId, setRequestId] = useState("");
  const [name, setName] = useState("");
  const [startDate, setStartDate] = useState(today);
  const [review, setReview] = useState<PlanReview | null>(null);
  const [search, setSearch] = useState("");
  const [pending, setPending] = useState(0);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [refresh, setRefresh] = useState(0);
  const busy = pending > 0;
  const context = useRef("");
  context.current = `${studentId}:${step}`;
  async function run(action: () => Promise<void>) {
    setPending((n) => n + 1);
    setError("");
    try {
      await action();
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setPending((n) => n - 1);
    }
  }
  useEffect(() => {
    if (user?.role !== "ADMIN" || studentId) return;
    let active = true;
    setPending((n) => n + 1);
    void interventionApi
      .students()
      .then((rows) => {
        if (active) setStudents(rows);
      })
      .catch((cause: Error) => {
        if (active) setError(cause.message);
      })
      .finally(() => setPending((n) => n - 1));
    return () => {
      active = false;
    };
  }, [user?.role, studentId, refresh]);
  useEffect(() => {
    setProgram(null);
    setDomains([]);
    setAges({});
    setSelected([]);
    setReview(null);
    setError("");
    setNotice("");
  }, [studentId]);
  useEffect(() => {
    // Clear the draft only after navigation has reached the workspace, so the
    // missing-review guard cannot redirect a successful finish back to selection.
    if (step === "workspace") {
      setSelected([]);
      setReview(null);
      setProgram(null);
      setAges({});
    }
  }, [step]);
  useEffect(() => {
    if (!studentId || user?.role !== "ADMIN") return;
    let active = true;
    setWorkspace(null);
    setPending((n) => n + 1);
    void interventionApi
      .workspace(studentId)
      .then((row) => {
        if (active) setWorkspace(row);
      })
      .catch((cause: Error) => {
        if (active) setError(cause.message);
      })
      .finally(() => setPending((n) => n - 1));
    return () => {
      active = false;
    };
  }, [studentId, refresh, user?.role]);
  useEffect(() => {
    if (!studentId || step !== "program" || user?.role !== "ADMIN") return;
    let active = true;
    setPending((n) => n + 1);
    void interventionApi
      .programs()
      .then((rows) => {
        if (active) setPrograms(rows);
      })
      .catch((cause: Error) => {
        if (active) setError(cause.message);
      })
      .finally(() => setPending((n) => n - 1));
    return () => {
      active = false;
    };
  }, [studentId, step, user?.role]);
  useEffect(() => {
    if (!program) return;
    let active = true;
    setDomains([]);
    setPending((n) => n + 1);
    void interventionApi
      .domains(program.id)
      .then((rows) => {
        if (active) setDomains(rows);
      })
      .catch((cause: Error) => {
        if (active) setError(cause.message);
      })
      .finally(() => setPending((n) => n - 1));
    return () => {
      active = false;
    };
  }, [program]);
  useEffect(() => {
    if (studentId && (step === "targets" || step === "review") && !program)
      navigate(`${base}/new/program`, { replace: true });
    else if (studentId && step === "review" && !review)
      navigate(`${base}/new/targets`, { replace: true });
  }, [studentId, step, program, review, base, navigate]);
  function chooseProgram(row: ProgramSummary) {
    const choose = () => {
      if (program?.id !== row.id) {
        setSelected([]);
        setAges({});
        setReview(null);
        setRequestId(crypto.randomUUID());
        setName(`Kế hoạch ${row.name}`);
        setStartDate(today());
        setProgram(row);
      }
    };
    if (program?.id !== row.id && selected.length)
      modal.confirm({
        title: "Đổi chương trình?",
        content: "Các lựa chọn trong lượt này sẽ được bỏ.",
        onOk: choose,
      });
    else choose();
  }
  function toggle(row: GoalSelection) {
    setSelected((current) =>
      current.some((item) => selectionKey(item) === selectionKey(row))
        ? current.filter((item) => selectionKey(item) !== selectionKey(row))
        : [...current, row],
    );
    setReview(null);
  }
  function exit() {
    const leave = () => {
      setError("");
      navigate(`${base}?tab=bank`);
    };
    if (!selected.length) leave();
    else
      modal.confirm({
        title: "Thoát khi chưa hoàn thành?",
        content: "Các mục tiêu đang chọn chưa được lưu vào kế hoạch.",
        okText: "Thoát",
        cancelText: "Tiếp tục chọn",
        onOk: leave,
      });
  }
  if (user?.role !== "ADMIN")
    return (
      <Alert
        type="warning"
        showIcon
        message="Phần tạo kế hoạch hiện dành cho quản trị viên."
      />
    );
  const student = workspace?.student;
  const activePlans =
    workspace?.plans.filter(({ plan }) =>
      ["ACTIVE", "DRAFT"].includes(plan.status),
    ) ?? [];
  const allAssigned =
    workspace?.plans
      .filter(({ plan }) => plan.status !== "CANCELLED")
      .flatMap(({ goals }) => goals) ?? [];
  const bankItems = uniqueSelections([
    ...allAssigned.map(fromAssigned),
    ...(workspace?.bank.map((row) => row.goalSnapshot) ?? []),
  ]);
  const filtered = students.filter((row) =>
    `${row.fullName} ${row.code}`
      .toLocaleLowerCase("vi")
      .includes(search.toLocaleLowerCase("vi")),
  );
  return (
    <div className="intervention-page ip-page">
      {error && (
        <Alert
          type="error"
          showIcon
          message={error}
          closable
          onClose={() => setError("")}
        />
      )}
      {notice && (
        <Alert
          type="success"
          showIcon
          message={notice}
          closable
          onClose={() => setNotice("")}
        />
      )}
      {busy && (
        <div className="ip-loading">
          <Spin /> Đang tải dữ liệu…
        </div>
      )}
      {!studentId ? (
        <>
          <div className="page-heading">
            <div>
              <div className="eyebrow">QUẢN LÝ LỚP CÁ NHÂN</div>
              <h1>Kế hoạch can thiệp</h1>
              <p>Đồng hành cùng từng học sinh, theo dõi từng mục tiêu.</p>
            </div>
            <Input
              aria-label="Tìm học sinh"
              prefix={<Search size={18} />}
              placeholder="Tìm học sinh theo tên hoặc mã"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <div className="ip-metrics">
            {[
              { label: "Học sinh", value: students.length, icon: <Users /> },
              {
                label: "Mục tiêu đang học",
                value: students.reduce((n, row) => n + row.studyingGoals, 0),
                icon: <Target />,
              },
              {
                label: "Mục tiêu đã hoàn thành",
                value: students.reduce((n, row) => n + row.achievedGoals, 0),
                icon: <ClipboardCheck />,
              },
            ].map((item) => (
              <div className="panel" key={item.label}>
                {item.icon}
                <div>
                  <span>{item.label}</span>
                  <b>{item.value}</b>
                </div>
              </div>
            ))}
          </div>
          <section className="panel ip-table-wrap">
            <table className="ip-table">
              <thead>
                <tr>
                  <th>STT</th>
                  <th>HỌC SINH</th>
                  <th>TUỔI</th>
                  <th>MT ĐANG HỌC</th>
                  <th>MT HOÀN THÀNH</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {filtered.map((row, index) => (
                  <tr
                    key={row.id}
                    onClick={() => navigate(`${root}/${row.id}`)}
                  >
                    <td>{index + 1}</td>
                    <td>
                      <button
                        className="ip-student-link"
                        onClick={() => navigate(`${root}/${row.id}`)}
                      >
                        <span className="ip-avatar">
                          {row.fullName
                            .trim()
                            .split(/\s+/)
                            .slice(-2)
                            .map((word) => word[0])
                            .join("")}
                        </span>
                        <span>
                          <b>{row.fullName}</b>
                          <small>{row.code}</small>
                        </span>
                      </button>
                    </td>
                    <td>{ageText(row.ageMonths)}</td>
                    <td>
                      <span className="ip-count">
                        {row.studyingGoals} mục tiêu
                      </span>
                    </td>
                    <td>{row.achievedGoals} mục tiêu</td>
                    <td>
                      <ArrowRight size={18} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!filtered.length && !busy && (
              <Empty description={students.length ? "Không có học sinh phù hợp" : "Trung tâm chưa có hồ sơ học sinh để lập kế hoạch"} />
            )}
          </section>
        </>
      ) : (
        <>
          <div className="ip-breadcrumb">
            <Button
              type="text"
              icon={<ArrowLeft size={16} />}
              onClick={() => (step === "workspace" ? navigate(root) : exit())}
            >
              {step === "workspace" ? "Danh sách học sinh" : "Thoát"}
            </Button>
            <span>
              Kế hoạch can thiệp /{" "}
              {step === "workspace" ? "Hồ sơ học sinh" : "Ngân hàng mục tiêu"}
            </span>
          </div>
          {student && (
            <div className="ip-student-heading">
              <span className="ip-avatar">
                {student.fullName
                  .trim()
                  .split(/\s+/)
                  .slice(-2)
                  .map((word) => word[0])
                  .join("")}
              </span>
              <div>
                <h1>{student.fullName}</h1>
                <p>
                  {ageText(student.ageMonths)} · {student.code}
                </p>
              </div>
            </div>
          )}
          {workspace && step === "workspace" && (
            <>
              <nav className="ip-tabs" aria-label="Kế hoạch của học sinh">
                {tabs.map((item) => (
                  <button
                    key={item.id}
                    className={tab === item.id ? "active" : ""}
                    onClick={() => navigate(`${base}?tab=${item.id}`)}
                  >
                    {item.name}
                  </button>
                ))}
              </nav>
              {tab === "current" && (
                <>
                  <div className="ip-section-heading">
                    <h2>Kế hoạch hiện tại</h2>
                    <Button
                      onClick={() => navigate(`${base}?tab=bank`)}
                      icon={<BookOpen size={16} />}
                    >
                      Ngân hàng mục tiêu
                    </Button>
                  </div>
                  <Plans rows={activePlans} onSaved={() => setRefresh(n => n + 1)} />
                </>
              )}
              {tab === "history" && (
                <>
                  <h2>Lịch sử kế hoạch</h2>
                  <Plans rows={workspace.plans} onSaved={() => setRefresh(n => n + 1)} />
                </>
              )}
              {tab === "bank" && (
                <>
                  <div className="ip-section-heading">
                    <div>
                      <h2>Ngân hàng mục tiêu</h2>
                      <p>
                        Mục tiêu đã lưu và các mục tiêu đã chọn trong kế hoạch
                        của học sinh.
                      </p>
                    </div>
                    <Button
                      type="primary"
                      icon={<Sparkles size={17} />}
                      onClick={() => navigate(`${base}/new/program`)}
                    >
                      Gợi ý mục tiêu
                    </Button>
                  </div>
                  {bankItems.length ? (
                    <SelectionOutline rows={bankItems} />
                  ) : (
                    <section className="panel ip-bank-empty">
                      <span className="ip-empty-symbol">
                        <Target size={48} />
                      </span>
                      <h2>Sẵn sàng cho những bước tiến mới</h2>
                      <p>
                        Chọn mục tiêu từ thư viện chương trình để xây dựng kế
                        hoạch phù hợp cho học sinh.
                      </p>
                    </section>
                  )}
                </>
              )}
              {["achieved", "paused"].includes(tab) && (
                <>
                  <h2>
                    {tab === "achieved"
                      ? "Mục tiêu đã hoàn thành"
                      : "Mục tiêu đã dừng"}
                  </h2>
                  {allAssigned.some(
                    (row) =>
                      row.status ===
                      (tab === "achieved" ? "ACHIEVED" : "PAUSED"),
                  ) ? (
                    <SelectionOutline
                      rows={uniqueSelections(
                        allAssigned
                          .filter(
                            (row) =>
                              row.status ===
                              (tab === "achieved" ? "ACHIEVED" : "PAUSED"),
                          )
                          .map(fromAssigned),
                      )}
                    />
                  ) : (
                    <Empty description="Chưa có mục tiêu trong mục này" />
                  )}
                </>
              )}
            </>
          )}
          {workspace && step !== "workspace" && (
            <>
              <ol className="ip-steps" aria-label="Tiến trình lập kế hoạch">
                {[
                  "Thư viện mục tiêu",
                  "Lĩnh vực và độ tuổi",
                  "Kiểm tra và hoàn tất",
                ].map((label, index) => (
                  <li
                    key={label}
                    className={
                      index === ["program", "targets", "review"].indexOf(step)
                        ? "active"
                        : ""
                    }
                  >
                    <span>{index + 1}</span>
                    {label}
                  </li>
                ))}
              </ol>
              {step === "program" && (
                <>
                  <div className="ip-section-heading">
                    <div>
                      <h2>Bước 1: Chọn chương trình</h2>
                      <p>
                        Thông tin được tổng hợp từ thư viện mục tiêu hiện có.
                      </p>
                    </div>
                  </div>
                  <section className="panel ip-table-wrap">
                    <table className="ip-table">
                      <thead>
                        <tr>
                          <th />
                          <th>STT</th>
                          <th>CHƯƠNG TRÌNH</th>
                          <th>LĨNH VỰC</th>
                          <th>MỤC TIÊU CHÍNH</th>
                          <th>THÁNG TUỔI</th>
                        </tr>
                      </thead>
                      <tbody>
                        {programs.map((row, index) => (
                          <tr
                            key={row.id}
                            className={program?.id === row.id ? "selected" : ""}
                            onClick={() => chooseProgram(row)}
                          >
                            <td>
                              <input
                                type="radio"
                                name="program"
                                aria-label={row.name}
                                checked={program?.id === row.id}
                                onChange={() => chooseProgram(row)}
                                onClick={(e) => e.stopPropagation()}
                              />
                            </td>
                            <td>{index + 1}</td>
                            <td>
                              <b>{row.name}</b>
                              <small>{row.code}</small>
                            </td>
                            <td>{row.domainCount}</td>
                            <td>{row.goalCount}</td>
                            <td>
                              {row.ageMinMonths !== null
                                ? `${row.ageMinMonths}–${row.ageMaxMonths} tháng`
                                : "Chưa có cấp độ"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    {!programs.length && !busy && (
                      <Empty description="Chưa có chương trình trong thư viện" />
                    )}
                  </section>
                  <div className="ip-footer">
                    <span>Chọn một chương trình để bắt đầu.</span>
                    <Button
                      type="primary"
                      disabled={!program || busy}
                      icon={<ArrowRight size={16} />}
                      onClick={() => navigate(`${base}/new/targets`)}
                    >
                      Tiếp tục: Lĩnh vực can thiệp
                    </Button>
                  </div>
                </>
              )}
              {step === "targets" && program && (
                <>
                  <div className="ip-section-heading">
                    <div>
                      <h2>Bước 2: Lĩnh vực can thiệp</h2>
                      <p>
                        {program.name} · Chọn độ tuổi riêng cho từng lĩnh vực,
                        sau đó tích chọn nhiều mục tiêu.
                      </p>
                    </div>
                    <Button onClick={() => navigate(`${base}/new/program`)}>
                      Đổi chương trình
                    </Button>
                  </div>
                  <section className="panel ip-plan-fields">
                    <label>
                      Tên kế hoạch
                      <Input
                        aria-label="Tên kế hoạch"
                        maxLength={200}
                        value={name}
                        onChange={(e) => {
                          setName(e.target.value);
                          setReview(null);
                        }}
                      />
                    </label>
                    <label>
                      Ngày bắt đầu
                      <input
                        aria-label="Ngày bắt đầu"
                        type="date"
                        value={startDate}
                        onChange={(e) => {
                          setStartDate(e.target.value);
                          setReview(null);
                        }}
                      />
                    </label>
                  </section>
                  {domains.map((group, index) => (
                    <InterventionDomainPicker
                      key={group.id}
                      group={group}
                      index={index}
                      program={program}
                      selected={selected}
                      toggle={toggle}
                      domainId={ages[group.id] ?? ""}
                      onAgeChange={(id) => {
                        setAges((current) => ({ ...current, [group.id]: id }));
                        setSelected((current) => current.filter((row) => !group.ages.some((age) => age.domainId === row.location.domainId)));
                        setReview(null);
                      }}
                    />
                  ))}
                  {!domains.length && !busy && (
                    <Empty description="Chương trình chưa có lĩnh vực" />
                  )}
                  <div className="ip-footer ip-sticky">
                    <div>
                      <b>{selected.length} mục tiêu đã chọn</b>
                      <p>Lựa chọn được giữ khi đổi lĩnh vực và độ tuổi.</p>
                    </div>
                    <Button
                      type="primary"
                      loading={busy}
                      disabled={!selected.length || !name.trim() || !startDate}
                      icon={<ArrowRight size={16} />}
                      onClick={() =>
                        void run(async () => {
                          const expected = context.current;
                          const result = await interventionApi.preview(
                            studentId,
                            {
                              requestId,
                              programId: program.id,
                              name,
                              startDate,
                              selections: selected.map(
                                ({ goalId, specificGoalId }) => ({
                                  goalId,
                                  specificGoalId,
                                }),
                              ),
                            },
                          );
                          if (context.current === expected) {
                            setReview(result);
                            navigate(`${base}/new/review`);
                          }
                        })
                      }
                    >
                      Kiểm tra và hoàn tất
                    </Button>
                  </div>
                </>
              )}
              {step === "review" && review && (
                <>
                  <div className="ip-section-heading">
                    <div>
                      <h2>Bước 3: Kiểm tra và hoàn tất</h2>
                      <p>
                        {review.name} · Bắt đầu {review.startDate} ·{" "}
                        {review.total} mục tiêu
                      </p>
                    </div>
                    <Button
                      onClick={() => navigate(`${base}/new/targets`)}
                      icon={<ArrowLeft size={16} />}
                    >
                      Quay lại lựa chọn
                    </Button>
                  </div>
                  <SelectionOutline
                    rows={review.items}
                    remove={(row) => {
                      toggle(row);
                      navigate(`${base}/new/targets`);
                    }}
                  />
                  <div className="ip-footer ip-sticky">
                    <span>Kiểm tra nội dung trước khi lưu kế hoạch.</span>
                    <Button
                      type="primary"
                      loading={busy}
                      icon={<Check size={17} />}
                      onClick={() =>
                        void run(async () => {
                          const expected = context.current;
                          const saved = await interventionApi.complete(
                            studentId,
                            review,
                          );
                          if (context.current === expected) {
                            setRefresh((n) => n + 1);
                            setNotice(
                              `Đã lưu ${saved.result.plan.name} với ${saved.result.goals.length} mục tiêu.`,
                            );
                            navigate(`${base}?tab=current`);
                          }
                        })
                      }
                    >
                      Hoàn thành
                    </Button>
                  </div>
                </>
              )}
            </>
          )}
        </>
      )}
    </div>
  );
}
