import { useEffect, useState } from "react";
import { Alert, Button, Checkbox, Empty, Spin } from "antd";
import { ChevronDown } from "lucide-react";
import {
  goalBankApi,
  type GoalDetail,
  type GoalSelection,
  type SpecificGoal,
} from "../services/goalBankApi";
import {
  interventionApi,
  type AgeOption,
  type DomainGroup,
  type DomainTargets,
  type GoalChoice,
  type ProgramSummary,
} from "../services/interventionApi";
export const selectionKey = (row: {
  goalId: string;
  specificGoalId: string | null;
}) => (row.specificGoalId ? `S:${row.specificGoalId}` : `G:${row.goalId}`);

function GoalRow({
  goal,
  age,
  group,
  program,
  selected,
  toggle,
  supportsSpecificGoals,
}: {
  goal: GoalChoice;
  age: AgeOption;
  group: DomainGroup;
  program: ProgramSummary;
  selected: GoalSelection[];
  toggle: (row: GoalSelection) => void;
  supportsSpecificGoals: boolean;
}) {
  const [detail, setDetail] = useState<GoalDetail | null>(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!open || detail || !supportsSpecificGoals || !goal.hasSpecificGoals)
      return;
    let active = true;
    setLoading(true);
    setError("");
    void goalBankApi
      .goal(goal.id)
      .then((row) => {
        if (active) {
          setDetail(row);
          setLoading(false);
        }
      })
      .catch((cause: Error) => {
        if (active) {
          setError(cause.message);
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [open, detail, goal.id, goal.hasSpecificGoals, supportsSpecificGoals]);
  function choice(child: SpecificGoal | null): GoalSelection {
    return {
      goalId: goal.id,
      specificGoalId: child?.id ?? null,
      targetType: child ? "SPECIFIC_GOAL" : "GOAL",
      code: child?.code ?? goal.code,
      name: goal.name,
      description: child?.description ?? goal.description,
      ageMinMonths: child?.ageMinMonths ?? age.ageMinMonths,
      ageMaxMonths: child?.ageMaxMonths ?? age.ageMaxMonths,
      location: {
        programId: program.id,
        programName: program.name,
        domainId: age.domainId,
        domainName: group.name,
        levelId: age.levelId,
        levelName: age.levelName,
        ageMinMonths: age.ageMinMonths,
        ageMaxMonths: age.ageMaxMonths,
      },
    };
  }
  const checked = (row: GoalSelection) =>
    selected.some((item) => selectionKey(item) === selectionKey(row));
  return (
    <article className="ip-goal-row">
      <div className="ip-goal-main">
        {goal.canSelectGeneralGoal ? (
          <Checkbox
            checked={checked(choice(null))}
            onChange={() => toggle(choice(null))}
          >
            <b>{goal.code}</b> · {goal.name}
          </Checkbox>
        ) : (
          <b>
            {goal.code} · {goal.name}
          </b>
        )}
        {goal.description && <p>{goal.description}</p>}
        {supportsSpecificGoals && goal.hasSpecificGoals && (
          <Button
            type="text"
            icon={<ChevronDown size={16} />}
            onClick={() => setOpen(!open)}
            aria-expanded={open}
          >
            {open
              ? "Thu gọn mục tiêu cụ thể"
              : `Chọn mục tiêu cụ thể · ${goal.code}`}
          </Button>
        )}
      </div>
      {open && (
        <div className="ip-children">
          {loading && <Spin />}
          {error && <Alert type="error" message={error} />}
          {detail?.specificGoals.map((child) => (
            <div key={child.id}>
              <Checkbox
                checked={checked(choice(child))}
                onChange={() => toggle(choice(child))}
              >
                <b>{child.code}</b> · {child.description}
              </Checkbox>
              <small className="ip-age">
                {child.ageMinMonths}–{child.ageMaxMonths} tháng
              </small>
            </div>
          ))}
        </div>
      )}
    </article>
  );
}
function AgeGoals({
  age,
  group,
  program,
  selected,
  toggle,
}: {
  age: AgeOption;
  group: DomainGroup;
  program: ProgramSummary;
  selected: GoalSelection[];
  toggle: (row: GoalSelection) => void;
}) {
  const [data, setData] = useState<DomainTargets | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    setData(null); setError("");
    void interventionApi
      .targets(age.levelDomainId)
      .then((row) => {
        if (active) setData(row);
      })
      .catch((cause: Error) => {
        if (active) setError(cause.message);
      });
    return () => {
      active = false;
    };
  }, [age.levelDomainId]);
  if (error) return <Alert type="error" message={error} />;
  if (!data)
    return (
      <div className="ip-loading">
        <Spin /> Đang tải mục tiêu…
      </div>
    );
  if (!data.goals.length)
    return <Empty description="Chưa có mục tiêu trong độ tuổi này" />;
  return (
    <div className="ip-domain-goals">
      {data.goals.map((goal) => (
        <GoalRow
          key={goal.id}
          goal={goal}
          age={age}
          group={group}
          program={program}
          selected={selected}
          toggle={toggle}
          supportsSpecificGoals={data.supportsSpecificGoals}
        />
      ))}
    </div>
  );
}
export function InterventionDomainPicker({
  group,
  index,
  program,
  selected,
  toggle,
  domainId,
  onAgeChange,
}: {
  group: DomainGroup;
  index: number;
  program: ProgramSummary;
  selected: GoalSelection[];
  toggle: (row: GoalSelection) => void;
  domainId: string;
  onAgeChange: (id: string) => void;
}) {
  const age = group.ages.find((row) => row.levelDomainId === domainId);
  const count = selected.filter((row) =>
    group.ages.some((option) => option.domainId === row.location.domainId),
  ).length;
  return (
    <section className="panel ip-domain" aria-label={`Lĩnh vực ${group.name}`}>
      <div className="ip-domain-heading">
        <div>
          <span className="ip-number">{index + 1}</span>
          <h2>{group.name}</h2>
          {count > 0 && <span className="ip-count">Đã chọn {count}</span>}
        </div>
        <label>
          <span>Độ tuổi</span>
          <select
            aria-label={`Độ tuổi ${group.name}`}
            value={domainId}
            onChange={(e) => onAgeChange(e.target.value)}
          >
            <option value="">Chọn tháng tuổi</option>
            {group.ages.map((row) => (
              <option key={row.levelDomainId} value={row.levelDomainId}>
                {row.ageMinMonths}–{row.ageMaxMonths} tháng · {row.levelName}
              </option>
            ))}
          </select>
        </label>
      </div>
      {age ? (
        <AgeGoals
          key={age.levelDomainId}
          age={age}
          group={group}
          program={program}
          selected={selected}
          toggle={toggle}
        />
      ) : (
        <p className="ip-domain-hint">
          Chọn độ tuổi để hiển thị mục tiêu của lĩnh vực này.
        </p>
      )}
    </section>
  );
}
