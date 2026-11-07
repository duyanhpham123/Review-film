import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../api/endpoints';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Spinner } from '../components/ui';

/** Chỉ cho quay lại đường dẫn nội bộ, tránh open redirect qua ?next=. */
function safeNext(value) {
  return value && value.startsWith('/') && !value.startsWith('//') ? value : '/';
}

function AuthCard({ title, children }) {
  return (
    <div className="card auth-card">
      <title>{`${title} · ReviewFilm`}</title>
      <h1 style={{ fontSize: '1.5rem' }}>{title}</h1>
      {children}
    </div>
  );
}

export function Login() {
  const { user, login } = useAuth();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [form, setForm] = useState({ login: '', password: '', rememberMe: true });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const next = safeNext(params.get('next'));
  if (user) return <Navigate to={next} replace />;

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await login(form.login.trim(), form.password, form.rememberMe);
      navigate(next, { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthCard title="Đăng nhập">
      <form className="form" onSubmit={submit}>
        <label className="field">Username hoặc email
          <input type="text" autoComplete="username" value={form.login} onChange={(e) => setForm({ ...form, login: e.target.value })} required />
        </label>
        <label className="field">Mật khẩu
          <input type="password" autoComplete="current-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
        </label>
        <div className="row between">
          <label className="check"><input type="checkbox" checked={form.rememberMe} onChange={(e) => setForm({ ...form, rememberMe: e.target.checked })} /> Ghi nhớ đăng nhập</label>
          <Link to="/quen-mat-khau" className="link-btn">Quên mật khẩu?</Link>
        </div>
        {error && <div className="alert error">{error}</div>}
        <button className="btn primary" disabled={busy}>Đăng nhập</button>
        <p className="muted small">Chưa có tài khoản? <Link to="/dang-ky" className="link-btn">Đăng ký</Link></p>
      </form>
    </AuthCard>
  );
}

export function Register() {
  const { user } = useAuth();
  const [form, setForm] = useState({ username: '', email: '', password: '', confirm: '', fullName: '' });
  const [error, setError] = useState('');
  const [done, setDone] = useState(null);
  const [busy, setBusy] = useState(false);
  if (user) return <Navigate to="/" replace />;
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    if (form.password !== form.confirm) { setError('Mật khẩu nhập lại không khớp'); return; }
    setBusy(true);
    setError('');
    try {
      const res = await authApi.register({ username: form.username.trim(), email: form.email.trim(), password: form.password, fullName: form.fullName.trim() || null });
      setDone(res);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return (
      <AuthCard title="Đăng ký thành công">
        <div className="alert success">Chúng tôi đã gửi link xác thực tới <strong>{done.email}</strong>. Hãy mở email và bấm vào link để kích hoạt việc viết review, bình luận.</div>
        <p style={{ marginTop: 14 }}><Link to="/dang-nhap" className="btn primary">Đăng nhập</Link></p>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Tạo tài khoản">
      <form className="form" onSubmit={submit}>
        <label className="field">Username (3-50 ký tự: chữ, số, dấu _)
          <input type="text" autoComplete="username" value={form.username} onChange={set('username')} pattern="[A-Za-z0-9_]{3,50}" required />
        </label>
        <label className="field">Email
          <input type="email" autoComplete="email" value={form.email} onChange={set('email')} required />
        </label>
        <label className="field">Họ tên (không bắt buộc)
          <input type="text" autoComplete="name" value={form.fullName} onChange={set('fullName')} maxLength={100} />
        </label>
        <label className="field">Mật khẩu (8-72 ký tự)
          <input type="password" autoComplete="new-password" value={form.password} onChange={set('password')} minLength={8} maxLength={72} required />
        </label>
        <label className="field">Nhập lại mật khẩu
          <input type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} required />
        </label>
        {error && <div className="alert error">{error}</div>}
        <button className="btn primary" disabled={busy}>Đăng ký</button>
        <p className="muted small">Đã có tài khoản? <Link to="/dang-nhap" className="link-btn">Đăng nhập</Link></p>
      </form>
    </AuthCard>
  );
}

export function VerifyEmail() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const { user, reloadUser } = useAuth();
  const [state, setState] = useState({ status: token ? 'loading' : 'error', message: token ? '' : 'Link không hợp lệ.' });
  const sent = useRef(false);

  useEffect(() => {
    // StrictMode chạy effect 2 lần khi dev; token chỉ dùng được 1 lần nên chặn lần gửi thứ hai
    if (!token || sent.current) return;
    sent.current = true;
    authApi.verifyEmail(token)
      .then(() => { setState({ status: 'ok', message: '' }); if (user) reloadUser().catch(() => {}); })
      .catch((err) => setState({ status: 'error', message: errorMessage(err) }));
  }, [token]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <AuthCard title="Xác thực email">
      {state.status === 'loading' && <Spinner />}
      {state.status === 'ok' && <div className="alert success">Email đã được xác thực. Bạn có thể viết review và bình luận.</div>}
      {state.status === 'error' && <div className="alert error">{state.message} Bạn có thể gửi lại link trong trang Tài khoản.</div>}
      <p style={{ marginTop: 14 }}><Link to="/" className="btn">Về trang chủ</Link></p>
    </AuthCard>
  );
}

