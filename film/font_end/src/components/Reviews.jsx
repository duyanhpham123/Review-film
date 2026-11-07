import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { reviewApi } from '../api/endpoints';
import { errorCode, errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ReportButton from './ReportButton';
import { Avatar, Pagination, QueryState, UserLink, timeAgo } from './ui';

export function SpoilerText({ spoiler, children, clamp }) {
  const [show, setShow] = useState(false);
  const hidden = spoiler && !show;
  return (
    <div className={`spoiler ${hidden ? 'hidden' : ''}`}>
      <div className={`spoiler-body pre-line ${clamp ? 'clamp' : ''}`}>{children}</div>
      {hidden && (
        <div className="spoiler-cover">
          <button className="btn small" onClick={() => setShow(true)}>⚠ Có tiết lộ nội dung · Bấm để xem</button>
        </div>
      )}
    </div>
  );
}

function LikeButton({ review }) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const [liked, setLiked] = useState(!!review.likedByMe);
  const [count, setCount] = useState(review.likeCount);
  const mine = user?.id === review.author?.id;
  const toggle = useMutation({
    mutationFn: () => (liked ? reviewApi.unlike(review.id) : reviewApi.like(review.id)),
    onMutate: () => { setLiked(!liked); setCount((c) => c + (liked ? -1 : 1)); },
    onError: () => { setLiked(liked); setCount(review.likeCount); },
    onSettled: () => qc.invalidateQueries({ queryKey: ['reviews'] }),
  });
  if (!user || mine) return <span className="muted small">♥ {count}</span>;
  return (
    <button className={`btn small ${liked ? 'active' : ''}`} onClick={() => toggle.mutate()} aria-pressed={liked}>♥ {count}</button>
  );
}

export function ReviewItem({ review, showMovie, full }) {
  const { user } = useAuth();
  const canReport = user && user.id !== review.author?.id;
  return (
    <article className="review">
      <div className="review-head">
        <Avatar src={review.author?.avatarUrl} name={review.author?.fullName || review.author?.username} />
        <div>
          <UserLink user={review.author} />
          <div className="muted small">
            {timeAgo(review.createdAt)}
            {showMovie && <> · <Link to={`/phim/${review.movie.slug}`}>{review.movie.title}</Link></>}
            {review.status === 'HIDDEN' && <> · <span className="tag danger">Đã bị ẩn</span></>}
          </div>
        </div>
        {review.score != null && <div className="review-score">★ {review.score}</div>}
      </div>
      <h3>{full ? review.title : <Link to={`/review/${review.id}`}>{review.title}</Link>}</h3>
      <SpoilerText spoiler={review.hasSpoiler} clamp={!full}>{review.content}</SpoilerText>
      <div className="row" style={{ marginTop: 10 }}>
        <LikeButton review={review} />
        {!full && <Link to={`/review/${review.id}`} className="btn small ghost">💬 {review.commentCount} bình luận</Link>}
        <span className="grow" />
        {canReport && <ReportButton type="review" id={review.id} />}
      </div>
    </article>
  );
}

/** Form viết hoặc sửa review. Có thể kèm điểm (ghi luôn vào điểm của người viết). */
export function ReviewForm({ movieId, review, onDone, onCancel }) {
  const qc = useQueryClient();
  const [form, setForm] = useState({
    title: review?.title || '',
    content: review?.content || '',
    hasSpoiler: review?.hasSpoiler || false,
    score: review?.score ?? '',
  });
  const [error, setError] = useState('');
  const save = useMutation({
    mutationFn: () => {
      const body = { ...form, score: form.score === '' ? null : Number(form.score) };
      return review ? reviewApi.update(review.id, body) : reviewApi.create(movieId, body);
    },
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: ['reviews'] });
      qc.invalidateQueries({ queryKey: ['review', saved.id] });
      qc.invalidateQueries({ queryKey: ['movie-me', saved.movie.id] });
      qc.invalidateQueries({ queryKey: ['rating', saved.movie.id] });
      // Số review hiển thị trên trang phim
      qc.invalidateQueries({ queryKey: ['movie', saved.movie.slug] });
      onDone?.(saved);
    },
    onError: (e) => setError(errorCode(e) === 'EMAIL_NOT_VERIFIED'
      ? 'Bạn cần xác thực email trước khi viết review (kiểm tra hộp thư hoặc gửi lại link trong trang Tài khoản).'
      : errorMessage(e)),
  });
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));

  return (
    <form className="card form" onSubmit={(e) => { e.preventDefault(); setError(''); save.mutate(); }}>
      <h3>{review ? 'Sửa review' : 'Viết review'}</h3>
      <label className="field">Tiêu đề
        <input type="text" value={form.title} onChange={set('title')} maxLength={255} required />
      </label>
      <label className="field">Nội dung (tối thiểu 20 ký tự)
        <textarea rows={7} value={form.content} onChange={set('content')} minLength={20} maxLength={50000} required />
      </label>
      <div className="row">
        <label className="field" style={{ flexDirection: 'row', alignItems: 'center' }}>Điểm
          <select value={form.score} onChange={set('score')} style={{ width: 'auto' }}>
            <option value="">Không chấm</option>
            {Array.from({ length: 10 }, (_, i) => 10 - i).map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </label>
        <label className="check"><input type="checkbox" checked={form.hasSpoiler} onChange={set('hasSpoiler')} /> Có tiết lộ nội dung</label>
      </div>
      {error && <div className="alert error">{error}</div>}
      <div className="row">
        <button className="btn primary" disabled={save.isPending}>{review ? 'Lưu' : 'Đăng review'}</button>
        {onCancel && <button type="button" className="btn" onClick={onCancel}>Hủy</button>}
      </div>
    </form>
  );
}

export function MovieReviews({ movie, myReviewId }) {
  const { user } = useAuth();
  const [sort, setSort] = useState('latest');
  const [page, setPage] = useState(0);
  const [writing, setWriting] = useState(false);
  const reviews = useQuery({
    queryKey: ['reviews', movie.id, sort, page],
    queryFn: () => reviewApi.byMovie(movie.id, sort, page),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="stack">
      <div className="row between">
        <div className="row">
          <button className={`btn small ${sort === 'latest' ? 'active' : ''}`} onClick={() => { setSort('latest'); setPage(0); }}>Mới nhất</button>
          <button className={`btn small ${sort === 'top' ? 'active' : ''}`} onClick={() => { setSort('top'); setPage(0); }}>Nhiều like</button>
        </div>
        {user ? (
          myReviewId
            ? <Link to={`/review/${myReviewId}`} className="btn small">Xem review của bạn</Link>
            : !writing && <button className="btn primary small" onClick={() => setWriting(true)}>✎ Viết review</button>
        ) : (
          <Link to="/dang-nhap" className="btn small">Đăng nhập để viết review</Link>
        )}
      </div>
      {writing && <ReviewForm movieId={movie.id} onDone={() => setWriting(false)} onCancel={() => setWriting(false)} />}
      <QueryState query={reviews} isEmpty={(d) => d.content.length === 0} empty="Chưa có review nào. Hãy là người đầu tiên!">
        {(data) => (
          <div>
            {data.content.map((r) => <ReviewItem key={r.id} review={r} />)}
            <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
          </div>
        )}
      </QueryState>
    </div>
  );
}
