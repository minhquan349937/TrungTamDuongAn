import { useState } from "react";
import {
  Link,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import {
  Button,
  Checkbox,
  Empty,
  Input,
  InputNumber,
  Result,
  Tabs,
} from "antd";
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  Layers3,
  Library,
  Search,
  Sparkles,
  Target,
  Users,
  X,
} from "lucide-react";
import { Avatar, Badge, DataTable } from "../components/ui";
import { useDemo } from "../hooks/useDemo";
import { useConfirm } from "../hooks/useConfirm";
import {
  goalAges,
  goalCatalog,
  goalDomains,
  goalPrograms,
} from "../mocks/goalLibrary";
import { addStudentGoals } from "../services/interventionService";
import { age, normalize } from "../utils/format";
import type { InterventionGoal, Student } from "../types";
import "./intervention.css";

const basePath = "/individual/plan";

function GoalGroups({
  goals,
  onRemove,
}: {
  goals: InterventionGoal[];
  onRemove?: (id: string) => void;
}) {
  const groups = [...new Set(goals.map((g) => `${g.programId}:${g.domainId}`))];
  return (
    <div className="goal-groups">
      {groups.map((key) => {
        const rows = goals.filter(
          (g) => `${g.programId}:${g.domainId}` === key,
        );
        return (
          <details className="goal-review-group" key={key} open>
            <summary>
              <span className="goal-domain-symbol">
                <Layers3 size={18} />
              </span>
              <span>
                {rows[0].domainName}
                <small>{rows[0].programName}</small>
              </span>
              <span className="goal-count">{rows.length} mục tiêu</span>
              <ChevronDown size={16} />
            </summary>
            <div className="goal-review-rows">
              {rows.map((goal, i) => (
                <div className="goal-review-row" key={goal.id}>
                  <span className="goal-row-number">
                    {String(i + 1).padStart(2, "0")}
                  </span>
                  <div className="goal-row-content">
                    <div className="goal-meta">
                      <span>{goal.code}</span>
                      <span className="goal-age-tag">{goal.ageLabel}</span>
                    </div>
                    <p>{goal.description}</p>
                    <small>{goal.group}</small>
                  </div>
                  {onRemove ? (
                    <button
                      type="button"
                      className="goal-remove"
                      aria-label={`Bỏ mục tiêu ${goal.code}`}
                      onClick={() => onRemove(goal.id)}
                    >
                      <X size={17} />
                    </button>
                  ) : (
                    <CheckCircle2 className="goal-saved-icon" size={18} />
                  )}
                </div>
              ))}
            </div>
          </details>
        );
      })}
    </div>
  );
}

