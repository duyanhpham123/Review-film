import { Link } from 'react-router-dom';
import { MOVIE_TYPE, scoreText } from './ui';

export function Poster({ src, title }) {
  return (
    <div className="poster">
      {src ? <img src={src} alt={title} loading="lazy" /> : <div className="poster-fallback">{title}</div>}
    </div>
  );
}

export function MovieCard({ movie }) {
  const year = movie.releaseDate ? movie.releaseDate.slice(0, 4) : '';
  return (
    <Link to={`/phim/${movie.slug}`} className="movie-card">
      <div className="poster">
        {movie.posterUrl
          ? <img src={movie.posterUrl} alt={movie.title} loading="lazy" />
          : <div className="poster-fallback">{movie.title}</div>}
        {movie.ratingCount > 0 && <span className="score">★ {scoreText(movie.avgRating)}</span>}
        {movie.type === 'SERIES' && <span className="type">{MOVIE_TYPE.SERIES}</span>}
      </div>
      <div className="title">{movie.title}</div>
      <div className="meta">{[year, movie.reviewCount ? `${movie.reviewCount} review` : null].filter(Boolean).join(' · ')}</div>
    </Link>
  );
}

export function MovieGrid({ movies }) {
  return (
    <div className="movie-grid">
      {movies.map((m) => <MovieCard key={m.id} movie={m} />)}
    </div>
  );
}

export function MovieScroller({ movies }) {
  return (
    <div className="scroller">
      {movies.map((m) => <MovieCard key={m.id} movie={m} />)}
    </div>
  );
}
