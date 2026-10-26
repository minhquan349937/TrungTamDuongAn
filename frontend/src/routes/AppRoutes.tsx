import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import { Result, Button } from "antd";
import { AppLayout } from "../layouts/AppLayout";
import { Dashboard, Charts } from "../pages/Dashboard";
import { StudentsTable, StaffTable } from "../features/PeopleTables";
import { Classes } from "../pages/Classes";
import { Schedule } from "../pages/Schedule";
import { Fees } from "../pages/Fees";
import {
  LeavePage,
  WorkPage,
  Notifications,
  Parents,
  Accounts,
  PhaseTwo,
} from "../pages/Supporting";
import { useDemo } from "../hooks/useDemo";
import { canVisit } from "../services/authorization";
function Content() {
  const { role } = useDemo(),
    { pathname } = useLocation();
  if (!canVisit(role, pathname))
    return (
      <Result
        status="403"
        title="Bạn không có quyền truy cập"
        subTitle="Vai trò hiện tại không được phép xem màn hình này."
        extra={<Button href="#/dashboard">Về tổng quan</Button>}
      />
    );
  if (pathname === "/dashboard") return <Dashboard />;
  if (pathname === "/students")
    return (
      <>
        <div className="page-heading">
          <div>
            <div className="eyebrow">QUẢN LÝ HỒ SƠ</div>
            <h1>{role === "PARENT" ? "Hồ sơ của con" : "Hồ sơ học sinh"}</h1>
            <p>Quan tâm từng học sinh, theo dõi từng bước phát triển.</p>
          </div>
        </div>
        <StudentsTable />
      </>
    );
  if (pathname === "/staff")
    return (
      <>
        <div className="page-heading">
          <div>
            <div className="eyebrow">QUẢN LÝ HỒ SƠ</div>
            <h1>Đội ngũ Dương An</h1>
            <p>Những người đồng hành tận tâm trên hành trình của con.</p>
          </div>
        </div>
        <StaffTable />
      </>
    );
  if (/^\/(individual|groups|daycare)(\/(assign|students))?$/.test(pathname))
    return <Classes />;
  if (pathname.startsWith("/schedule") || pathname.startsWith("/attendance"))
    return <Schedule key={pathname} />;
  if (["/fees", "/payments"].includes(pathname)) return <Fees />;
  if (pathname === "/leave") return <LeavePage />;
  if (pathname.startsWith("/work")) return <WorkPage />;
  if (pathname === "/notifications") return <Notifications />;
  if (pathname === "/parents") return <Parents />;
  if (pathname === "/accounts") return <Accounts />;
  if (["/analytics", "/facility"].includes(pathname))
    return (
      <>
        <div className="page-heading">
          <div>
            <h1>Biểu đồ thống kê trung tâm</h1>
            <p>Số liệu được cập nhật từ dữ liệu demo hiện tại.</p>
          </div>
        </div>
        <Charts />
      </>
    );
  return <PhaseTwo />;
}
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Content />} />
      </Route>
    </Routes>
  );
}
