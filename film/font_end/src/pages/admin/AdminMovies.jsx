import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { MOVIE_STATUS, MOVIE_TYPE, Pagination, QueryState, scoreText } from '../../components/ui';
import TmdbImport from './TmdbImport';

export default function AdminMovies() {
  const qc = useQueryClient();
  const [filter, setFilter] = useState({ keyword: '', type: '', status: '', page: 0 });
  const [keyword, setKeyword] = useState('');
  const [importing, setImporting] = useState(false);
  const movies = useQuery({ queryKey: ['admin', 'movies', filter], queryFn: () => adminApi.movies({ ...filter, size: 20 }), placeholderData: keepPreviousData });
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin', 'movies'] });

  const toggle = async (m) => {
    try { await adminApi.setVisibility(m.id, !m.hidden); refresh(); } catch (e) { alert(errorMessage(e)); }
  };
  const remove = async (m) => {
    if (!confirm(`Xóa phim "${m.title}"? (xóa mềm, có thể khôi phục trong DB)`)) return;
    try { await adminApi.deleteMovie(m.id); refresh(); } catch (e) { alert(errorMessage(e)); }
  };

  return (
    <div className="stack">
      <div className="row between">
        <h1>Phim</h1>
        <div className="row">
          <button className="btn" onClick={() => setImporting(true)}>⇣ Import từ TMDB</button>
          <Link to="/admin/phim/moi" className="btn primary">＋ Thêm phim</Link>
        </div>
      </div>
      {importing && <TmdbImport onClose={() => setImporting(false)} />}
      <form className="row" onSubmit={(e) => { e.preventDefault(); setFilter({ ...filter, keyword, page: 0 }); }}>
        <input type="search" placeholder="Tìm tên phim..." value={keyword} onChange={(e) => setKeyword(e.target.value)} className="grow" aria-label="Tên phim" />
        <select value={filter.type} onChange={(e) => setFilter({ ...filter, type: e.target.value, page: 0 })} aria-label="Loại">
          <option value="">Lẻ + bộ</option>
          {Object.entries(MOVIE_TYPE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select value={filter.status} onChange={(e) => setFilter({ ...filter, status: e.target.value, page: 0 })} aria-label="Trạng thái">
          <option value="">Mọi trạng thái</option>
          {Object.entries(MOVIE_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <button className="btn">Lọc</button>
      </form>
      <QueryState query={movies} isEmpty={(d) => d.content.length === 0} empty="Không có phim.">
        {(data) => (
          <>
            <div className="table-wrap">
              <table>
                <thead><tr><th></th><th>Tên</th><th>Loại</th><th>Trạng thái</th><th>Điểm</th><th>Review</th><th>Hiển thị</th><th></th></tr></thead>
                <tbody>
                  {data.content.map((m) => (
                    <tr key={m.id}>
                      <td>{m.posterUrl ? <img className="thumb" src={m.posterUrl} alt="" /> : <div className="thumb" />}</td>
                      <td><Link to={`/admin/phim/${m.id}`}><strong>{m.title}</strong></Link><div className="muted small">{m.releaseDate?.slice(0, 4)} · /{m.slug}</div></td>
                      <td>{MOVIE_TYPE[m.type]}</td>
                      <td>{MOVIE_STATUS[m.status]}</td>
                      <td>{scoreText(m.avgRating)} <span className="muted small">({m.ratingCount})</span></td>
                      <td>{m.reviewCount}</td>
                      <td><button className={`btn small ${m.hidden ? '' : 'active'}`} onClick={() => toggle(m)}>{m.hidden ? 'Đang ẩn' : 'Đang hiện'}</button></td>
                      <td className="row" style={{ flexWrap: 'nowrap' }}>
                        <Link to={`/admin/phim/${m.id}`} className="btn small">Sửa</Link>
                        <button className="btn small danger" onClick={() => remove(m)}>Xóa</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => setFilter({ ...filter, page: p })} />
          </>
        )}
      </QueryState>
    </div>
  );
}
