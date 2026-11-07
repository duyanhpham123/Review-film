import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { catalogApi } from '../api/endpoints';
import { Poster } from '../components/MovieCard';
import { QueryState, formatDate } from '../components/ui';

const ROLE = { DIRECTOR: 'Đạo diễn', ACTOR: 'Diễn viên', WRITER: 'Biên kịch', PRODUCER: 'Nhà sản xuất' };

export default function Person() {
  const { slug } = useParams();
  const person = useQuery({ queryKey: ['person', slug], queryFn: () => catalogApi.person(slug) });

  return (
    <QueryState query={person}>
      {(p) => {
        const groups = Object.keys(ROLE)
          .map((role) => ({ role, credits: p.credits.filter((c) => c.role === role) }))
          .filter((g) => g.credits.length > 0);
        return (
          <>
            <title>{`${p.name} · ReviewFilm`}</title>
            <div className="detail" style={{ gridTemplateColumns: '220px 1fr' }}>
              <Poster src={p.avatarUrl} title={p.name} />
              <div>
                <h1>{p.name}</h1>
                {p.birthDate && <p className="muted">Sinh ngày {formatDate(p.birthDate)}</p>}
                {p.biography ? <p className="pre-line">{p.biography}</p> : <p className="muted">Chưa có tiểu sử.</p>}
              </div>
            </div>
            {groups.map((g) => (
              <section className="section" key={g.role}>
                <h2>{ROLE[g.role]} ({g.credits.length})</h2>
                <div className="movie-grid">
                  {g.credits.map((c) => (
                    <Link key={`${c.movie.id}-${c.characterName || ''}`} to={`/phim/${c.movie.slug}`} className="movie-card">
                      <Poster src={c.movie.posterUrl} title={c.movie.title} />
                      <div className="title">{c.movie.title}</div>
                      <div className="meta">{[c.movie.releaseDate?.slice(0, 4), c.characterName].filter(Boolean).join(' · ')}</div>
                    </Link>
                  ))}
                </div>
              </section>
            ))}
          </>
        );
      }}
    </QueryState>
  );
}
