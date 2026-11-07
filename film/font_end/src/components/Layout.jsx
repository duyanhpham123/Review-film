import { useEffect, useRef, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthContext';
import { meApi } from '../api/endpoints';
import { Avatar } from './ui';

function BellIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.7 21a2 2 0 0 1-3.4 0" />
    </svg>
  );
}

function NotificationBell() {
  // Polling 60 giây cho badge chuông (SSE để sau)
  const { data } = useQuery({ queryKey: ['unread-count'], queryFn: meApi.unreadCount, refetchInterval: 60_000 });
  const count = data?.count || 0;
  return (
    <Link to="/thong-bao" className="bell" aria-label={`Thông báo${count ? `, ${count} chưa đọc` : ''}`}>
      <BellIcon />
      {count > 0 && <span className="badge">{count > 99 ? '99+' : count}</span>}
    </Link>
  );
}

function UserMenu() {
  const { user, logout, isModerator } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const navigate = useNavigate();
  useEffect(() => {
    const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('click', close);
    return () => document.removeEventListener('click', close);
  }, []);
  return (
    <div className="menu" ref={ref}>
      <button className="btn ghost" onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-haspopup="menu">
        <Avatar src={user.avatarUrl} name={user.fullName || user.username} size={30} />
        <span className="hide-mobile">{user.username}</span>
      </button>
      {open && (
        <div className="menu-panel" role="menu" onClick={() => setOpen(false)}>
          <Link to={`/u/${user.username}`}>Trang cá nhân</Link>
          <Link to="/tai-khoan/watchlist">Watchlist</Link>
          <Link to="/tai-khoan/yeu-thich">Yêu thích</Link>
          <Link to="/tai-khoan">Cài đặt tài khoản</Link>
          {isModerator && <Link to="/admin">Quản trị</Link>}
          <button onClick={async () => { await logout(); navigate('/'); }}>Đăng xuất</button>
        </div>
      )}
    </div>
  );
}

export default function Layout() {
  const { user, loading } = useAuth();
  const [keyword, setKeyword] = useState('');
  const [navOpen, setNavOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  useEffect(() => { setNavOpen(false); window.scrollTo(0, 0); }, [location.pathname]);

  const submitSearch = (e) => {
    e.preventDefault();
    const kw = keyword.trim();
    navigate(kw ? `/phim?keyword=${encodeURIComponent(kw)}` : '/phim');
  };

  return (
    <>
      <header className="header">
        <div className="container header-inner">
          <button className="btn ghost menu-toggle" onClick={() => setNavOpen((o) => !o)} aria-label="Mở menu">☰</button>
          <Link to="/" className="logo">Review<span>Film</span></Link>
          <nav className={`nav ${navOpen ? 'open' : ''}`}>
            {/* NavLink bỏ qua query string nên tự tính mục đang chọn */}
            {[
              ['/phim', 'Phim', location.pathname === '/phim' && !location.search.match(/type=SERIES|status=UPCOMING/)],
              ['/phim?type=SERIES', 'Phim bộ', location.pathname === '/phim' && location.search.includes('type=SERIES')],
              ['/phim?status=UPCOMING', 'Sắp chiếu', location.pathname === '/phim' && location.search.includes('status=UPCOMING')],
              ['/nguoi', 'Diễn viên', location.pathname.startsWith('/nguoi')],
            ].map(([to, label, active]) => (
              <Link key={to} to={to} className={active ? 'active' : ''} aria-current={active ? 'page' : undefined}>{label}</Link>
            ))}
          </nav>
          <form className="header-search" onSubmit={submitSearch} role="search">
            <input type="search" placeholder="Tìm phim..." value={keyword} onChange={(e) => setKeyword(e.target.value)} aria-label="Tìm phim" />
          </form>
          <div className="header-actions">
            {loading ? null : user ? (
              <>
                <NotificationBell />
                <UserMenu />
              </>
            ) : (
              <>
                <Link to="/dang-nhap" className="btn ghost">Đăng nhập</Link>
                <Link to="/dang-ky" className="btn primary hide-mobile">Đăng ký</Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main className="page">
        <div className="container">
          <Outlet />
        </div>
      </main>
      <footer className="footer">
        <div className="container row between">
          <span>© {new Date().getFullYear()} ReviewFilm. Dữ liệu chỉ phục vụ mục đích học tập.</span>
          <span>Spring Boot · React</span>
        </div>
      </footer>
    </>
  );
}
