import { Link } from 'react-router-dom';
import { errorMessage } from '../api/client';

export function Spinner() {
  return <div className="spinner" role="status" aria-label="Đang tải" />;
}

export function ErrorBox({ error, message }) {
  if (!error && !message) return null;
  return <div className="alert error">{message || errorMessage(error)}</div>;
}

export function Empty({ children }) {
  return <div className="empty">{children}</div>;
}

/** Hiển thị loading / lỗi / nội dung cho một query của TanStack Query. */
export function QueryState({ query, children, empty, isEmpty }) {
  if (query.isPending) return <Spinner />;
  if (query.isError) {
    if (query.error?.response?.status === 404) return <Empty>Không tìm thấy nội dung.</Empty>;
    return <ErrorBox error={query.error} />;
  }
  if (isEmpty?.(query.data)) return <Empty>{empty || 'Chưa có dữ liệu.'}</Empty>;
  return children(query.data);
}

export function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  const pages = [];
  const start = Math.max(0, page - 2);
  const end = Math.min(totalPages - 1, page + 2);
  for (let i = start; i <= end; i++) pages.push(i);
  return (
    <nav className="pagination" aria-label="Phân trang">
      <button className="btn small" disabled={page === 0} onClick={() => onChange(page - 1)}>‹ Trước</button>
      {start > 0 && <span className="muted">…</span>}
      {pages.map((p) => (
        <button key={p} className={`btn small ${p === page ? 'primary' : ''}`} onClick={() => onChange(p)}>{p + 1}</button>
      ))}
      {end < totalPages - 1 && <span className="muted">…</span>}
      <button className="btn small" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>Sau ›</button>
    </nav>
  );
}

export function Avatar({ src, name, size = 36 }) {
  const initial = (name || '?').trim().charAt(0).toUpperCase();
  const style = { width: size, height: size, fontSize: size * 0.42 };
  return src
    ? <img className="avatar" src={src} alt={name || ''} style={style} loading="lazy" />
    : <span className="avatar" style={style} aria-hidden="true">{initial}</span>;
}

/** Tên người dùng kèm link trang cá nhân; người dùng đã xóa không có link. */
export function UserLink({ user }) {
  if (!user) return <span className="muted">Ẩn danh</span>;
  if (!user.id) return <span className="muted">{user.fullName}</span>;
  return <Link to={`/u/${user.username}`}><strong>{user.fullName || user.username}</strong></Link>;
}

export function formatDate(value) {
  if (!value) return '';
  const d = new Date(value.length === 10 ? `${value}T00:00:00` : `${value}${value.endsWith('Z') ? '' : 'Z'}`);
  return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export function timeAgo(value) {
  if (!value) return '';
  const d = new Date(value.endsWith('Z') ? value : `${value}Z`);
  const s = Math.floor((Date.now() - d.getTime()) / 1000);
  if (s < 60) return 'vừa xong';
  if (s < 3600) return `${Math.floor(s / 60)} phút trước`;
  if (s < 86400) return `${Math.floor(s / 3600)} giờ trước`;
  if (s < 86400 * 30) return `${Math.floor(s / 86400)} ngày trước`;
  return formatDate(value);
}

export function scoreText(value) {
  const n = Number(value || 0);
  return n ? n.toFixed(1) : '–';
}

export const MOVIE_STATUS = { UPCOMING: 'Sắp chiếu', ONGOING: 'Đang chiếu', COMPLETED: 'Đã phát hành' };
export const MOVIE_TYPE = { MOVIE: 'Phim lẻ', SERIES: 'Phim bộ' };
export const WATCH_STATUS = { WANT_TO_WATCH: 'Muốn xem', WATCHING: 'Đang xem', WATCHED: 'Đã xem' };
export const REPORT_REASON = { SPAM: 'Spam', OFFENSIVE: 'Xúc phạm', SPOILER: 'Tiết lộ nội dung', OTHER: 'Khác' };
