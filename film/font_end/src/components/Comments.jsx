import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { commentApi } from '../api/endpoints';
import { errorCode, errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ReportButton from './ReportButton';
import { Avatar, Pagination, QueryState, UserLink, timeAgo } from './ui';

function CommentForm({ onSubmit, placeholder = 'Viết bình luận...', autoFocus, onCancel, initial = '', submitText = 'Gửi' }) {
  const [content, setContent] = useState(initial);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    if (!content.trim()) return;
    setBusy(true);
    setError('');
    try {
      await onSubmit(content.trim());
      setContent('');
    } catch (err) {
      setError(errorCode(err) === 'EMAIL_NOT_VERIFIED' ? 'Bạn cần xác thực email trước khi bình luận.' : errorMessage(err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <form className="stack" onSubmit={submit} style={{ gap: 8 }}>
      <textarea rows={2} style={{ minHeight: 60 }} value={content} onChange={(e) => setContent(e.target.value)}
        placeholder={placeholder} maxLength={2000} autoFocus={autoFocus} aria-label={placeholder} />
      {error && <div className="alert error small">{error}</div>}
      <div className="row">
        <button className="btn primary small" disabled={busy || !content.trim()}>{submitText}</button>
        {onCancel && <button type="button" className="btn small" onClick={onCancel}>Hủy</button>}
      </div>
    </form>
  );
}

function CommentItem({ comment, onReply, onChanged, isReply }) {
  const { user } = useAuth();
  const [replying, setReplying] = useState(false);
  const [editing, setEditing] = useState(false);
  const visible = comment.status === 'VISIBLE';
  const mine = user && comment.author?.id === user.id;

  const remove = async () => {
    if (!confirm('Xóa bình luận này?')) return;
    await commentApi.remove(comment.id);
    onChanged();
  };

  return (
    <div className={`comment ${visible ? '' : 'deleted'}`}>
      <Avatar src={visible ? comment.author?.avatarUrl : null} name={visible ? (comment.author?.fullName || comment.author?.username) : '?'} size={34} />
      <div className="body">
        {editing ? (
          <CommentForm initial={comment.content} submitText="Lưu" autoFocus onCancel={() => setEditing(false)}
            onSubmit={async (content) => { await commentApi.update(comment.id, content); setEditing(false); onChanged(); }} />
        ) : (
          <div className="bubble">
            {visible && <div className="small"><UserLink user={comment.author} /></div>}
            <div className="pre-line">{comment.content}</div>
          </div>
        )}
        <div className="row small" style={{ gap: 14, marginTop: 4 }}>
          <span className="muted">{timeAgo(comment.createdAt)}{comment.edited ? ' · đã sửa' : ''}</span>
          {visible && user && <button className="link-btn" onClick={() => setReplying((r) => !r)}>Trả lời</button>}
          {visible && mine && !editing && <button className="link-btn" onClick={() => setEditing(true)}>Sửa</button>}
          {visible && mine && <button className="link-btn" onClick={remove}>Xóa</button>}
          {visible && user && !mine && <ReportButton type="comment" id={comment.id} />}
        </div>
        {replying && (
          <div style={{ marginTop: 8 }}>
            <CommentForm autoFocus placeholder={`Trả lời ${comment.author?.username || ''}...`} onCancel={() => setReplying(false)}
              onSubmit={async (content) => { await onReply(content, isReply ? comment.parentId : comment.id); setReplying(false); }} />
          </div>
        )}
      </div>
    </div>
  );
}

/** Bình luận 2 cấp cho trang phim (reviewId không có) hoặc dưới một review. */
export default function Comments({ movieId, reviewId }) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const key = ['comments', reviewId ? `r${reviewId}` : `m${movieId}`, page];
  const comments = useQuery({
    queryKey: key,
    queryFn: () => (reviewId ? commentApi.forReview(reviewId, page) : commentApi.forMovie(movieId, page)),
    placeholderData: keepPreviousData,
  });
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['comments'] });
    if (reviewId) qc.invalidateQueries({ queryKey: ['review', Number(reviewId)] });
  };
  const post = useMutation({
    mutationFn: ({ content, parentId }) => (reviewId
      ? commentApi.onReview(reviewId, { content, parentId })
      : commentApi.onMovie(movieId, { content, parentId })),
    onSuccess: refresh,
  });
  const send = (content, parentId = null) => post.mutateAsync({ content, parentId });

  return (
    <div className="stack">
      {user ? <CommentForm onSubmit={(c) => send(c)} /> : (
        <p className="muted"><Link to="/dang-nhap" className="link-btn">Đăng nhập</Link> để bình luận.</p>
      )}
      <QueryState query={comments} isEmpty={(d) => d.content.length === 0} empty="Chưa có bình luận.">
        {(data) => (
          <div>
            {data.content.map((c) => (
              <div key={c.id}>
                <CommentItem comment={c} onReply={send} onChanged={refresh} />
                {c.replies.length > 0 && (
                  <div className="replies">
                    {c.replies.map((r) => <CommentItem key={r.id} comment={r} onReply={send} onChanged={refresh} isReply />)}
                  </div>
                )}
              </div>
            ))}
            <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
          </div>
        )}
      </QueryState>
    </div>
  );
}
