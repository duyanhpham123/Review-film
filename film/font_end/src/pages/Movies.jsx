import { useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { catalogApi, movieApi } from '../api/endpoints';
import { MovieGrid } from '../components/MovieCard';
import { MOVIE_STATUS, MOVIE_TYPE, Pagination, QueryState } from '../components/ui';

const SORTS = { latest: 'Mới thêm', release: 'Ngày phát hành', rating: 'Điểm cao', views: 'Xem nhiều', reviews: 'Nhiều review', title: 'Tên A-Z' };

/** Danh sách + tìm kiếm + lọc. Bộ lọc nằm trên URL để chia sẻ link và bấm Back được. */
export default function Movies() {
  const [params, setParams] = useSearchParams();
  const filter = {
    keyword: params.get('keyword') || '',
    genre: params.get('genre') || '',
    country: params.get('country') || '',
    year: params.get('year') || '',
    type: params.get('type') || '',
    status: params.get('status') || '',
    sort: params.get('sort') || 'latest',
    page: Number(params.get('page') || 0),
    size: 24,
  };
  const genres = useQuery({ queryKey: ['genres'], queryFn: catalogApi.genres, staleTime: 3600_000 });
  const countries = useQuery({ queryKey: ['countries'], queryFn: catalogApi.countries, staleTime: 3600_000 });
  const movies = useQuery({ queryKey: ['movies', filter], queryFn: () => movieApi.list(filter), placeholderData: keepPreviousData });

  const update = (key, value) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next);
  };

  const thisYear = new Date().getFullYear();
  const years = Array.from({ length: 50 }, (_, i) => thisYear + 1 - i);

  return (
    <>
      <title>{filter.keyword ? `Tìm "${filter.keyword}"` : 'Danh sách phim'} · ReviewFilm</title>
      <div className="section-head">
        <h1>{filter.keyword ? `Kết quả cho "${filter.keyword}"` : 'Danh sách phim'}</h1>
        {movies.data && <span className="muted">{movies.data.totalElements} phim</span>}
      </div>

      <form className="row card" style={{ padding: 12 }} onSubmit={(e) => { e.preventDefault(); update('keyword', new FormData(e.currentTarget).get('keyword').trim()); }}>
        <input type="search" name="keyword" defaultValue={filter.keyword} key={filter.keyword} placeholder="Tên phim..." className="grow" aria-label="Từ khóa" />
        <select value={filter.genre} onChange={(e) => update('genre', e.target.value)} aria-label="Thể loại">
          <option value="">Mọi thể loại</option>
          {(genres.data || []).map((g) => <option key={g.id} value={g.slug}>{g.name}</option>)}
        </select>
        <select value={filter.country} onChange={(e) => update('country', e.target.value)} aria-label="Quốc gia">
          <option value="">Mọi quốc gia</option>
          {(countries.data || []).map((c) => <option key={c.id} value={c.code}>{c.name}</option>)}
        </select>
        <select value={filter.year} onChange={(e) => update('year', e.target.value)} aria-label="Năm">
          <option value="">Mọi năm</option>
          {years.map((y) => <option key={y} value={y}>{y}</option>)}
        </select>
        <select value={filter.type} onChange={(e) => update('type', e.target.value)} aria-label="Loại phim">
          <option value="">Lẻ + bộ</option>
          {Object.entries(MOVIE_TYPE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select value={filter.status} onChange={(e) => update('status', e.target.value)} aria-label="Trạng thái">
          <option value="">Mọi trạng thái</option>
          {Object.entries(MOVIE_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select value={filter.sort} onChange={(e) => update('sort', e.target.value)} aria-label="Sắp xếp">
          {Object.entries(SORTS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <button className="btn primary" type="submit">Tìm</button>
      </form>

      <div className="section" style={{ marginTop: 20 }}>
        <QueryState query={movies} isEmpty={(d) => d.content.length === 0} empty="Không có phim phù hợp.">
          {(data) => (
            <>
              <MovieGrid movies={data.content} />
              <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => update('page', String(p))} />
            </>
          )}
        </QueryState>
      </div>
    </>
  );
}
