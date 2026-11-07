import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Spinner } from './ui';

/** Trang cần đăng nhập: chưa đăng nhập thì chuyển tới trang đăng nhập rồi quay lại. */
export function RequireAuth({ children }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Spinner />;
  if (!user) return <Navigate to={`/dang-nhap?next=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  return children;
}

export function RequireModerator({ children }) {
  const { user, loading, isModerator } = useAuth();
  if (loading) return <Spinner />;
  if (!user) return <Navigate to="/dang-nhap?next=/admin" replace />;
  if (!isModerator) return <div className="empty">Bạn không có quyền truy cập trang này.</div>;
  return children;
}

export function RequireAdmin({ children }) {
  const { isAdmin } = useAuth();
  if (!isAdmin) return <div className="empty">Chỉ quản trị viên mới truy cập được trang này.</div>;
  return children;
}
