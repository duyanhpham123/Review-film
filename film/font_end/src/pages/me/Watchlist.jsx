import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { meApi } from '../../api/endpoints';
import { Poster } from '../../components/MovieCard';
import { Pagination, QueryState, WATCH_STATUS, formatDate } from '../../components/ui';

export default function Watchlist() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ['watchlist', status, page], queryFn: () => meApi.watchlist(status, page), placeholderData: keepPreviousData });

  const change = async (movieId, next) => {
    if (next) await meApi.setWatchlist(movieId, { status: next });
    else await meApi.removeWatchlist(movieId);
    qc.invalidateQueries({ queryKey: ['watchlist'] });
    qc.invalidateQueries({ queryKey: ['movie-me', movieId] });
  };

  return (
    <>
      <title>Watchlist · ReviewFilm</title>
      <h1>Watchlist của tôi</h1>
      <div className="tabs">
        {[['', 'Tất cả'], ...Object.entries(WATCH_STATUS)].map(([k, v]) => (
          <button key={k} className={status === k ? 'active' : ''} onClick={() => { setStatus(k); setPage(0); }}>{v}</button>
        ))}
      </div>
      <QueryState query={list} isEmpty={(d) => d.content.length === 0} empty={<>Danh sách trống. <Link to="/phim" className="link-btn">Tìm phim</Link></>}>
        {(data) => (
          <>
            <div className="movie-grid">
              {data.content.map((item) => (
                <div key={item.movie.id} className="movie-card">
                  <Link to={`/phim/${item.movie.slug}`}><Poster src={item.movie.posterUrl} title={item.movie.title} /></Link>
                  <Link to={`/phim/${item.movie.slug}`} className="title">{item.movie.title}</Link>
                  {item.watchedAt && <span className="meta">Xem ngày {formatDate(item.watchedAt)}</span>}
                  <select value={item.status} onChange={(e) => change(item.movie.id, e.target.value)} aria-label="Đổi trạng thái">
                    {Object.entries(WATCH_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                    <option value="">Bỏ khỏi danh sách</option>
                  </select>
                </div>
              ))}
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
          </>
        )}
      </QueryState>
    </>
  );
}
