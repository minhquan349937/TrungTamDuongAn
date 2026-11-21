import type { ActivityChoice, StrategyChoice } from "../services/goalBankApi";

export function GoalMethodsSummary({ strategies, activities }: {
  strategies?: StrategyChoice[];
  activities?: ActivityChoice[];
}) {
  return <>
    {!!strategies?.length && <div className="ip-strategy-summary"><b>Chiến lược</b>
      {strategies.map(row => <div key={row.strategyId}><span>{row.name ?? row.strategyId}</span>
        {row.description && <p>{row.description}</p>}</div>)}
    </div>}
    {!!activities?.length && <div className="ip-strategy-summary"><b>Hoạt động gợi ý</b>
      {activities.map(row => <div key={row.activityId}><span>{row.name ?? row.activityId}</span>
        {row.description && <p>{row.description}</p>}
        {row.note && <p>Áp dụng: {row.note}</p>}</div>)}
    </div>}
  </>;
}
