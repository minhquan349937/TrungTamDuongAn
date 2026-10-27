import { useState } from "react";
import { Alert, Button, Input } from "antd";
import { KeyRound, LockKeyhole, Mail, Sprout } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import "./login.css";

export function LoginPage() {
  const { login, apiAvailable } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      navigate("/dashboard", { replace: true });
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-page">
      <div className="login-glow login-glow-one" />
      <div className="login-glow login-glow-two" />
      <section className="login-card">
        <div className="login-brand">
          <span className="login-brand-icon"><Sprout size={25} /></span>
          <span>Dương<span>An</span><small>ĐỒNG HÀNH PHÁT TRIỂN</small></span>
        </div>
        <div className="login-heading">
          <div className="login-eyebrow">KHÔNG GIAN LÀM VIỆC</div>
          <h1>Chào mừng bạn trở lại</h1>
          <p>Đăng nhập để tiếp tục đồng hành cùng trẻ.</p>
        </div>
        {!apiAvailable && (
          <Alert
            type="warning"
            showIcon
            message="Chưa kết nối được backend"
            description="Hãy khởi động Spring Boot và MySQL Docker, sau đó tải lại trang."
          />
        )}
        {error && <Alert className="login-error" type="error" showIcon message={error} />}
        <form className="login-form" onSubmit={submit}>
          <label htmlFor="login-email">Email</label>
          <Input
            id="login-email"
            size="large"
            autoComplete="username"
            prefix={<Mail size={17} />}
            placeholder="name@example.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
          <div className="login-password-label">
            <label htmlFor="login-password">Mật khẩu</label>
          </div>
          <Input.Password
            id="login-password"
            size="large"
            autoComplete="current-password"
            prefix={<KeyRound size={17} />}
            placeholder="Nhập mật khẩu"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
          <Button
            className="login-submit"
            type="primary"
            size="large"
            htmlType="submit"
            loading={submitting}
            disabled={!apiAvailable}
            icon={<LockKeyhole size={17} />}
          >
            Đăng nhập
          </Button>
        </form>
        <div className="login-role-hint">
          <b>Ba không gian, một tài khoản riêng</b>
          <div><span>Quản trị viên</span><i /><span>Giáo viên</span><i /><span>Phụ huynh</span></div>
        </div>
      </section>
      <p className="login-footer">© {new Date().getFullYear()} Trung tâm Dương An · Cùng con lớn lên mỗi ngày</p>
    </main>
  );
}
