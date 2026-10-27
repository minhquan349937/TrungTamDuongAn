import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { App, Spin, Button, Result } from "antd";
import type { Database, Role } from "../types";
import {
  loadDatabase,
  persistDatabase,
  mockService,
} from "../services/mockService";
import { createSeed } from "../mocks/seed";
import { useAuth } from "../auth/AuthProvider";
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