export function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [state, setState] = useState({ busy: false, msg: '', error: '' });
  const submit = async (e) => {
    e.preventDefault();
    setState({ busy: true, msg: '', error: '' });
    try {
      const res = await authApi.forgotPassword(email.trim());
      setState({ busy: false, msg: res.message, error: '' });
    } catch (err) {
      setState({ busy: false, msg: '', error: errorMessage(err) });
    }
  };
  return (
    <AuthCard title="Quên mật khẩu">
      <form className="form" onSubmit={submit}>
        <p className="muted">Nhập email đã đăng ký, chúng tôi sẽ gửi link đặt lại mật khẩu (hiệu lực 30 phút).</p>
        <label className="field">Email
          <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        {state.msg && <div className="alert success">{state.msg}</div>}
        {state.error && <div className="alert error">{state.error}</div>}
        <button className="btn primary" disabled={state.busy}>Gửi link</button>
      </form>
    </AuthCard>
  );
}

export function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const [form, setForm] = useState({ password: '', confirm: '' });
  const [state, setState] = useState({ busy: false, done: false, error: '' });
  const submit = async (e) => {
    e.preventDefault();
    if (form.password !== form.confirm) { setState({ ...state, error: 'Mật khẩu nhập lại không khớp' }); return; }
    setState({ busy: true, done: false, error: '' });
    try {
      await authApi.resetPassword(token, form.password);
      setState({ busy: false, done: true, error: '' });
    } catch (err) {
      setState({ busy: false, done: false, error: errorMessage(err) });
    }
  };
  if (state.done) {
    return (
      <AuthCard title="Đặt lại mật khẩu">
        <div className="alert success">Đã đổi mật khẩu. Mọi thiết bị đã bị đăng xuất, hãy đăng nhập lại.</div>
        <p style={{ marginTop: 14 }}><Link to="/dang-nhap" className="btn primary">Đăng nhập</Link></p>
      </AuthCard>
    );
  }
  return (
    <AuthCard title="Đặt lại mật khẩu">
      <form className="form" onSubmit={submit}>
        {!token && <div className="alert error">Link không hợp lệ.</div>}
        <label className="field">Mật khẩu mới (8-72 ký tự)
          <input type="password" autoComplete="new-password" minLength={8} maxLength={72} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
        </label>
        <label className="field">Nhập lại mật khẩu mới
          <input type="password" autoComplete="new-password" value={form.confirm} onChange={(e) => setForm({ ...form, confirm: e.target.value })} required />
        </label>
        {state.error && <div className="alert error">{state.error}</div>}
        <button className="btn primary" disabled={state.busy || !token}>Đổi mật khẩu</button>
      </form>
    </AuthCard>
  );
}
