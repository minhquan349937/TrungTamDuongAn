import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import type { Role } from "../types";

export type AuthUser = {
  id: string;
  email: string;
  displayName: string;
  role: Role;
};

type AuthContextValue = {
  user: AuthUser | null;
  loading: boolean;
  apiAvailable: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

async function readCsrfToken() {
  const response = await fetch("/api/auth/csrf", { credentials: "include" });
  if (!response.ok) throw new Error("Không thể kết nối máy chủ đăng nhập.");
  const body = (await response.json()) as { token: string };
  return body.token;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [apiAvailable, setApiAvailable] = useState(true);
  const [csrfToken, setCsrfToken] = useState("");

  const refreshCsrf = useCallback(async () => {
    const token = await readCsrfToken();
    setCsrfToken(token);
    return token;
  }, []);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const token = await readCsrfToken();
        const response = await fetch("/api/auth/me", { credentials: "include" });
        if (response.ok) {
          const account = (await response.json()) as AuthUser;
          if (active) setUser(account);
        } else if (response.status !== 401) {
          throw new Error("Không thể tải phiên đăng nhập.");
        }
        if (active) {
          setCsrfToken(token);
          setApiAvailable(true);
        }
      } catch {
        if (active) setApiAvailable(false);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const token = csrfToken || (await refreshCsrf());
    const response = await fetch("/api/auth/login", {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": token,
      },
      body: JSON.stringify({ email, password }),
    });
    const body = (await response.json().catch(() => ({}))) as
      | AuthUser
      | { message?: string };
    if (!response.ok) {
      throw new Error(
        "message" in body && body.message
          ? body.message
          : "Đăng nhập chưa thành công. Vui lòng thử lại.",
      );
    }
    setUser(body as AuthUser);
    setApiAvailable(true);
    await refreshCsrf();
  }, [csrfToken, refreshCsrf]);

  const logout = useCallback(async () => {
    try {
      const token = csrfToken || (await refreshCsrf());
      await fetch("/api/auth/logout", {
        method: "POST",
        credentials: "include",
        headers: { "X-CSRF-TOKEN": token },
      });
    } finally {
      setUser(null);
      setCsrfToken("");
    }
  }, [csrfToken, refreshCsrf]);

  const value = useMemo(
    () => ({ user, loading, apiAvailable, login, logout }),
    [user, loading, apiAvailable, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("AuthProvider is required");
  return context;
}
