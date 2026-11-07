import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { meApi } from '../../api/endpoints';
import { Pagination, QueryState, timeAgo } from '../../components/ui';

const ICON = { REVIEW_COMMENT: '💬', COMMENT_REPLY: '↩', REVIEW_LIKE: '♥', MOVIE_RELEASED: '🎬' };

export default function Notifications() {
  const [page, setPage] = useState(0);
  const qc = useQueryClient();
  const navigate = useNavigate();
  const list = useQuery({ queryKey: ['notifications', page], queryFn: () => meApi.notifications(page), placeholderData: keepPreviousData });

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['notifications'] });
    qc.invalidateQueries({ queryKey: ['unread-count'] });
  };

  /** Bấm vào thông báo: đánh dấu đã đọc rồi mở nội dung liên quan. */
  const open = async (n) => {
    if (!n.read) { await meApi.markRead(n.id).catch(() => {}); refresh(); }
    if (n.reviewId) navigate(`/review/${n.reviewId}`);
    else if (n.movieSlug) navigate(`/phim/${n.movieSlug}`);
  };

  return (
    <div style={{ maxWidth: 760 }}>
      <title>Thông báo · ReviewFilm</title>
      <div className="row between">
        <h1>Thông báo</h1>
        <button className="btn small" onClick={async () => { await meApi.markAllRead(); refresh(); }}>Đánh dấu tất cả đã đọc</button>
      </div>
      <QueryState query={list} isEmpty={(d) => d.content.length === 0} empty="Chưa có thông báo nào.">
        {(data) => (
          <>
            <div className="stack" style={{ gap: 2 }}>
              {data.content.map((n) => (
                <div key={n.id} className={`notif ${n.read ? '' : 'unread'}`} role="button" tabIndex={0}
                  onClick={() => open(n)} onKeyDown={(e) => e.key === 'Enter' && open(n)}>
                  <span style={{ fontSize: '1.2rem' }} aria-hidden="true">{ICON[n.type]}</span>
                  <div className="grow">
                    <div>{n.message}</div>
                    <div className="muted small">{timeAgo(n.createdAt)}</div>
                  </div>
                  {!n.read && <span className="dot" aria-label="Chưa đọc" />}
                </div>
              ))}
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
          </>
        )}
      </QueryState>
    </div>
  );
}
