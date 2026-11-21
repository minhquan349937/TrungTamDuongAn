import { useState } from "react";
import { Button, Popover } from "antd";
import { ChevronDown } from "lucide-react";
import type { ActivityChoice, GoalSelection } from "../services/goalBankApi";
import { GoalActivitySelect } from "./GoalActivitySelect";

export function GoalActivityPicker({ target, onChange, disabled }: {
  target: GoalSelection;
  onChange: (target: GoalSelection, activities: ActivityChoice[]) => void;
  disabled: boolean;
}) {
  const [open, setOpen] = useState(false);
  const selected = target.activities ?? [];
  return <Popover trigger="click" placement="bottomRight" open={open} onOpenChange={setOpen}
    content={<div style={{ width: 360, maxWidth: "80vw" }}>
      <b>Hoạt động gợi ý · {target.code}</b>
      <GoalActivitySelect goalId={target.goalId} value={selected} disabled={disabled} insidePopover
        onChange={rows => onChange(target, rows)} />
    </div>}>
    <Button size="small" icon={<ChevronDown size={14} />} aria-label={`Hoạt động gợi ý cho ${target.code}`}>
      Hoạt động gợi ý{selected.length > 0 && ` (${selected.length})`}
    </Button>
  </Popover>;
}
