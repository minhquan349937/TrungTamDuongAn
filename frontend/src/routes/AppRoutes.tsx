import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import { Result, Button, Spin } from "antd";
import { AppLayout } from "../layouts/AppLayout";
import { Dashboard, Charts } from "../pages/Dashboard";
import { StudentsTable, StaffTable } from "../features/PeopleTables";
import { DashboardStaffTable } from "../features/DashboardPeopleTables";
import { StudentProfiles } from "../pages/StudentProfiles";
import { Classes } from "../pages/Classes";
import { Schedule } from "../pages/Schedule";
import { Fees } from "../pages/Fees";
import {
  LeavePage,
  WorkPage,
  Notifications,
  Parents,
  PhaseTwo,
} from "../pages/Supporting";
import { useDemo } from "../hooks/useDemo";
import { canVisit } from "../services/authorization";
import { RealInterventionPlans } from "../pages/RealInterventionPlans";
import { IndividualAssignments } from "../pages/IndividualAssignments";
import { IndividualReports } from "../pages/IndividualReports";
import { ParentPlanReports } from "../pages/PlanMonthlyEvaluations";
import { GoalCatalogManager } from "../pages/GoalCatalogManager";
import { LoginPage } from "../pages/LoginPage";
import { PublicWebsitePage, WebsitePreviewPage } from "../pages/PublicWebsite";
import { WebsiteManager } from "../pages/WebsiteManager";
import { useAuth } from "../auth/AuthProvider";
import { Accounts } from "../pages/Accounts";
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
  if (pathname === "/reports") return <ParentPlanReports />;
  if (pathname === "/individual/report" || pathname.startsWith("/individual/report/")) return <IndividualReports key={pathname} />;
  if (pathname === "/transfers") return <Navigate to="/students" replace />;
  if (pathname === "/library") return <GoalCatalogManager />;
  if (pathname === "/website") return <WebsiteManager />;
  if (
    pathname === "/individual/plan" ||
    pathname.startsWith("/individual/plan/")
  )
    return <RealInterventionPlans />;
  if (pathname === "/students")
    return role === "ADMIN" ? <StudentProfiles /> : (
      <>
        <div className="page-heading">
          <div>
            <div className="eyebrow">QUẢN LÝ HỒ SƠ</div>
            <h1>{role === "PARENT" ? "Hồ sơ của con" : "Học sinh được phân công"}</h1>
            <p>{role === "TEACHER" ? "Học sinh trong các lớp bạn đang phụ trách." : "Quan tâm từng học sinh, theo dõi từng bước phát triển."}</p>
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
        {role === "ADMIN" ? <DashboardStaffTable compact={false} /> : <StaffTable />}
      </>
    );
  if (pathname === "/individual/assign")
    return role === "ADMIN" ? <IndividualAssignments /> : <Result status="403" title="Bạn không có quyền phân công giáo viên" />;
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
  const { user, loading } = useAuth();
  const { pathname } = useLocation();
  // The public website needs no session: render it before (and without) the login check.
  if (pathname === "/gioi-thieu") return <PublicWebsitePage />;
  if (loading)
    return <div className="loading-screen"><Spin size="large" /><p>Đang kiểm tra phiên đăng nhập…</p></div>;
  return (
    <Routes>
      <Route path="/gioi-thieu/xem-truoc" element={<WebsitePreviewPage />} />
      <Route path="/login" element={user ? <Navigate to="/dashboard" replace /> : <LoginPage />} />
      <Route element={user ? <AppLayout /> : <Navigate to="/login" replace />}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Content />} />
      </Route>
      <Route path="*" element={<Navigate to={user ? "/dashboard" : "/login"} replace />} />
    </Routes>
  );
}
