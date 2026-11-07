import { useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import { QueryState } from '../../components/ui';

function isoDate(d) {
  return d.toISOString().slice(0, 10);
}

/** Biểu đồ cột đơn giản bằng CSS, không cần thư viện. */
function BarChart({ points, label }) {
  const max = Math.max(1, ...points.map((p) => p.value));
  const total = points.reduce((s, p) => s + p.value, 0);
  return (
    <div className="card">
      <div className="row between"><strong>{label}</strong><span className="muted small">Tổng {total.toLocaleString('vi-VN')}</span></div>
      <div className="chart" role="img" aria-label={`${label}: tổng ${total}`}>
        {points.map((p) => (
          <div key={p.date} className="col" style={{ height: `${(p.value / max) * 100}%` }} title={`${p.date}: ${p.value}`} />
        ))}
      </div>
      <div className="chart-axis"><span>{points[0]?.date}</span><span>{points[points.length - 1]?.date}</span></div>
    </div>
  );
}

export default function Dashboard() {
  const { isAdmin } = useAuth();
  const [days, setDays] = useState(30);
  const range = { from: isoDate(new Date(Date.now() - (days - 1) * 86400_000)), to: isoDate(new Date()) };
  const summary = useQuery({ queryKey: ['admin', 'summary'], queryFn: adminApi.summary, enabled: isAdmin });
  const views = useQuery({ queryKey: ['admin', 'views', range], queryFn: () => adminApi.views(range), enabled: isAdmin });
  const users = useQuery({ queryKey: ['admin', 'new-users', range], queryFn: () => adminApi.newUsers(range), enabled: isAdmin });
  const reviews = useQuery({ queryKey: ['admin', 'reviews-daily', range], queryFn: () => adminApi.reviewsDaily(range), enabled: isAdmin });
  const top = useQuery({ queryKey: ['admin', 'top', range], queryFn: () => adminApi.topMovies({ ...range, by: 'views', limit: 10 }), enabled: isAdmin });

  // Moderator không xem được dashboard: chuyển sang trang báo cáo
  if (!isAdmin) return <Navigate to="/admin/bao-cao" replace />;

  return (
    <div className="stack" style={{ gap: 20 }}>
      <div className="row between">
        <h1>Tổng quan</h1>
        <select value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label="Khoảng thời gian">
          <option value={7}>7 ngày</option>
          <option value={30}>30 ngày</option>
          <option value={90}>90 ngày</option>
        </select>
      </div>
      <QueryState query={summary}>
        {(s) => (
          <div className="stats">
            {[
              ['Phim', s.totalMovies], ['Người dùng', s.totalUsers], ['Review', s.totalReviews], ['Lượt chấm', s.totalRatings],
              ['Lượt xem hôm nay', s.viewsToday], ['User mới hôm nay', s.newUsersToday], ['Review mới hôm nay', s.newReviewsToday],
            ].map(([label, value]) => (
              <div className="card stat" key={label}><div className="value">{value.toLocaleString('vi-VN')}</div><div className="label">{label}</div></div>
            ))}
            <Link to="/admin/bao-cao" className="card stat"><div className="value" style={{ color: s.pendingReports ? 'var(--danger)' : undefined }}>{s.pendingReports}</div><div className="label">Báo cáo chờ xử lý ›</div></Link>
          </div>
        )}
      </QueryState>
      <div className="form-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))' }}>
        <QueryState query={views}>{(d) => <BarChart points={d} label="Lượt xem theo ngày" />}</QueryState>
        <QueryState query={users}>{(d) => <BarChart points={d} label="Người dùng mới" />}</QueryState>
        <QueryState query={reviews}>{(d) => <BarChart points={d} label="Review mới" />}</QueryState>
      </div>
      <div className="card">
        <h2>Top phim xem nhiều</h2>
        <QueryState query={top} isEmpty={(d) => d.length === 0} empty="Chưa có lượt xem trong khoảng này.">
          {(d) => (
            <div className="table-wrap">
              <table>
                <thead><tr><th>#</th><th>Phim</th><th>Lượt xem</th></tr></thead>
                <tbody>
                  {d.map((m, i) => (
                    <tr key={m.id}><td>{i + 1}</td><td><Link to={`/phim/${m.slug}`}>{m.title}</Link></td><td>{m.value.toLocaleString('vi-VN')}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryState>
      </div>
    </div>
  );
}
