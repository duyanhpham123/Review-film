import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { Pagination, QueryState, REPORT_REASON, UserLink, timeAgo } from '../../components/ui';

const STATUS = { PENDING: 'Chờ xử lý', RESOLVED: 'Đã xử lý', REJECTED: 'Bỏ qua' };

export default function AdminReports() {
  const qc = useQueryClient();
  const [filter, setFilter] = useState({ status: 'PENDING', targetType: '', page: 0 });
  const reports = useQuery({ queryKey: ['admin', 'reports', filter], queryFn: () => adminApi.reports({ ...filter, size: 20 }), placeholderData: keepPreviousData });
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin', 'reports'] });

  const handle = async (r, status, hideTarget) => {
    try { await adminApi.handleReport(r.id, { status, hideTarget }); refresh(); } catch (e) { alert(errorMessage(e)); }
  };
  const setTarget = async (r, status) => {
    try {
      if (r.targetType === 'REVIEW') await adminApi.setReviewStatus(r.targetId, status);
      else await adminApi.setCommentStatus(r.targetId, status);
      refresh();
    } catch (e) { alert(errorMessage(e)); }
  };

  return (
    <div className="stack">
      <h1>Báo cáo vi phạm</h1>
      <div className="row">
        <div className="tabs" style={{ marginBottom: 0, flex: 1 }}>
          {[['', 'Tất cả'], ...Object.entries(STATUS)].map(([k, v]) => (
            <button key={k} className={filter.status === k ? 'active' : ''} onClick={() => setFilter({ ...filter, status: k, page: 0 })}>{v}</button>
          ))}
        </div>
        <select value={filter.targetType} onChange={(e) => setFilter({ ...filter, targetType: e.target.value, page: 0 })} aria-label="Loại nội dung">
          <option value="">Review + bình luận</option>
          <option value="REVIEW">Review</option>
          <option value="COMMENT">Bình luận</option>
        </select>
      </div>
      <QueryState query={reports} isEmpty={(d) => d.content.length === 0} empty="Không có báo cáo.">
        {(data) => (
          <>
            <div className="stack">
              {data.content.map((r) => (
                <div key={r.id} className="card stack" style={{ gap: 8 }}>
                  <div className="row between">
                    <div className="row">
                      <span className="tag">{r.targetType === 'REVIEW' ? 'Review' : 'Bình luận'}</span>
                      <span className="tag danger">{REPORT_REASON[r.reason]}</span>
                      <span className={`tag ${r.targetStatus === 'VISIBLE' ? 'success' : ''}`}>Nội dung: {r.targetStatus}</span>
                    </div>
                    <span className="muted small">{STATUS[r.status]} · {timeAgo(r.createdAt)}</span>
                  </div>
                  <div className="muted small">Người báo cáo: <UserLink user={r.reporter} /></div>
                  {r.targetPreview && <blockquote className="bubble pre-line" style={{ margin: 0, padding: '8px 12px', borderLeft: '3px solid var(--border)' }}>{r.targetPreview}</blockquote>}
                  {r.note && <div className="small">Ghi chú: {r.note}</div>}
                  <div className="row">
                    {r.targetType === 'REVIEW' && <Link to={`/review/${r.targetId}`} className="btn small ghost">Mở review</Link>}
                    {r.status === 'PENDING' && (
                      <>
                        <button className="btn small danger" onClick={() => handle(r, 'RESOLVED', true)}>Ẩn nội dung + đóng báo cáo</button>
                        <button className="btn small" onClick={() => handle(r, 'REJECTED', false)}>Bỏ qua</button>
                      </>
                    )}
                    {r.targetStatus === 'HIDDEN' && <button className="btn small" onClick={() => setTarget(r, 'VISIBLE')}>Hiện lại nội dung</button>}
                    {r.targetStatus === 'VISIBLE' && r.status !== 'PENDING' && <button className="btn small" onClick={() => setTarget(r, 'HIDDEN')}>Ẩn nội dung</button>}
                  </div>
                </div>
              ))}
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => setFilter({ ...filter, page: p })} />
          </>
        )}
      </QueryState>
    </div>
  );
}
