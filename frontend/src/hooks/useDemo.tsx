import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Alert, App, Spin, Button, Result } from "antd";
import type { Database, Role } from "../types";
import {
  loadDatabase,
  persistDatabase,
  mockService,
} from "../services/mockService";
import { useAuth } from "../auth/AuthProvider";
import { parentPortalApi, parentPortalDatabase, type ParentPortal } from "../services/parentPortalApi";
import { visibleData } from "../services/authorization";
import { teacherPortalApi, teacherPortalDatabase, type TeacherPortal } from "../services/teacherPortalApi";
type Context = {
  db: Database;
  view: Database;
  role: Role;
  child: string;
  setChild: (id: string) => void;
  run: (action: (db: Database) => Database, success?: string) => boolean;
};
const DemoContext = createContext<Context | null>(null);
export function DemoProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return user?.role === "PARENT"
    ? <ParentDataProvider key={user.id}>{children}</ParentDataProvider>
    : user?.role === "TEACHER"
    ? <TeacherDataProvider key={user.id}>{children}</TeacherDataProvider>
    : <LocalDataProvider>{children}</LocalDataProvider>;
}

function TeacherDataProvider({ children }: { children: ReactNode }) {
  const { logout } = useAuth();
  const { message } = App.useApp();
  const [snapshot, setSnapshot] = useState<{ portal: TeacherPortal; db: Database } | null>(null);
  const [error, setError] = useState("");
  const generation = useRef(0);
  const reload = useCallback(async () => {
    const request = ++generation.current;
    try {
      const portal = await teacherPortalApi.get();
      const db = teacherPortalDatabase(portal);
      if (request !== generation.current) return;
      setSnapshot({ portal, db }); setError("");
    } catch (cause) {
      if (request !== generation.current) return;
      setSnapshot(null); setError((cause as Error).message);
    }
  }, []);
  useEffect(() => {
    void reload();
    const refresh = () => { if (document.visibilityState === "visible") void reload(); };
    const timer = window.setInterval(refresh, 15000);
    window.addEventListener("focus", refresh); document.addEventListener("visibilitychange", refresh);
    return () => {
      generation.current++; window.clearInterval(timer);
      window.removeEventListener("focus", refresh); document.removeEventListener("visibilitychange", refresh);
    };
  }, [reload]);
  if (error) return <Result status="warning" title="Không thể tải danh sách phụ trách" subTitle={error}
    extra={<><Button type="primary" onClick={() => void reload()}>Thử lại</Button><Button onClick={() => void logout()}>Đăng xuất</Button></>} />;
  if (!snapshot) return <div className="loading-screen"><Spin size="large" /><p>Đang tải thông tin phân công…</p></div>;
  return <DemoContext.Provider value={{ db: snapshot.db, view: snapshot.db, role: "TEACHER", child: "", setChild: () => {},
    run: () => { message.info("Thao tác này chưa hỗ trợ lưu trên dữ liệu giáo viên. Vui lòng liên hệ quản trị viên."); return false; },
  }}>
    {snapshot.portal.linkStatus === "NO_ACTIVE_STAFF_PROFILE" && <Alert type="info" showIcon message="Tài khoản chưa có hồ sơ giáo viên đang hoạt động. Vui lòng liên hệ quản trị viên để liên kết hồ sơ và phân công lớp." />}
    {children}
  </DemoContext.Provider>;
}

function ParentDataProvider({ children }: { children: ReactNode }) {
  const { logout } = useAuth();
  const [snapshot, setSnapshot] = useState<{ portal: ParentPortal; db: Database } | null>(null);
  const [child, setChild] = useState("");
  const [error, setError] = useState("");
  const generation = useRef(0);
  const { message } = App.useApp();
  const reload = useCallback(async () => {
    const request = ++generation.current;
    try {
      const portal = await parentPortalApi.get();
      const db = parentPortalDatabase(portal);
      if (generation.current !== request) return;
      setSnapshot({ portal, db }); setError("");
      setChild(current => db.students.some(s => s.id === current) ? current : db.students[0]?.id ?? "");
    } catch (reason) {
      if (generation.current !== request) return;
      setSnapshot(null); setError((reason as Error).message);
    }
  }, []);
  useEffect(() => {
    void reload();
    const refresh = () => { if (document.visibilityState === "visible") void reload(); };
    const timer = window.setInterval(refresh, 15000);
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      generation.current++; window.clearInterval(timer);
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [reload]);
  if (error) return <Result status="warning" title="Không thể tải hồ sơ của con" subTitle={error}
    extra={<><Button type="primary" onClick={() => void reload()}>Thử lại</Button><Button onClick={() => void logout()}>Đăng xuất</Button></>} />;
  if (!snapshot) return <div className="loading-screen"><Spin size="large" /><p>Đang tải hồ sơ của con…</p></div>;
  const { db, portal } = snapshot;
  return <DemoContext.Provider value={{ db, view: visibleData(db, "PARENT", child, portal.guardian?.id),
    role: "PARENT", child, setChild: id => { if (db.students.some(student => student.id === id)) setChild(id); },
    run: () => { message.error("Tài khoản phụ huynh chỉ được xem thông tin."); return false; },
  }}>{children}</DemoContext.Provider>;
}

function LocalDataProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [db, setDb] = useState<Database | null>(null),
    [error, setError] = useState(""),
    [child, setChild] = useState("HS001");
  const role: Role = user?.role ?? "ADMIN";
  const { message } = App.useApp();
  useEffect(() => {
    try {
      setDb(loadDatabase());
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);
  useEffect(() => {
    const linked = db?.parents.find((p) => p.id === "PH001")?.studentIds ?? [];
    if (db && !linked.includes(child)) setChild(linked[0] ?? "");
  }, [db, child]);
  function run(
    action: (db: Database) => Database,
    success = "Đã lưu thay đổi",
  ) {
    try {
      const next = action(db!);
      persistDatabase(next);
      setDb(next);
      message.success(success);
      return true;
    } catch (e) {
      message.error((e as Error).message);
      return false;
    }
  }
  if (error)
    return (
      <Result
        status="warning"
        title={error}
        extra={
          <Button
            type="primary"
            onClick={() => {
              localStorage.removeItem("duongan.demo.v1");
              window.location.reload();
            }}
          >Xóa dữ liệu lưu trong trình duyệt</Button>
        }
      />
    );
  if (!db)
    return (
      <div className="loading-screen">
        <Spin size="large" />
        <p>Đang tải trung tâm Dương An…</p>
      </div>
    );
  return (
    <DemoContext.Provider
      value={{
        db,
        view: mockService.view(db, role, role === "PARENT" ? child : undefined),
        role,
        child,
        setChild,
        run,
      }}
    >
      {children}
    </DemoContext.Provider>
  );
}
export function useDemo() {
  const c = useContext(DemoContext);
  if (!c) throw Error("DemoProvider is required");
  return c;
}