function GoalWizard({
  student,
  onExit,
  onSaved,
}: {
  student: Student;
  onExit: () => void;
  onSaved: (count: number) => void;
}) {
  const { db, role, run } = useDemo();
  const modal = useConfirm();
  const [step, setStep] = useState(0);
  const [programId, setProgramId] = useState("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [ages, setAges] = useState<Record<string, string>>({});
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [unlimited, setUnlimited] = useState(true);
  const [limit, setLimit] = useState(15);
  const selected = goalCatalog.filter((g) => selectedIds.includes(g.id));
  const program = goalPrograms.find((p) => p.id === programId);
  const domains = goalDomains.filter((d) => program?.domainIds.includes(d.id));
  const existing = new Set(
    db.studentGoals
      .filter((g) => g.studentId === student.id)
      .map((g) => g.goal.id),
  );
  const atLimit = !unlimited && selected.length >= limit;
  const overLimit = !unlimited && selected.length > limit;
  function remove(id: string) {
    setSelectedIds((ids) => ids.filter((value) => value !== id));
  }
  function chooseProgram(id: string) {
    if (id === programId) return;
    const change = () => {
      setProgramId(id);
      setSelectedIds([]);
      setAges({});
      setExpanded({});
    };
    if (selected.length)
      modal.confirm({
        title: "Đổi thư viện mục tiêu?",
        content:
          "Các mục tiêu đang chọn sẽ được bỏ để bạn chọn lại trong thư viện mới. Ngân hàng đã lưu của học sinh vẫn được giữ.",
        okText: "Đổi thư viện",
        cancelText: "Tiếp tục chọn",
        onOk: change,
      });
    else change();
  }
  function exit() {
    if (!selected.length) return onExit();
    modal.confirm({
      title: "Thoát khi chưa lưu mục tiêu?",
      content:
        "Các lựa chọn trong lần này chưa được thêm vào ngân hàng của học sinh.",
      okText: "Thoát",
      cancelText: "Tiếp tục chọn",
      onOk: onExit,
    });
  }
  function finish() {
    if (!selected.length || overLimit) return;
    const newCount = selected.filter((g) => !existing.has(g.id)).length;
    if (
      run(
        (d) => addStudentGoals(d, role, student.id, selectedIds),
        `Đã thêm ${newCount} mục tiêu vào ngân hàng`,
      )
    )
      onSaved(newCount);
  }
  function moveStep(next: number) {
    setStep(next);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }
  return (
    <div className="goal-wizard">
      <div className="goal-wizard-heading">
        <div>
          <span className="eyebrow">THIẾT LẬP NGÂN HÀNG MỤC TIÊU</span>
          <h2>
            {
              [
                "Chọn thư viện phù hợp",
                "Chọn mục tiêu theo lĩnh vực",
                "Kiểm tra và hoàn tất",
              ][step]
            }
          </h2>
          <p>
            {
              [
                "Bắt đầu từ một chương trình để khám phá các mục tiêu cho học sinh.",
                "Chọn độ tuổi của từng lĩnh vực, sau đó đánh dấu mục tiêu muốn triển khai.",
                "Xem lại những mục tiêu sẽ được thêm vào ngân hàng của học sinh.",
              ][step]
            }
          </p>
        </div>
        <Button icon={<X size={15} />} onClick={exit}>
          Thoát
        </Button>
      </div>
      <ol className="goal-steps" aria-label="Tiến trình chọn mục tiêu">
        {[
          "Thư viện mục tiêu",
          "Lĩnh vực & mục tiêu",
          "Kiểm tra & hoàn tất",
        ].map((label, index) => (
          <li
            key={label}
            className={index === step ? "current" : index < step ? "done" : ""}
            aria-current={step === index ? "step" : undefined}
          >
            <span>{index < step ? <Check size={15} /> : `0${index + 1}`}</span>
            <div>
              <small>BƯỚC {index + 1}</small>
              <b>{label}</b>
            </div>
          </li>
        ))}
      </ol>
      {step === 0 && (
        <section className="panel goal-library-panel">
          <div className="goal-section-heading">
            <div>
              <h3>Thư viện mục tiêu</h3>
              <p>Chọn một chương trình để tiếp tục.</p>
            </div>
            <span className="goal-pill">
              <Library size={14} /> {goalPrograms.length} thư viện
            </span>
          </div>
          <div className="goal-library-labels" aria-hidden="true">
            <span>CHƯƠNG TRÌNH</span>
            <span>LĨNH VỰC</span>
            <span>MỤC TIÊU</span>
            <span>ĐỘ TUỔI MẪU</span>
          </div>
          <div
            role="radiogroup"
            aria-label="Chọn thư viện mục tiêu"
            className="goal-library-list"
          >
            {goalPrograms.map((p) => (
              <label
                key={p.id}
                className={`goal-library-option ${p.id === programId ? "chosen" : ""}`}
              >
                <div className="goal-library-main">
                  <input
                    type="radio"
                    name="goal-program"
                    value={p.id}
                    checked={programId === p.id}
                    onChange={() => chooseProgram(p.id)}
                  />
                  <span className={`goal-library-icon ${p.color}`}>
                    <BookOpen size={23} />
                  </span>
                  <div>
                    <b>{p.name}</b>
                    <p>{p.description}</p>
                  </div>
                </div>
                <span className="goal-library-stat">
                  <strong>{p.domainIds.length}</strong>
                  <small>lĩnh vực</small>
                </span>
                <span className="goal-library-stat">
                  <strong>
                    {goalCatalog.filter((g) => g.programId === p.id).length}
                  </strong>
                  <small>mục tiêu</small>
                </span>
                <span className="goal-library-age">9–48 tháng</span>
              </label>
            ))}
          </div>
          <div className="goal-limit-settings">
            <div>
              <h3>Số mục tiêu muốn chọn</h3>
              <p>Bạn chủ động chọn từng mục tiêu ở bước tiếp theo.</p>
            </div>
            <div>
              <Checkbox
                checked={unlimited}
                onChange={(e) => setUnlimited(e.target.checked)}
              >
                Không giới hạn số mục tiêu
              </Checkbox>
              {!unlimited && (
                <InputNumber
                  aria-label="Giới hạn số mục tiêu"
                  min={1}
                  max={200}
                  precision={0}
                  value={limit}
                  onChange={(n) => setLimit(n ?? 1)}
                />
              )}
            </div>
          </div>
          <p className="goal-demo-note">
            Thư viện dùng nội dung minh họa để trải nghiệm giao diện, chưa phải
            bộ mục tiêu chuẩn đầy đủ của từng chương trình.
          </p>
        </section>
      )}
      {step === 1 && (
        <div className="goal-selection-layout">
          <section className="goal-domains" aria-label="Lĩnh vực can thiệp">
            <div className="goal-section-heading">
              <div>
                <h3>{program?.name}</h3>
                <p>
                  Đổi độ tuổi chỉ lọc danh sách; các mục tiêu đã chọn vẫn được
                  giữ.
                </p>
              </div>
              <span className="goal-pill">{domains.length} lĩnh vực</span>
            </div>
            {domains.map((domain, index) => {
              const ageId = ages[domain.id];
              const goals = goalCatalog.filter(
                (g) =>
                  g.programId === programId &&
                  g.domainId === domain.id &&
                  g.ageId === ageId,
              );
              const domainSelected = selected.filter(
                (g) => g.domainId === domain.id,
              ).length;
              return (
                <section
                  className={`goal-domain-card ${ageId ? "has-age" : ""}`}
                  key={domain.id}
                  aria-label={domain.name}
                >
                  <div className="goal-domain-header">
                    <button
                      className="goal-domain-toggle"
                      aria-expanded={!!expanded[domain.id]}
                      aria-controls={`domain-${domain.id}`}
                      disabled={!ageId}
                      onClick={() =>
                        setExpanded({
                          ...expanded,
                          [domain.id]: !expanded[domain.id],
                        })
                      }
                    >
                      <span className="goal-domain-number">
                        {String(index + 1).padStart(2, "0")}
                      </span>
                      <span>
                        <b>{domain.name}</b>
                        <small>
                          {domainSelected
                            ? `${domainSelected} mục tiêu đã chọn`
                            : domain.description}
                        </small>
                      </span>
                      {ageId && (
                        <ChevronDown
                          size={15}
                          className={expanded[domain.id] ? "is-open" : ""}
                        />
                      )}
                    </button>
                    <label className="goal-age-select">
                      <span>Độ tuổi</span>
                      <select
                        aria-label={`Độ tuổi ${domain.name}`}
                        value={ageId ?? ""}
                        onChange={(e) => {
                          setAges({ ...ages, [domain.id]: e.target.value });
                          setExpanded({
                            ...expanded,
                            [domain.id]: !!e.target.value,
                          });
                        }}
                      >
                        <option value="">Chọn độ tuổi</option>
                        {goalAges.map((a) => (
                          <option key={a.id} value={a.id}>
                            {a.label}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>
                  {ageId && expanded[domain.id] && (
                    <div
                      className="goal-domain-content"
                      id={`domain-${domain.id}`}
                    >
                      <div className="goal-domain-content-title">
                        <span>{domain.group}</span>
                        <small>{goals.length} mục tiêu</small>
                      </div>
                      {goals.map((goal) => {
                        const checked = selectedIds.includes(goal.id),
                          saved = existing.has(goal.id);
                        return (
                          <label
                            className={`goal-choice ${checked ? "checked" : ""} ${saved ? "already-saved" : ""}`}
                            key={goal.id}
                          >
                            <input
                              type="checkbox"
                              aria-label={`${goal.code} ${goal.description}`}
                              checked={checked || saved}
                              disabled={saved || (!checked && atLimit)}
                              onChange={(e) =>
                                e.target.checked
                                  ? setSelectedIds((ids) => [...ids, goal.id])
                                  : remove(goal.id)
                              }
                            />
                            <span>
                              <span className="goal-meta">
                                <b>{goal.code}</b>
                                <span className="goal-age-tag">
                                  {goal.ageLabel}
                                </span>
                              </span>
                              <span className="goal-choice-description">
                                {goal.description}
                              </span>
                              {saved && (
                                <small>
                                  Đã có trong ngân hàng của học sinh
                                </small>
                              )}
                            </span>
                          </label>
                        );
                      })}
                    </div>
                  )}
                </section>
              );
            })}
          </section>
          <aside className="goal-selection-summary">
            <div className="goal-summary-top">
              <span className="goal-summary-icon">
                <Target size={25} />
              </span>
              <h3>Mục tiêu đã chọn</h3>
              <div className="goal-selection-total" aria-live="polite">
                {selected.length}
                <small>{unlimited ? "mục tiêu" : `/ ${limit} mục tiêu`}</small>
              </div>
              <p>
                Thuộc {new Set(selected.map((g) => g.domainId)).size} lĩnh vực
                can thiệp
              </p>
            </div>
            {selected.length ? (
              <div className="goal-picked-list">
                {selected.map((g) => (
                  <div key={g.id}>
                    <span>
                      <b>{g.domainName}</b>
                      <small>
                        {g.code} · {g.ageLabel}
                      </small>
                    </span>
                    <button
                      aria-label={`Bỏ mục tiêu ${g.code}`}
                      onClick={() => remove(g.id)}
                    >
                      <X size={14} />
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <p className="goal-selection-hint">
                Chọn độ tuổi ở một lĩnh vực và đánh dấu mục tiêu để thêm vào
                đây.
              </p>
            )}
            {atLimit && (
              <p className="goal-limit-note" role="status">
                {overLimit
                  ? "Số mục tiêu đang vượt giới hạn. Bỏ bớt lựa chọn hoặc quay lại đổi giới hạn."
                  : "Đã đủ số mục tiêu bạn đặt. Có thể bỏ chọn hoặc quay lại đổi giới hạn."}
              </p>
            )}
            <div className="goal-summary-student">
              <Avatar name={student.name} />
              <span>
                <small>NGÂN HÀNG CỦA</small>
                <b>{student.name}</b>
              </span>
            </div>
          </aside>
        </div>
      )}
      {step === 2 && (
        <section className="goal-review">
          <div className="goal-review-banner">
            <span className="goal-summary-icon">
              <ClipboardList size={25} />
            </span>
            <div>
              <h3>{selected.length} mục tiêu sẵn sàng thêm vào ngân hàng</h3>
              <p>
                {new Set(selected.map((g) => g.domainId)).size} lĩnh vực ·{" "}
                {program?.name} · {student.name}
              </p>
            </div>
            <Button icon={<ArrowLeft size={14} />} onClick={() => moveStep(1)}>
              Điều chỉnh lựa chọn
            </Button>
          </div>
          {selected.length ? (
            <GoalGroups goals={selected} onRemove={remove} />
          ) : (
            <Empty description="Chưa có mục tiêu. Quay lại để chọn mục tiêu cho học sinh." />
          )}
        </section>
      )}
      <div className="goal-wizard-footer">
        <Button
          icon={<ArrowLeft size={15} />}
          onClick={() => (step === 0 ? exit() : moveStep(step - 1))}
        >
          {step === 0 ? "Về ngân hàng" : "Quay lại"}
        </Button>
        <span aria-live="polite">
          {step === 0 ? (
            program ? (
              `Đã chọn thư viện ${program.shortName}`
            ) : (
              "Chọn thư viện để bắt đầu"
            )
          ) : (
            <>
              <b>{selected.length}</b> mục tiêu đã chọn
            </>
          )}
        </span>
        <Button
          type="primary"
          icon={step === 2 ? <Check size={16} /> : <ArrowRight size={16} />}
          disabled={step === 0 ? !programId : !selected.length || overLimit}
          onClick={() => (step === 2 ? finish() : moveStep(step + 1))}
        >
          {
            ["Tiếp tục: Lĩnh vực can thiệp", "Kiểm tra lựa chọn", "Hoàn tất"][
              step
            ]
          }
        </Button>
      </div>
    </div>
  );
}

function StudentBank({
  student,
  onSuggest,
  savedCount,
}: {
  student: Student;
  onSuggest: () => void;
  savedCount: number | null;
}) {
  const { view } = useDemo();
  const [query, setQuery] = useState("");
  const [programFilter, setProgramFilter] = useState("");
  const records = view.studentGoals.filter((g) => g.studentId === student.id);
  const goals = records.map((r) => r.goal);
  const filtered = goals.filter(
    (g) =>
      (!programFilter || g.programId === programFilter) &&
      normalize(`${g.description} ${g.code} ${g.domainName}`).includes(
        normalize(query),
      ),
  );
  return (
    <div className="student-goal-bank">
      {savedCount !== null && (
        <div className="goal-save-success" role="status">
          <CheckCircle2 size={20} />
          <div>
            <b>Ngân hàng mục tiêu đã được cập nhật</b>
            <span>
              Đã thêm {savedCount} mục tiêu cho {student.name}. Bạn có thể xem
              lại bên dưới.
            </span>
          </div>
        </div>
      )}
      <div className="goal-section-heading goal-bank-heading">
        <div>
          <h2>
            Ngân hàng mục tiêu{" "}
            <span className="goal-count">{goals.length}</span>
          </h2>
          <p>
            Tập hợp mục tiêu dự kiến triển khai trên hành trình phát triển của
            con.
          </p>
        </div>
        {goals.length > 0 && (
          <Button
            type="primary"
            icon={<Sparkles size={16} />}
            onClick={onSuggest}
          >
            Gợi ý mục tiêu
          </Button>
        )}
      </div>
      {!goals.length ? (
        <section className="panel goal-empty">
          <div className="goal-empty-art" aria-hidden="true">
            <span className="goal-art-book">
              <BookOpen size={48} strokeWidth={1.5} />
            </span>
            <span className="goal-art-target">
              <Target size={38} strokeWidth={1.5} />
            </span>
            <span className="goal-art-check">
              <Check size={14} />
            </span>
            <i />
            <i />
          </div>
          <span className="goal-pill">MỖI BƯỚC NHỎ, MỘT TIẾN BỘ MỚI</span>
          <h2>Bắt đầu ngân hàng mục tiêu của con</h2>
          <p>
            {student.name} chưa có mục tiêu trong ngân hàng. Chọn từ thư viện và
            xây dựng danh sách phù hợp với từng lĩnh vực phát triển.
          </p>
          <Button
            type="primary"
            size="large"
            icon={<Sparkles size={17} />}
            onClick={onSuggest}
          >
            Gợi ý mục tiêu
          </Button>
          <div className="goal-empty-path">
            <span>
              <Library size={15} /> Chọn thư viện
            </span>
            <ChevronRight size={13} />
            <span>
              <Target size={15} /> Chọn mục tiêu
            </span>
            <ChevronRight size={13} />
            <span>
              <CheckCircle2 size={15} /> Xem lại & hoàn tất
            </span>
          </div>
        </section>
      ) : (
        <>
          <div className="goal-bank-summary">
            <span>
              <Target size={17} />
              <b>{goals.length}</b> mục tiêu
            </span>
            <span>
              <Layers3 size={17} />
              <b>{new Set(goals.map((g) => g.domainId)).size}</b> lĩnh vực
            </span>
            <span>
              <BookOpen size={17} />
              <b>{new Set(goals.map((g) => g.programId)).size}</b> chương trình
            </span>
          </div>
          <div className="goal-bank-filters">
            <Input
              aria-label="Tìm mục tiêu đã lưu"
              prefix={<Search size={16} />}
              placeholder="Tìm mục tiêu, mã hoặc lĩnh vực…"
              allowClear
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            <select
              aria-label="Lọc thư viện đã lưu"
              value={programFilter}
              onChange={(e) => setProgramFilter(e.target.value)}
            >
              <option value="">Tất cả thư viện</option>
              {goalPrograms
                .filter((p) => goals.some((g) => g.programId === p.id))
                .map((p) => (
                  <option value={p.id} key={p.id}>
                    {p.shortName}
                  </option>
                ))}
            </select>
            <span>{filtered.length} mục tiêu</span>
          </div>
          {filtered.length ? (
            <GoalGroups goals={filtered} />
          ) : (
            <Empty description="Không tìm thấy mục tiêu phù hợp" />
          )}
        </>
      )}
    </div>
  );
}

function StudentIntervention({ student }: { student: Student }) {
  const { db, view } = useDemo();
  const [params, setParams] = useSearchParams();
  const [wizard, setWizard] = useState(false);
  const [savedCount, setSavedCount] = useState<number | null>(null);
  const goals = view.studentGoals.filter((g) => g.studentId === student.id);
  const tab = params.get("tab") === "bank" ? "bank" : "overview";
  function showBank() {
    setParams({ tab: "bank" });
  }
  return (
    <>
      {!wizard && (
        <div className="goal-breadcrumb">
          <Link to={basePath}>Kế hoạch can thiệp</Link>
          <ChevronRight size={13} />
          <span>{student.name}</span>
        </div>
      )}
      <section className="panel goal-student-profile">
        <Avatar name={student.name} />
        <div className="goal-student-name">
          <span className="eyebrow">HỒ SƠ CAN THIỆP CÁ NHÂN</span>
          <h1>{student.name}</h1>
          <p>
            {student.id}
            <span>·</span>
            {age(student.birthDate)}
            <span>·</span>
            <Badge>{student.status}</Badge>
          </p>
        </div>
        <div className="goal-student-teacher">
          <small>GIÁO VIÊN PHỤ TRÁCH</small>
          <b>
            {db.staff.find((s) => s.id === student.teacherId)?.name ??
              "Chưa phân công"}
          </b>
        </div>
      </section>
      {wizard ? (
        <GoalWizard
          student={student}
          onExit={() => setWizard(false)}
          onSaved={(count) => {
            setWizard(false);
            setSavedCount(count);
            showBank();
            window.scrollTo({ top: 0 });
          }}
        />
      ) : (
        <>
          <Tabs
            className="goal-student-tabs"
            activeKey={tab}
            onChange={(key) => setParams(key === "bank" ? { tab: "bank" } : {})}
            items={[
              {
                key: "overview",
                label: (
                  <span className="goal-tab-label">
                    <ClipboardList size={16} /> Tổng quan can thiệp
                  </span>
                ),
              },
              {
                key: "bank",
                label: (
                  <span className="goal-tab-label">
                    <Target size={16} /> Ngân hàng mục tiêu{" "}
                    <span>{goals.length}</span>
                  </span>
                ),
              },
            ]}
          />
          {tab === "bank" ? (
            <StudentBank
              student={student}
              savedCount={savedCount}
              onSuggest={() => {
                setSavedCount(null);
                setWizard(true);
              }}
            />
          ) : (
            <section className="panel goal-overview">
              <span className="goal-summary-icon">
                <ClipboardList size={28} />
              </span>
              <div>
                <span className="eyebrow">
                  HÀNH TRÌNH CỦA {student.name.toLocaleUpperCase("vi")}
                </span>
                <h2>Chuẩn bị mục tiêu cho kế hoạch can thiệp</h2>
                <p>
                  Ngân hàng mục tiêu giúp lưu những kỹ năng dự kiến triển khai
                  cho học sinh. Bắt đầu bằng việc chọn chương trình, lĩnh vực và
                  độ tuổi phù hợp.
                </p>
                <div className="goal-overview-numbers">
                  <span>
                    <b>{goals.length}</b> mục tiêu trong ngân hàng
                  </span>
                  <span>
                    <b>{new Set(goals.map((g) => g.goal.domainId)).size}</b>{" "}
                    lĩnh vực đã chọn
                  </span>
                </div>
                <Button
                  type="primary"
                  icon={<ArrowRight size={16} />}
                  onClick={showBank}
                >
                  Mở ngân hàng mục tiêu
                </Button>
              </div>
            </section>
          )}
        </>
      )}
    </>
  );
}

export function InterventionPlans() {
  const { view } = useDemo();
  const nav = useNavigate();
  const { pathname } = useLocation();
  const studentId = pathname.slice(basePath.length + 1);
  const student = view.students.find((s) => s.id === studentId);
  if (studentId)
    return (
      <div className="intervention-page">
        {student ? (
          <StudentIntervention key={student.id} student={student} />
        ) : (
          <Result
            status="404"
            title="Không tìm thấy hồ sơ học sinh"
            subTitle="Hồ sơ không tồn tại hoặc không thuộc phạm vi được phân công."
            extra={
              <Button onClick={() => nav(basePath)}>
                Về danh sách học sinh
              </Button>
            }
          />
        )}
      </div>
    );
  const withBank = new Set(view.studentGoals.map((g) => g.studentId)).size;
  return (
    <div className="intervention-page">
      <div className="page-heading">
        <div>
          <div className="eyebrow">QUẢN LÝ LỚP CÁ NHÂN</div>
          <h1>Kế hoạch can thiệp</h1>
          <p>
            Mỗi học sinh một hành trình. Cùng xây dựng những mục tiêu phù hợp
            cho con.
          </p>
        </div>
        <span className="goal-pill">
          <Target size={16} /> Đồng hành từng bước nhỏ
        </span>
      </div>
      <div className="stats-grid goal-dashboard-stats">
        {[
          {
            label: "Học sinh",
            value: view.students.length,
            icon: <Users size={20} />,
            note: "Trong phạm vi quản lý",
          },
          {
            label: "Đã có ngân hàng mục tiêu",
            value: withBank,
            icon: <BookOpen size={20} />,
            note: "Học sinh đã được chọn mục tiêu",
          },
          {
            label: "Mục tiêu đã lưu",
            value: view.studentGoals.length,
            icon: <Target size={20} />,
            note: "Trong ngân hàng của các học sinh",
          },
          {
            label: "Thư viện mục tiêu",
            value: goalPrograms.length,
            icon: <Library size={20} />,
            note: "Chương trình minh họa để lựa chọn",
          },
        ].map((item) => (
          <div className="stat-card" key={item.label}>
            <div className="stat-top">
              <span>{item.label}</span>
              <span className="stat-icon">{item.icon}</span>
            </div>
            <strong>{item.value}</strong>
            <small>{item.note}</small>
          </div>
        ))}
      </div>
      <DataTable
        title="Hành trình can thiệp của học sinh"
        subtitle="Chọn học sinh để xem và xây dựng ngân hàng mục tiêu."
        rows={view.students}
        pageSize={10}
        searchText={(s) => `${s.name} ${s.id}`}
        statusOf={(s) => s.status}
        statuses={["Đang học", "Tạm nghỉ", "Ngừng học"]}
        columns={[
          {
            label: "Học sinh",
            render: (s, i) => (
              <div className="person-cell">
                <Avatar name={s.name} index={i} />
                <div>
                  <button
                    className="name-link"
                    onClick={() => nav(`${basePath}/${s.id}`)}
                  >
                    {s.name}
                  </button>
                  <small>{s.id}</small>
                </div>
              </div>
            ),
          },
          { label: "Độ tuổi", render: (s) => age(s.birthDate) },
          { label: "Trạng thái", render: (s) => <Badge>{s.status}</Badge> },
          {
            label: "Ngân hàng mục tiêu",
            render: (s) => {
              const n = view.studentGoals.filter(
                (g) => g.studentId === s.id,
              ).length;
              return (
                <span
                  className={`goal-bank-table-count ${n ? "has-goals" : ""}`}
                >
                  <Target size={14} />
                  {n ? `${n} mục tiêu` : "Chưa có mục tiêu"}
                </span>
              );
            },
          },
          {
            label: "Lĩnh vực",
            render: (s) => {
              const n = new Set(
                view.studentGoals
                  .filter((g) => g.studentId === s.id)
                  .map((g) => g.goal.domainId),
              ).size;
              return n ? `${n} lĩnh vực` : "—";
            },
          },
          {
            label: "Thao tác",
            render: (s) => (
              <Button
                type="text"
                className="goal-open-student"
                onClick={() => nav(`${basePath}/${s.id}`)}
              >
                Xem chi tiết <ArrowRight size={14} />
              </Button>
            ),
          },
        ]}
      />
    </div>
  );
}
