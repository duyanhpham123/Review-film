import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { meApi, movieApi } from '../api/endpoints';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Poster } from '../components/MovieCard';
import RatingBox from '../components/RatingBox';
import { MovieReviews } from '../components/Reviews';
import Comments from '../components/Comments';
import { MOVIE_STATUS, MOVIE_TYPE, QueryState, WATCH_STATUS, formatDate } from '../components/ui';

function PeopleRow({ people }) {
  return (
    <div className="people-row">
      {people.map((p) => (
        <Link key={`${p.personId}-${p.role}-${p.characterName || ''}`} to={`/nguoi/${p.slug}`} className="person-chip">
          {p.avatarUrl
            ? <img className="avatar" src={p.avatarUrl} alt={p.name} loading="lazy" />
            : <span className="avatar" style={{ fontSize: 40 }} aria-hidden="true">{p.name.charAt(0)}</span>}
          <strong>{p.name}</strong>
          {p.characterName && <span className="muted">{p.characterName}</span>}
        </Link>
      ))}
    </div>
  );
}

function MyActions({ movie }) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const [error, setError] = useState('');
  const me = useQuery({ queryKey: ['movie-me', movie.id], queryFn: () => movieApi.me(movie.id), enabled: !!user });
  const done = () => { qc.invalidateQueries({ queryKey: ['movie-me', movie.id] }); qc.invalidateQueries({ queryKey: ['watchlist'] }); qc.invalidateQueries({ queryKey: ['favorites'] }); setError(''); };
  const fav = useMutation({
    mutationFn: () => (me.data?.favorite ? movieApi.unfavorite(movie.id) : movieApi.favorite(movie.id)),
    onSuccess: done, onError: (e) => setError(errorMessage(e)),
  });
  const watch = useMutation({
    mutationFn: (status) => (status ? meApi.setWatchlist(movie.id, { status }) : meApi.removeWatchlist(movie.id)),
    onSuccess: done, onError: (e) => setError(errorMessage(e)),
  });

  if (!user) return <Link to="/dang-nhap" className="btn">Đăng nhập để lưu phim</Link>;
  const status = me.data?.watchlistStatus;
  return (
    <div className="stack" style={{ gap: 8 }}>
      <div className="row">
        <button className={`btn ${me.data?.favorite ? 'active' : ''}`} onClick={() => fav.mutate()} disabled={fav.isPending || me.isPending}>
          {me.data?.favorite ? '♥ Đã yêu thích' : '♡ Yêu thích'}
        </button>
        <select value={status || ''} onChange={(e) => watch.mutate(e.target.value)} disabled={watch.isPending || me.isPending} aria-label="Trạng thái xem">
          <option value="">＋ Thêm vào watchlist</option>
          {Object.entries(WATCH_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        {status && <button className="link-btn" onClick={() => watch.mutate('')}>Bỏ khỏi watchlist</button>}
      </div>
      {error && <div className="alert error small">{error}</div>}
    </div>
  );
}

function Episodes({ slug }) {
  const seasons = useQuery({ queryKey: ['episodes', slug], queryFn: () => movieApi.episodes(slug) });
  return (
    <QueryState query={seasons} isEmpty={(d) => d.length === 0} empty="Chưa có thông tin tập phim.">
      {(data) => data.map((s) => (
        <div key={s.seasonNumber} style={{ marginBottom: 18 }}>
          <h3>Mùa {s.seasonNumber}</h3>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Tập</th><th>Tên</th><th>Thời lượng</th><th>Ngày phát sóng</th></tr></thead>
              <tbody>
                {s.episodes.map((e) => (
                  <tr key={e.id}>
                    <td>{e.episodeNumber}</td>
                    <td>{e.title || <span className="muted">Tập {e.episodeNumber}</span>}{e.description && <div className="muted small">{e.description}</div>}</td>
                    <td>{e.durationMinutes ? `${e.durationMinutes} phút` : ''}</td>
                    <td>{formatDate(e.airDate)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </QueryState>
  );
}

function trailerEmbed(url) {
  if (!url) return null;
  const m = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([\w-]{11})/);
  return m ? `https://www.youtube-nocookie.com/embed/${m[1]}` : null;
}

export default function MovieDetail() {
  const { slug } = useParams();
  const { user } = useAuth();
  const [tab, setTab] = useState('reviews');
  const movie = useQuery({ queryKey: ['movie', slug], queryFn: () => movieApi.detail(slug) });
  const me = useQuery({ queryKey: ['movie-me', movie.data?.id], queryFn: () => movieApi.me(movie.data.id), enabled: !!user && !!movie.data });

  // Ghi nhận lượt xem một lần mỗi lần mở trang (backend tự chống đếm trùng trong 1 giờ)
  const movieId = movie.data?.id;
  useEffect(() => {
    if (movieId) movieApi.view(movieId).catch(() => {});
  }, [movieId]);

  return (
    <QueryState query={movie}>
      {(m) => {
        const embed = trailerEmbed(m.trailerUrl);
        const crew = [...m.directors, ...m.writers];
        return (
          <>
            <title>{`${m.title}${m.releaseDate ? ` (${m.releaseDate.slice(0, 4)})` : ''} · ReviewFilm`}</title>
            <meta name="description" content={(m.description || `Review phim ${m.title}`).slice(0, 160)} />
            {m.bannerUrl && (
              <div className="hero" style={{ minHeight: 260, marginBottom: 24 }}>
                <div className="hero-bg" style={{ backgroundImage: `url(${m.bannerUrl})`, opacity: 0.5 }} />
              </div>
            )}
            <div className="detail">
              <div className="stack">
                <Poster src={m.posterUrl} title={m.title} />
                <RatingBox movieId={m.id} myRating={me.data?.myRating} />
              </div>
              <div>
                <div className="row" style={{ marginBottom: 8 }}>
                  <span className="tag">{MOVIE_TYPE[m.type]}</span>
                  <span className="tag accent">{MOVIE_STATUS[m.status]}</span>
                  {m.ageRating && <span className="tag">{m.ageRating}</span>}
                </div>
                <h1>{m.title}</h1>
                {m.originalTitle && m.originalTitle !== m.title && <p className="muted">{m.originalTitle}</p>}
                <MyActions movie={m} />
                <dl className="facts">
                  {m.releaseDate && <><dt>Phát hành</dt><dd>{formatDate(m.releaseDate)}</dd></>}
                  {m.durationMinutes && <><dt>Thời lượng</dt><dd>{m.durationMinutes} phút</dd></>}
                  {m.genres.length > 0 && <><dt>Thể loại</dt><dd>{m.genres.map((g, i) => <span key={g.id}>{i > 0 && ', '}<Link to={`/phim?genre=${g.slug}`}>{g.name}</Link></span>)}</dd></>}
                  {m.countries.length > 0 && <><dt>Quốc gia</dt><dd>{m.countries.map((c, i) => <span key={c.id}>{i > 0 && ', '}<Link to={`/phim?country=${c.code}`}>{c.name}</Link></span>)}</dd></>}
                  {m.directors.length > 0 && <><dt>Đạo diễn</dt><dd>{m.directors.map((d, i) => <span key={d.personId}>{i > 0 && ', '}<Link to={`/nguoi/${d.slug}`}>{d.name}</Link></span>)}</dd></>}
                  <dt>Lượt xem</dt><dd>{m.viewCount.toLocaleString('vi-VN')}</dd>
                </dl>
                {m.description && <p className="pre-line">{m.description}</p>}
                {embed && (
                  <div style={{ aspectRatio: '16 / 9', marginTop: 16 }}>
                    <iframe title={`Trailer ${m.title}`} src={embed} style={{ width: '100%', height: '100%', border: 0, borderRadius: 10 }}
                      allow="encrypted-media; picture-in-picture" allowFullScreen loading="lazy" />
                  </div>
                )}
                {!embed && m.trailerUrl && <a className="btn" href={m.trailerUrl} target="_blank" rel="noreferrer noopener">▶ Xem trailer</a>}
              </div>
            </div>

            {m.cast.length > 0 && (
              <section className="section">
                <h2>Diễn viên</h2>
                <PeopleRow people={m.cast} />
              </section>
            )}
            {crew.length > 0 && (
              <section className="section">
                <h2>Ê-kíp</h2>
                <PeopleRow people={crew.map((c) => ({ ...c, characterName: c.role === 'DIRECTOR' ? 'Đạo diễn' : 'Biên kịch' }))} />
              </section>
            )}

            <section className="section">
              <div className="tabs" role="tablist">
                <button role="tab" className={tab === 'reviews' ? 'active' : ''} onClick={() => setTab('reviews')}>Review ({m.reviewCount})</button>
                <button role="tab" className={tab === 'comments' ? 'active' : ''} onClick={() => setTab('comments')}>Bình luận</button>
                {m.type === 'SERIES' && <button role="tab" className={tab === 'episodes' ? 'active' : ''} onClick={() => setTab('episodes')}>Tập phim</button>}
              </div>
              {tab === 'reviews' && <MovieReviews movie={m} myReviewId={me.data?.myReviewId} />}
              {tab === 'comments' && <Comments movieId={m.id} />}
              {tab === 'episodes' && <Episodes slug={m.slug} />}
            </section>
          </>
        );
      }}
    </QueryState>
  );
}
