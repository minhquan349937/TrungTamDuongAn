import type { ReactNode } from "react";
import {
  Search,
  ChevronLeft,
  ChevronRight,
  ArrowUpRight,
  SlidersHorizontal,
} from "lucide-react";
import { Input, Select, Empty } from "antd";
import { useState } from "react";
import { normalize, initials } from "../utils/format";
export function Avatar({ name, index = 0 }: { name: string; index?: number }) {
  return (
    <span className={"avatar avatar-" + (index % 4)}>{initials(name)}</span>
  );
}
export function Badge({ children, tone }: { children: ReactNode; tone?: "green" | "red" }) {
  const text = String(children);
  return (
    <span
      className={
        "badge " +
        (tone ??
          (/Đang|Có mặt|Đã duyệt|Đã thanh toán|Đã chấm/.test(text)
            ? "green"
            : /Tạm|Chờ|Chưa|Nghỉ|Có phép/.test(text)
              ? "amber"
              : /Ngừng|Vắng|Từ chối/.test(text)
                ? "red"
                : "blue"))
      }
    >
      {children}
    </span>
  );
}
export function StatCard({
  label,
  value,
  icon,
  delta,
  caption,
}: {
  label: string;
  value: ReactNode;
  icon: ReactNode;
  delta: string;
  caption: string;
}) {
  return (
    <div className="stat-card">
      <div className="stat-top">
        <span>{label}</span>
        <span className="stat-icon">{icon}</span>
      </div>
      <strong>{value}</strong>
      <div className="stat-bottom">
        <span>
          <ArrowUpRight size={14} />
          {delta}
        </span>
        <small>{caption}</small>
      </div>
    </div>
  );
}
export function SectionTitle({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
}) {
  return (
    <div className="section-title">
      <div>
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}
export function DataTable<T extends { id: string }>({
  title,
  subtitle,
  rows,
  columns,
  searchText,
  statusOf,
  statuses = [],
  action,
  pageSize = 5,
}: {
  title: string;
  subtitle?: string;
  rows: T[];
  columns: { label: string; render: (row: T, index: number) => ReactNode }[];
  searchText: (row: T) => string;
  statusOf?: (row: T) => string;
  statuses?: string[];
  action?: ReactNode;
  pageSize?: number;
}) {
  const [query, setQuery] = useState(""),
    [filter, setFilter] = useState("all"),
    [page, setPage] = useState(1),
    [size, setSize] = useState(pageSize);
  const filtered = rows.filter(
    (r) =>
      normalize(searchText(r)).includes(normalize(query)) &&
      (filter === "all" || statusOf?.(r) === filter),
  );
  const count = Math.max(1, Math.ceil(filtered.length / size)),
    current = Math.min(page, count);
  return (
    <section className="panel data-panel">
      <SectionTitle title={title} subtitle={subtitle} action={action} />
      <div className="table-toolbar">
        <Input
          aria-label={"Tìm kiếm " + title.toLowerCase()}
          prefix={<Search size={16} />}
          placeholder="Tìm theo tên hoặc mã…"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setPage(1);
          }}
          allowClear
        />
        {statuses.length > 0 && (
          <Select
            aria-label={"Lọc " + title.toLowerCase()}
            value={filter}
            onChange={(v) => {
              setFilter(v);
              setPage(1);
            }}
            suffixIcon={<SlidersHorizontal size={14} />}
            options={[
              { value: "all", label: "Tất cả trạng thái" },
              ...statuses.map((s) => ({ value: s, label: s })),
            ]}
          />
        )}
        <span className="results-hint">{filtered.length} kết quả</span>
      </div>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              {columns.map((c) => (
                <th key={c.label}>{c.label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered
              .slice((current - 1) * size, current * size)
              .map((r, i) => (
                <tr key={r.id}>
                  {columns.map((c) => (
                    <td key={c.label}>{c.render(r, i)}</td>
                  ))}
                </tr>
              ))}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <Empty
            description="Không tìm thấy dữ liệu phù hợp"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        )}
      </div>
      <div className="pagination">
        <span>
          Hiển thị{" "}
          <select
            aria-label={"Số dòng " + title}
            value={size}
            onChange={(e) => {
              setSize(Number(e.target.value));
              setPage(1);
            }}
          >
            {[3, 5, 10, 20].map((n) => (
              <option key={n}>{n}</option>
            ))}
          </select>{" "}
          / {filtered.length} kết quả
        </span>
        <div>
          <button
            aria-label="Trang trước"
            disabled={current === 1}
            onClick={() => setPage(current - 1)}
          >
            <ChevronLeft size={15} />
          </button>
          {Array.from({ length: count }, (_, i) => i + 1)
            .filter(
              (n) =>
                count < 8 ||
                Math.abs(n - current) < 2 ||
                n === 1 ||
                n === count,
            )
            .map((n) => (
              <button
                key={n}
                className={n === current ? "selected" : ""}
                onClick={() => setPage(n)}
              >
                {n}
              </button>
            ))}
          <button
            aria-label="Trang sau"
            disabled={current === count}
            onClick={() => setPage(current + 1)}
          >
            <ChevronRight size={15} />
          </button>
        </div>
      </div>
    </section>
  );
}
