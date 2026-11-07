import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';

export default function AdminLayout() {
  const { isAdmin } = useAuth();
  return (
    <div className="admin">
      <title>Quản trị · ReviewFilm</title>
      <nav className="admin-nav" aria-label="Quản trị">
        {isAdmin && <NavLink to="/admin" end>Tổng quan</NavLink>}
        {isAdmin && <NavLink to="/admin/phim">Phim</NavLink>}
        {isAdmin && <NavLink to="/admin/nguoi">Đạo diễn, diễn viên</NavLink>}
        {isAdmin && <NavLink to="/admin/the-loai">Thể loại</NavLink>}
        {isAdmin && <NavLink to="/admin/nguoi-dung">Người dùng</NavLink>}
        <NavLink to="/admin/bao-cao">Báo cáo vi phạm</NavLink>
        {isAdmin && <NavLink to="/admin/audit">Audit log</NavLink>}
      </nav>
      <div style={{ minWidth: 0 }}>
        <Outlet />
      </div>
    </div>
  );
}
