import { Fragment, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints';
import { Pagination, QueryState } from '../../components/ui';

function pretty(json) {
  if (!json) return '—';
  try { return JSON.stringify(JSON.parse(json), null, 2); } catch { return json; }
}

function localTime(value) {
  return new Date(value.endsWith('Z') ? value : `${value}Z`).toLocaleString('vi-VN');
}

export default function AdminAudit() {
  const [form, setForm] = useState({ actor: '', action: '', from: '', to: '' });
  const [filter, setFilter] = useState({ ...form, page: 0 });
  const [open, setOpen] = useState(null);
  const logs = useQuery({ queryKey: ['admin', 'audit', filter], queryFn: () => adminApi.auditLogs({ ...filter, size: 30 }), placeholderData: keepPreviousData });

  return (
    <div className="stack">
      <h1>Audit log</h1>
      <form className="row" onSubmit={(e) => { e.preventDefault(); setFilter({ ...form, page: 0 }); }}>
        <input type="text" placeholder="Username người thực hiện" value={form.actor} onChange={(e) => setForm({ ...form, actor: e.target.value })} aria-label="Người thực hiện" />
        <input type="text" placeholder="Hành động (MOVIE_UPDATE...)" value={form.action} onChange={(e) => setForm({ ...form, action: e.target.value.toUpperCase() })} aria-label="Hành động" />
        <input type="date" value={form.from} onChange={(e) => setForm({ ...form, from: e.target.value })} aria-label="Từ ngày" />
        <input type="date" value={form.to} onChange={(e) => setForm({ ...form, to: e.target.value })} aria-label="Đến ngày" />
        <button className="btn">Lọc</button>
      </form>
      <QueryState query={logs} isEmpty={(d) => d.content.length === 0} empty="Không có log.">
        {(data) => (
          <>
            <div className="table-wrap">
              <table>
                <thead><tr><th>Thời gian</th><th>Người thực hiện</th><th>Hành động</th><th>Đối tượng</th><th>IP</th><th></th></tr></thead>
                <tbody>
                  {data.content.map((a) => (
                    <Fragment key={a.id}>
                      <tr>
                        <td className="small">{localTime(a.createdAt)}</td>
                        <td>{a.actorUsername || <span className="muted">hệ thống</span>}</td>
                        <td><span className="tag accent">{a.action}</span></td>
                        <td>{a.targetType} #{a.targetId}</td>
                        <td className="muted small">{a.ipAddress}</td>
                        <td><button className="btn small" onClick={() => setOpen(open === a.id ? null : a.id)}>{open === a.id ? 'Ẩn' : 'Chi tiết'}</button></td>
                      </tr>
                      {open === a.id && (
                        <tr>
                          <td colSpan={6}>
                            <div className="form-grid">
                              <div><div className="muted small">Trước</div><div className="json">{pretty(a.beforeData)}</div></div>
                              <div><div className="muted small">Sau</div><div className="json">{pretty(a.afterData)}</div></div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => setFilter({ ...filter, page: p })} />
          </>
        )}
      </QueryState>
    </div>
  );
}
