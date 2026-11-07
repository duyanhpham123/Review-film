import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { reviewApi } from '../api/endpoints';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Poster } from '../components/MovieCard';
import { ReviewForm, ReviewItem } from '../components/Reviews';
import Comments from '../components/Comments';
import { QueryState } from '../components/ui';

export default function ReviewDetail() {
  const { id } = useParams();
  const reviewId = Number(id);
  const { user } = useAuth();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const review = useQuery({ queryKey: ['review', reviewId], queryFn: () => reviewApi.get(reviewId) });

  const remove = async (r) => {
    if (!confirm('Xóa review này? Bình luận dưới review cũng bị xóa.')) return;
    try {
      await reviewApi.remove(r.id);
      qc.invalidateQueries({ queryKey: ['reviews'] });
      qc.invalidateQueries({ queryKey: ['movie-me', r.movie.id] });
      navigate(`/phim/${r.movie.slug}`);
    } catch (e) {
      alert(errorMessage(e));
    }
  };

  return (
    <QueryState query={review}>
      {(r) => (
        <>
          <title>{`${r.title} · Review ${r.movie.title} · ReviewFilm`}</title>
          <div className="detail" style={{ gridTemplateColumns: '160px 1fr' }}>
            <Link to={`/phim/${r.movie.slug}`} className="stack" style={{ gap: 8 }}>
              <Poster src={r.movie.posterUrl} title={r.movie.title} />
              <strong>{r.movie.title}</strong>
            </Link>
            <div>
              {r.status === 'HIDDEN' && <div className="alert info">Review này đã bị quản trị viên ẩn, chỉ bạn và quản trị viên nhìn thấy.</div>}
              {editing
                ? <ReviewForm review={r} onDone={() => setEditing(false)} onCancel={() => setEditing(false)} />
                : <ReviewItem review={r} full />}
              {user?.id === r.author?.id && !editing && (
                <div className="row">
                  <button className="btn small" onClick={() => setEditing(true)}>Sửa review</button>
                  <button className="btn small danger" onClick={() => remove(r)}>Xóa review</button>
                </div>
              )}
              {r.status === 'VISIBLE' && (
                <section className="section">
                  <h2>Bình luận ({r.commentCount})</h2>
                  <Comments reviewId={r.id} />
                </section>
              )}
            </div>
          </div>
        </>
      )}
    </QueryState>
  );
}
