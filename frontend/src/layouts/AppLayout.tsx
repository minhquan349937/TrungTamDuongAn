import { useConfirm } from "../hooks/useConfirm";
import { useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { Button, Select, Modal, Popover, Input } from "antd";
import {
  Sprout,
  LayoutDashboard,
  Building2,
  FolderOpen,
  UserRound,
  Users,
  Sun,
  CalendarDays,
  BookOpen,
  ChevronDown,
  PanelLeftClose,
  Menu,
  Bell,
  Search,
  ArrowUpRight,
  RotateCcw,
  Settings,
  ShieldCheck,
} from "lucide-react";
import { getMenu } from "../routes/menu";
import { useDemo } from "../hooks/useDemo";
import { mockService } from "../services/mockService";
import type { Role } from "../types";
import { Avatar } from "../components/ui";
const icons = {
  system: LayoutDashboard,
  building: Building2,
  folder: FolderOpen,
  individual: UserRound,
  groups: Users,
  daycare: Sun,
  calendar: CalendarDays,
  library: BookOpen,
};
export function AppLayout() {
  const modal = useConfirm();
  const { db, role, setRole, child, setChild, run } = useDemo(),
    nav = useNavigate(),
    location = useLocation();
  const [collapsed, setCollapsed] = useState(false),
    [mobile, setMobile] = useState(false),
    [expanded, setExpanded] = useState<string[]>([
      "Quản lý hệ thống",
      "Không gian giáo viên",
      "Công việc của tôi",
      "Đồng hành cùng con",
    ]),
    [search, setSearch] = useState("");
  const menu = getMenu(role),
    roleName =
      role === "ADMIN"
        ? "Quản trị viên"
        : role === "TEACHER"
          ? "Giáo viên"
          : "Phụ huynh";
  const name =
    role === "ADMIN"
      ? db.admins[0].name
      : role === "TEACHER"
        ? (db.staff.find((s) => s.id === "NV001")?.name ?? "Giáo viên")
        : db.parents[0].name;
  function reset() {
    modal.confirm({
      title: "Khôi phục dữ liệu demo?",
      content:
        "Tất cả thay đổi trong trình duyệt này sẽ được thay bằng dữ liệu mẫu ban đầu.",
      okText: "Khôi phục",
      cancelText: "Hủy",
      onOk: () => {
        run(() => mockService.reset(role), "Đã khôi phục dữ liệu mẫu");
        setChild("HS001");
      },
    });
  }
  return (
    <div
      className={
        "app-shell " +
        (collapsed ? "collapsed " : "") +
        (mobile ? "mobile-open" : "")
      }
    >
      {mobile && (
        <button
          className="sidebar-scrim"
          aria-label="Đóng menu"
          onClick={() => setMobile(false)}
        />
      )}
      <aside className="sidebar">
        <NavLink to="/dashboard" className="brand">
          <span className="brand-symbol">
            <Sprout size={25} />
          </span>
          <div>
            Dương<span>An</span>
            <small>ĐỒNG HÀNH PHÁT TRIỂN</small>
          </div>
        </NavLink>
        <button
          className="collapse-button"
          aria-label="Thu gọn hoặc mở sidebar"
          onClick={() => {
            setCollapsed(!collapsed);
            setMobile(false);
          }}
        >
          <PanelLeftClose size={16} />
        </button>
        <div className="center-label">
          <Building2 size={16} />
          <span>
            Trung tâm Dương An<small>Cơ sở chính · Hà Nội</small>
          </span>
          <ChevronDown size={14} />
        </div>
        <p className="nav-caption">KHÔNG GIAN LÀM VIỆC</p>
        <nav>
          {menu.map((g) => {
            const Icon = icons[g.icon as keyof typeof icons],
              open = expanded.includes(g.label),
              active = g.items.some((i) => i.path === location.pathname);
            return (
              <div className="nav-group" key={g.label}>
                <button
                  className={"nav-parent " + (active ? "active-group" : "")}
                  title={g.label}
                  onClick={() => {
                    if (collapsed) setCollapsed(false);
                    setExpanded(
                      open
                        ? expanded.filter((x) => x !== g.label)
                        : [...expanded, g.label],
                    );
                  }}
                >
                  <Icon size={19} />
                  <span>{g.label}</span>
                  <ChevronDown className={open ? "rotated" : ""} size={14} />
                </button>
                {open && !collapsed && (
                  <div className="nav-children">
                    {g.items.map((i) => (
                      <NavLink
                        key={i.path}
                        to={i.path}
                        onClick={() => setMobile(false)}
                      >
                        <span className="nav-dot" />
                        {i.label}
                      </NavLink>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </nav>
        <div className="sidebar-bottom">
          <div className="demo-card">
            <span>
              <ShieldCheck size={18} /> Không gian demo
            </span>
            <p>Dữ liệu giả, trải nghiệm thật.</p>
            <span className="demo-tag">CHỈ DÙNG ĐỂ MINH HỌA</span>
          </div>
          {role === "ADMIN" && (
            <>
              <button onClick={() => nav("/settings")}>
                <Settings size={18} />
                <span>Thiết lập trung tâm</span>
              </button>
              <button onClick={reset}>
                <RotateCcw size={17} />
                <span>Khôi phục dữ liệu demo</span>
              </button>
            </>
          )}
          <small>© {new Date().getFullYear()} Dương An</small>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="header-left">
            <button
              className="icon-button mobile-toggle"
              aria-label="Mở menu"
              onClick={() => setMobile(!mobile)}
            >
              <Menu size={21} />
            </button>
            <span className="workspace-label">
              Không gian quản lý <span>/</span> <b>{roleName}</b>
            </span>
          </div>
          <div className="header-right">
            <Popover
              trigger="click"
              title="Tìm nhanh chức năng"
              content={
                <div className="quick-search">
                  <Input
                    autoFocus
                    placeholder="Tên chức năng…"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                  />
                  {menu
                    .flatMap((g) => g.items)
                    .filter((i) =>
                      i.label.toLowerCase().includes(search.toLowerCase()),
                    )
                    .slice(0, 8)
                    .map((i) => (
                      <button key={i.path} onClick={() => nav(i.path)}>
                        {i.label}
                        <ArrowUpRight size={14} />
                      </button>
                    ))}
                </div>
              }
            >
              <button className="icon-button" aria-label="Tìm kiếm chức năng">
                <Search size={20} />
              </button>
            </Popover>
            <Popover
              trigger="click"
              title="Thông báo mới"
              content={
                <div className="notice-popover">
                  {db.notices.map((n) => (
                    <button key={n.id} onClick={() => nav("/notifications")}>
                      <b>{n.title}</b>
                      <small>{n.date}</small>
                    </button>
                  ))}
                </div>
              }
            >
              <button
                className="icon-button notification-button"
                aria-label="Thông báo"
              >
                <Bell size={20} />
                <i />
              </button>
            </Popover>
            <span className="header-divider" />
            <Select
              className="role-select"
              aria-label="Chuyển vai trò demo"
              value={role}
              onChange={(v: Role) => {
                setRole(v);
                nav("/dashboard");
              }}
              options={[
                { value: "ADMIN", label: "Quản trị viên" },
                { value: "TEACHER", label: "Giáo viên" },
                { value: "PARENT", label: "Phụ huynh" },
              ]}
            />
            <Popover
              trigger="click"
              content={
                <div>
                  <b>{name}</b>
                  <p>Tài khoản {roleName.toLowerCase()} minh họa</p>
                  <Button
                    onClick={() =>
                      nav(
                        role === "TEACHER"
                          ? "/work"
                          : role === "PARENT"
                            ? "/students"
                            : "/accounts",
                      )
                    }
                  >
                    Xem hồ sơ tài khoản
                  </Button>
                </div>
              }
            >
              <button className="account-button">
                <Avatar name={name} />
                <span>
                  <b>{name}</b>
                  <small>{roleName}</small>
                </span>
                <ChevronDown size={13} />
              </button>
            </Popover>
          </div>
        </header>
        <main>
          {role === "PARENT" && (
            <div className="child-selector">
              <span>Đang đồng hành cùng</span>
              <Select
                aria-label="Chọn hồ sơ con"
                value={child}
                onChange={setChild}
                options={db.students
                  .filter((s) => db.parents[0].studentIds.includes(s.id))
                  .map((s) => ({ value: s.id, label: s.name }))}
              />
              <span className="badge blue">Hồ sơ minh họa</span>
            </div>
          )}
          <Outlet />
        </main>
        <footer className="page-footer">
          <span>Dương An · Cùng con lớn lên mỗi ngày</span>
          <span>
            <i /> Bản demo · Dữ liệu giả lập
          </span>
        </footer>
      </div>
    </div>
  );
}
