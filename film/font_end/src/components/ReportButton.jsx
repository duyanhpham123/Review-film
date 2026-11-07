import { useState } from 'react';
import { reviewApi, commentApi } from '../api/endpoints';
import { errorMessage } from '../api/client';
import { REPORT_REASON } from './ui';

/** Báo cáo review hoặc bình luận. type: 'review' | 'comment'. */
export default function ReportButton({ type, id }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('SPAM');
  const [note, setNote] = useState('');
  const [state, setState] = useState({ busy: false, msg: '', error: '' });

  const submit = async (e) => {
    e.preventDefault();
    setState({ busy: true, msg: '', error: '' });
    try {
      const fn = type === 'review' ? reviewApi.report : commentApi.report;
      await fn(id, { reason, note: note.trim() || null });
      setState({ busy: false, msg: 'Đã gửi báo cáo, cảm ơn bạn.', error: '' });
      setTimeout(() => setOpen(false), 1200);
    } catch (err) {
      setState({ busy: false, msg: '', error: errorMessage(err) });
    }
  };

  return (
    <>
      <button className="link-btn" onClick={() => setOpen(true)}>Báo cáo</button>
      {open && (
        <div className="modal-backdrop" onClick={() => setOpen(false)}>
          <form className="card modal form" onClick={(e) => e.stopPropagation()} onSubmit={submit}>
            <h3>Báo cáo {type === 'review' ? 'review' : 'bình luận'}</h3>
            <label className="field">Lý do
              <select value={reason} onChange={(e) => setReason(e.target.value)}>
                {Object.entries(REPORT_REASON).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </label>
            <label className="field">Ghi chú (không bắt buộc)
              <textarea maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
            </label>
            {state.error && <div className="alert error">{state.error}</div>}
            {state.msg && <div className="alert success">{state.msg}</div>}
            <div className="row between">
              <button type="button" className="btn" onClick={() => setOpen(false)}>Hủy</button>
              <button className="btn primary" disabled={state.busy}>Gửi báo cáo</button>
            </div>
          </form>
        </div>
      )}
    </>
  );
}
