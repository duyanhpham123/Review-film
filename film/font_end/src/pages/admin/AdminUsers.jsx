import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { Pagination, QueryState, formatDate } from '../../components/ui';

const STATUS = { ACTIVE: 'Hoạt động', INACTIVE: 'Vô hiệu hóa', BANNED: 'Bị khóa' };
const ROLES = { ROLE_USER: 'User', ROLE_MODERATOR: 'Moderator', ROLE_ADMIN: 'Admin' };

export default function AdminUsers() {
  const { user: me } = useAuth();
  const qc = useQueryClient();
  const [keyword, setKeyword] = useState('');
  const [filter, setFilter] = useState({ keyword: '', status: '', role: '', page: 0 });
  const users = useQuery({ queryKey: ['admin', 'users', filter], queryFn: () => adminApi.users({ ...filter, size: 20 }), placeholderData: keepPreviousData });
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin', 'users'] });

  const setStatus = async (u, status) => {
    if (status === 'BANNED' && !confirm(`Khóa tài khoản ${u.username}? Người này sẽ bị đăng xuất.`)) return;
    try { await adminApi.setUserStatus(u.id, status); refresh(); } catch (e) { alert(errorMessage(e)); }
  };
  const toggleRole = async (u, role) => {
    const roles = u.roles.includes(role) ? u.roles.filter((r) => r !== role) : [...u.roles, role];
    try { await adminApi.setUserRoles(u.id, roles); refresh(); } catch (e) { alert(errorMessage(e)); }
  };

  return (
    <div className="stack">
      <h1>Người dùng</h1>
      <form className="row" onSubmit={(e) => { e.preventDefault(); setFilter({ ...filter, keyword, page: 0 }); }}>
        <input type="search" placeholder="Username, email, họ tên..." value={keyword} onChange={(e) => setKeyword(e.target.value)} className="grow" aria-label="Tìm người dùng" />
        <select value={filter.status} onChange={(e) => setFilter({ ...filter, status: e.target.value, page: 0 })} aria-label="Trạng thái">
          <option value="">Mọi trạng thái</option>
          {Object.entries(STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select value={filter.role} onChange={(e) => setFilter({ ...filter, role: e.target.value, page: 0 })} aria-label="Quyền">
          <option value="">Mọi quyền</option>
          {Object.entries(ROLES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <button className="btn">Lọc</button>
      </form>
      <p className="muted small">Đổi quyền có hiệu lực từ lần cấp access token tiếp theo của người đó (tối đa 15 phút).</p>
      <QueryState query={users} isEmpty={(d) => d.content.length === 0} empty="Không có người dùng.">
        {(data) => (
          <>
            <div className="table-wrap">
              <table>
                <thead><tr><th>Người dùng</th><th>Email</th><th>Tham gia</th><th>Quyền</th><th>Trạng thái</th></tr></thead>
                <tbody>
                  {data.content.map((u) => (
                    <tr key={u.id}>
                      <td><Link to={`/u/${u.username}`}><strong>{u.username}</strong></Link><div className="muted small">{u.fullName}</div></td>
                      <td>{u.email} {u.emailVerified ? <span className="tag success">đã xác thực</span> : <span className="tag">chưa xác thực</span>}</td>
                      <td className="small">{formatDate(u.createdAt)}<div className="muted">Đăng nhập: {u.lastLoginAt ? formatDate(u.lastLoginAt) : '–'}</div></td>
                      <td>
                        {['ROLE_MODERATOR', 'ROLE_ADMIN'].map((r) => (
                          <label key={r} className="check" style={{ marginRight: 10 }}>
                            <input type="checkbox" checked={u.roles.includes(r)} disabled={u.id === me.id && r === 'ROLE_ADMIN'} onChange={() => toggleRole(u, r)} /> {ROLES[r]}
                          </label>
                        ))}
                      </td>
                      <td>
                        <select value={u.status} disabled={u.id === me.id} onChange={(e) => setStatus(u, e.target.value)} aria-label={`Trạng thái của ${u.username}`}>
                          {Object.entries(STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                        </select>
                      </td>
                    </tr>
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
