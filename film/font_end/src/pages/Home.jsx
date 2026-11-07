import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { catalogApi, movieApi } from '../api/endpoints';
import { MovieScroller } from '../components/MovieCard';
import { QueryState, scoreText } from '../components/ui';

function Section({ title, to, query, empty }) {
  return (
    <section className="section">
      <div className="section-head">
        <h2>{title}</h2>
        {to && <Link to={to}>Xem tất cả ›</Link>}
      </div>
      <QueryState query={query} isEmpty={(d) => !(d.content || d).length} empty={empty}>
        {(data) => <MovieScroller movies={data.content || data} />}
      </QueryState>
    </section>
  );
}

export default function Home() {
  const trending = useQuery({ queryKey: ['trending', 'week'], queryFn: () => movieApi.trending('week', 12) });
  const latest = useQuery({ queryKey: ['movies', { sort: 'release', size: 12 }], queryFn: () => movieApi.list({ sort: 'release', size: 12 }) });
  const topRated = useQuery({ queryKey: ['movies', { sort: 'rating', size: 12 }], queryFn: () => movieApi.list({ sort: 'rating', size: 12 }) });
  const upcoming = useQuery({ queryKey: ['movies', { status: 'UPCOMING', size: 12 }], queryFn: () => movieApi.list({ status: 'UPCOMING', sort: 'release', size: 12 }) });
  const genres = useQuery({ queryKey: ['genres'], queryFn: catalogApi.genres, staleTime: 3600_000 });

  const featured = topRated.data?.content?.find((m) => m.ratingCount > 0) || latest.data?.content?.[0];

  return (
    <>
      <title>ReviewFilm · Review phim, chấm điểm, bình luận</title>
      <meta name="description" content="Tra cứu thông tin phim, đọc và viết review, chấm điểm, lưu danh sách phim muốn xem." />
      <section className="hero">
        {featured?.posterUrl && <div className="hero-bg" style={{ backgroundImage: `url(${featured.posterUrl})` }} />}
        <div className="hero-content">
          <span className="tag accent">Nổi bật</span>
          <h1>{featured ? featured.title : 'Review phim cùng cộng đồng'}</h1>
          <p className="muted">
            {featured
              ? `${featured.releaseDate?.slice(0, 4) || ''} · ★ ${scoreText(featured.avgRating)} từ ${featured.ratingCount} lượt chấm`
              : 'Tìm phim, đọc review chân thực và chia sẻ cảm nhận của bạn.'}
          </p>
          <div className="row">
            {featured && <Link to={`/phim/${featured.slug}`} className="btn primary">Xem chi tiết</Link>}
            <Link to="/phim" className="btn">Khám phá phim</Link>
          </div>
        </div>
      </section>

      <Section title="Xem nhiều tuần này" query={trending} empty="Chưa có lượt xem nào trong tuần." />
      <Section title="Mới phát hành" to="/phim?sort=release" query={latest} empty="Chưa có phim." />
      <Section title="Điểm cao nhất" to="/phim?sort=rating" query={topRated} empty="Chưa có phim." />
      <Section title="Sắp chiếu" to="/phim?status=UPCOMING" query={upcoming} empty="Chưa có phim sắp chiếu." />

      <section className="section">
        <div className="section-head"><h2>Thể loại</h2></div>
        <div className="row">
          {(genres.data || []).map((g) => (
            <Link key={g.id} to={`/phim?genre=${g.slug}`} className="btn small">{g.name}</Link>
          ))}
        </div>
      </section>
    </>
  );
}
