import { Link, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { catalogApi } from '../api/endpoints';
import { Pagination, QueryState } from '../components/ui';

export default function People() {
  const [params, setParams] = useSearchParams();
  const keyword = params.get('keyword') || '';
  const page = Number(params.get('page') || 0);
  const people = useQuery({ queryKey: ['people', keyword, page], queryFn: () => catalogApi.people(keyword, page, 30), placeholderData: keepPreviousData });

  return (
    <>
      <title>Đạo diễn, diễn viên · ReviewFilm</title>
      <div className="section-head"><h1>Đạo diễn, diễn viên</h1></div>
      <form className="row" onSubmit={(e) => { e.preventDefault(); const kw = new FormData(e.currentTarget).get('keyword').trim(); setParams(kw ? { keyword: kw } : {}); }}>
        <input type="search" name="keyword" defaultValue={keyword} placeholder="Tìm theo tên..." className="grow" aria-label="Tên" />
        <button className="btn primary">Tìm</button>
      </form>
      <div className="section" style={{ marginTop: 20 }}>
        <QueryState query={people} isEmpty={(d) => d.content.length === 0} empty="Không tìm thấy ai.">
          {(data) => (
            <>
              <div className="movie-grid">
                {data.content.map((p) => (
                  <Link key={p.id} to={`/nguoi/${p.slug}`} className="movie-card">
                    <div className="poster">
                      {p.avatarUrl ? <img src={p.avatarUrl} alt={p.name} loading="lazy" /> : <div className="poster-fallback">{p.name}</div>}
                    </div>
                    <div className="title">{p.name}</div>
                  </Link>
                ))}
              </div>
              <Pagination page={data.page} totalPages={data.totalPages}
                onChange={(p) => setParams({ ...(keyword ? { keyword } : {}), page: String(p) })} />
            </>
          )}
        </QueryState>
      </div>
    </>
  );
}
