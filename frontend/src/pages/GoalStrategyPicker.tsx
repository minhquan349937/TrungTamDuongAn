import { useId, useState } from "react";
import { Alert, Button, Checkbox, Empty, Input, Popover, Spin } from "antd";
import { ChevronDown, X } from "lucide-react";
import type { GoalSelection, StrategyChoice } from "../services/goalBankApi";

export type StrategyOption = { id: string; name: string; description: string | null };

export function GoalStrategyPicker({ target, options, loading, error, retry, onChange, disabled }: {
  target: GoalSelection;
  options: StrategyOption[];
  loading: boolean;
  error: string;
  retry: () => void;
  onChange: (target: GoalSelection, strategies: StrategyChoice[]) => void;
  disabled: boolean;
}) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  const selected = target.strategies ?? [];
  function toggle(option: StrategyOption) {
    onChange(target, selected.some(row => row.strategyId === option.id)
      ? selected.filter(row => row.strategyId !== option.id)
      : [...selected, { strategyId: option.id, name: option.name, description: null }]);
  }
  return <div className="ip-strategies">
    <Popover trigger="click" placement="bottomRight" open={open} onOpenChange={setOpen}
      destroyOnHidden overlayClassName="ip-strategy-popover"
      title={<div className="ip-strategy-popover-heading">
        <span>Chiến lược · {target.code}</span>
        <Button type="text" size="small" icon={<X size={15} />}
          aria-label={`Đóng chiến lược ${target.code}`} onClick={() => setOpen(false)} />
      </div>}
      content={<div id={panelId} className="ip-strategy-options" role="group" aria-label={`Chiến lược ${target.code}`}>
      {loading ? <Spin size="small" /> : error ? <Alert type="error" message={error}
        action={<Button size="small" onClick={retry}>Thử lại</Button>} />
        : !options.length ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có chiến lược đang sử dụng" />
        : options.map(option => {
          const current = selected.find(row => row.strategyId === option.id);
          return <div key={option.id} className="ip-strategy-option">
            <Checkbox checked={!!current} disabled={disabled} onChange={() => toggle(option)}>{option.name}</Checkbox>
            {option.description && <small>{option.description}</small>}
            {current && <Input.TextArea disabled={disabled} autoSize={{ minRows: 2, maxRows: 5 }}
              aria-label={`Cách áp dụng ${option.name} cho ${target.code}`}
              placeholder="Cách áp dụng cho mục tiêu này (không bắt buộc)"
              value={current.description ?? ""}
              onChange={event => onChange(target, selected.map(row => row.strategyId === option.id
                ? { ...row, description: event.target.value || null } : row))} />}
          </div>;
        })}
    </div>}>
      <Button size="small" icon={<ChevronDown size={14} />}
        aria-expanded={open} aria-controls={open ? panelId : undefined} aria-label={`Chiến lược cho ${target.code}`}>
        Chiến lược{selected.length > 0 && ` (${selected.length})`}
      </Button>
    </Popover>
  </div>;
}
