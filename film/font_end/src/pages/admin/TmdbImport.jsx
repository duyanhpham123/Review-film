import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { Spinner } from '../../components/ui';

/** Hộp thoại tìm phim trên TMDB và import. Import xong mở trang sửa phim để kiểm tra rồi hiện phim. */
export default function TmdbImport({ onClose }) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [type, setType] = useState('movie');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState(null);
  const [state, setState] = useState({ searching: false, importing: null, error: '' });

  const search = async (e) => {
    e.preventDefault();
    const q = query.trim();
    if (!q) return;
    setState({ searching: true, importing: null, error: '' });
    try {
      // Nhập thẳng số thì coi là TMDB ID
      if (/^\d+$/.test(q)) {
        setResults([{ tmdbId: Number(q), type, title: `TMDB ID ${q}`, year: null, posterUrl: null, overview: 'Import trực tiếp theo ID' }]);
      } else {
        setResults(await adminApi.searchTmdb(q, type));
      }
      setState({ searching: false, importing: null, error: '' });
    } catch (err) {
      setState({ searching: false, importing: null, error: errorMessage(err) });
    }
  };

  const doImport = async (r) => {
    setState({ searching: false, importing: r.tmdbId, error: '' });
    try {
      const movie = await adminApi.importTmdb(r.tmdbId, r.type);
      qc.invalidateQueries({ queryKey: ['admin', 'movies'] });
      onClose();
      navigate(`/admin/phim/${movie.id}`);
    } catch (err) {
      setState({ searching: false, importing: null, error: errorMessage(err) });
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="card modal stack" style={{ maxWidth: 680 }} onClick={(e) => e.stopPropagation()}>
        <div className="row between">
          <h2 style={{ margin: 0 }}>Import từ TMDB</h2>
          <button className="btn ghost small" onClick={onClose} aria-label="Đóng">✕</button>
        </div>
        <form className="row" onSubmit={search}>
          <select value={type} onChange={(e) => { setType(e.target.value); setResults(null); }} aria-label="Loại">
            <option value="movie">Phim lẻ</option>
            <option value="tv">Phim bộ</option>
          </select>
          <input type="search" className="grow" placeholder="Tên phim hoặc TMDB ID..." value={query}
            onChange={(e) => setQuery(e.target.value)} autoFocus aria-label="Tên phim hoặc TMDB ID" />
          <button className="btn primary" disabled={state.searching}>Tìm</button>
        </form>
        <p className="muted small" style={{ margin: 0 }}>
          Phim được tạo ở trạng thái ẩn kèm thể loại, quốc gia, đạo diễn, diễn viên{type === 'tv' ? ', danh sách tập' : ''}. Kiểm tra xong thì bấm "Hiện phim".
        </p>
        {state.error && <div className="alert error">{state.error}</div>}
        {state.searching && <Spinner />}
        {results && results.length === 0 && <div className="empty">Không tìm thấy kết quả.</div>}
        {results && results.length > 0 && (
          <div className="stack" style={{ gap: 8, maxHeight: 420, overflow: 'auto' }}>
            {results.map((r) => (
              <div key={`${r.type}-${r.tmdbId}`} className="row" style={{ alignItems: 'flex-start', flexWrap: 'nowrap' }}>
                {r.posterUrl ? <img className="thumb" src={r.posterUrl} alt="" loading="lazy" /> : <div className="thumb" />}
                <div className="grow" style={{ minWidth: 0 }}>
                  <strong>{r.title}</strong> {r.year && <span className="muted">({r.year})</span>}
                  {r.originalTitle && r.originalTitle !== r.title && <div className="muted small">{r.originalTitle}</div>}
                  {r.overview && <div className="muted small clamp" style={{ WebkitLineClamp: 2 }}>{r.overview}</div>}
                </div>
                <button className="btn small primary" disabled={state.importing !== null} onClick={() => doImport(r)}>
                  {state.importing === r.tmdbId ? 'Đang import...' : 'Import'}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
