import React from "react";
import ReactDOM from "react-dom/client";
import { HashRouter } from "react-router-dom";
import { ConfigProvider, App as AntApp } from "antd";
import viVN from "antd/locale/vi_VN";
import "@fontsource/be-vietnam-pro/vietnamese-400.css";
import "@fontsource/be-vietnam-pro/vietnamese-500.css";
import "@fontsource/be-vietnam-pro/vietnamese-600.css";
import "@fontsource/be-vietnam-pro/vietnamese-700.css";
import "@fontsource/be-vietnam-pro/latin-400.css";
import "@fontsource/be-vietnam-pro/latin-500.css";
import "@fontsource/be-vietnam-pro/latin-600.css";
import "@fontsource/be-vietnam-pro/latin-700.css";
import { DemoProvider } from "./hooks/useDemo";
import { AppRoutes } from "./routes/AppRoutes";
import "./styles.css";
class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { error: boolean }
> {
  state = { error: false };
  static getDerivedStateFromError() {
    return { error: true };
  }
  render() {
    return this.state.error ? (
      <div className="loading-screen">
        <h1>Không thể hiển thị trang</h1>
        <p>Vui lòng tải lại để thử lại.</p>
        <button onClick={() => location.reload()}>Tải lại trang</button>
      </div>
    ) : (
      this.props.children
    );
  }
}
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={viVN}
      theme={{
        token: {
          colorPrimary: "#2563eb",
          colorText: "#1e293b",
          colorTextSecondary: "#64748b",
          borderRadius: 9,
          fontFamily: '"Be Vietnam Pro", sans-serif',
          fontSize: 13,
          controlHeight: 38,
        },
        components: {
          Button: { primaryShadow: "0 3px 7px #2563eb15" },
          Select: { optionSelectedBg: "#eff5ff" },
        },
      }}
    >
      <AntApp>
        <ErrorBoundary>
          <DemoProvider>
            <HashRouter>
              <AppRoutes />
            </HashRouter>
          </DemoProvider>
        </ErrorBoundary>
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>,
);
