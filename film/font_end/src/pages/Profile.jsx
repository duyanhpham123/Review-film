import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { userApi } from '../api/endpoints';
import { ReviewItem } from '../components/Reviews';
import { Avatar, Pagination, QueryState, formatDate } from '../components/ui';

export default function Profile() {
  const { username } = useParams();
  const [page, setPage] = useState(0);
  const profile = useQuery({ queryKey: ['profile', username], queryFn: () => userApi.profile(username) });
  const reviews = useQuery({ queryKey: ['reviews', 'user', username, page], queryFn: () => userApi.reviews(username, page), placeholderData: keepPreviousData });

  return (
    <QueryState query={profile}>
      {(p) => (
        <>
          <title>{`${p.fullName || p.username} · ReviewFilm`}</title>
          <div className="card row" style={{ gap: 20 }}>
            <Avatar src={p.avatarUrl} name={p.fullName || p.username} size={88} />
            <div className="grow">
              <h1 style={{ marginBottom: 2 }}>{p.fullName || p.username}</h1>
              <div className="muted">@{p.username} · tham gia {formatDate(p.joinedAt)}</div>
              {p.bio && <p style={{ marginTop: 8 }} className="pre-line">{p.bio}</p>}
            </div>
            <div className="stats" style={{ minWidth: 300 }}>
              <div className="stat"><div className="value">{p.reviewCount}</div><div className="label">Review</div></div>
              <div className="stat"><div className="value">{p.ratingCount}</div><div className="label">Lượt chấm</div></div>
              <div className="stat"><div className="value">{p.watchedCount}</div><div className="label">Đã xem</div></div>
              <div className="stat"><div className="value">{p.averageScore ?? '–'}</div><div className="label">Điểm TB đã chấm</div></div>
            </div>
          </div>
          <section className="section">
            <h2>Review</h2>
            <QueryState query={reviews} isEmpty={(d) => d.content.length === 0} empty="Chưa viết review nào.">
              {(data) => (
                <>
                  {data.content.map((r) => <ReviewItem key={r.id} review={r} showMovie />)}
                  <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
                </>
              )}
            </QueryState>
          </section>
        </>
      )}
    </QueryState>
  );
}
