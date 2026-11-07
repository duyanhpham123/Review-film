import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { movieApi } from '../api/endpoints';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { scoreText } from './ui';

/** Điểm trung bình, biểu đồ phân bố và ô chấm điểm của tôi. Đọc API rating (không cache) để luôn mới. */
export default function RatingBox({ movieId, myRating }) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const [error, setError] = useState('');
  const rating = useQuery({ queryKey: ['rating', movieId], queryFn: () => movieApi.rating(movieId) });

  const afterChange = (summary) => {
    qc.setQueryData(['rating', movieId], summary);
    qc.invalidateQueries({ queryKey: ['movie-me', movieId] });
    setError('');
  };
  const rate = useMutation({ mutationFn: (score) => movieApi.rate(movieId, score), onSuccess: afterChange, onError: (e) => setError(errorMessage(e)) });
  const unrate = useMutation({ mutationFn: () => movieApi.unrate(movieId), onSuccess: afterChange, onError: (e) => setError(errorMessage(e)) });

  const data = rating.data;
  const max = Math.max(1, ...(data?.distribution || []).map((d) => d.count));

  return (
    <div className="card stack">
      <div className="row">
        <div className="big-score">{scoreText(data?.avgRating)}</div>
        <div>
          <div><strong>/ 10</strong></div>
          <div className="muted small">{data?.ratingCount || 0} lượt chấm</div>
        </div>
      </div>
      <div className="histogram" aria-label="Phân bố điểm">
        {[...(data?.distribution || [])].reverse().map((d) => (
          <div className="bar-row" key={d.score}>
            <span>{d.score}</span>
            <div className="bar"><span style={{ width: `${(d.count / max) * 100}%` }} /></div>
            <span>{d.count}</span>
          </div>
        ))}
      </div>
      <div>
        <div className="row between" style={{ marginBottom: 6 }}>
          <strong>Điểm của bạn</strong>
          {user && myRating != null && (
            <button className="link-btn" onClick={() => unrate.mutate()} disabled={unrate.isPending}>Xóa điểm</button>
          )}
        </div>
        {user ? (
          <div className="score-picker" role="radiogroup" aria-label="Chấm điểm từ 1 đến 10">
            {Array.from({ length: 10 }, (_, i) => i + 1).map((s) => (
              <button key={s} className={myRating != null && s <= myRating ? 'on' : ''} aria-pressed={myRating === s}
                onClick={() => rate.mutate(s)} disabled={rate.isPending}>{s}</button>
            ))}
          </div>
        ) : (
          <p className="muted small"><Link to="/dang-nhap" className="link-btn">Đăng nhập</Link> để chấm điểm.</p>
        )}
        {error && <div className="alert error small" style={{ marginTop: 8 }}>{error}</div>}
      </div>
    </div>
  );
}
