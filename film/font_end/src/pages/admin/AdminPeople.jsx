import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi, catalogApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { Avatar, Pagination, QueryState } from '../../components/ui';

function PersonModal({ person, onClose, onSaved }) {
  const [form, setForm] = useState({
    name: person?.name || '', slug: person?.slug || '', biography: person?.biography || '',
    birthDate: person?.birthDate || '', tmdbId: person?.tmdbId ?? '',
  });
  const [state, setState] = useState({ busy: false, error: '' });
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setState({ busy: true, error: '' });
    const body = {
      name: form.name.trim(), slug: form.slug.trim() || null, biography: form.biography || null,
      birthDate: form.birthDate || null, tmdbId: form.tmdbId === '' ? null : Number(form.tmdbId),
    };
    try {
      const saved = person ? await adminApi.updatePerson(person.id, body) : await adminApi.createPerson(body);
      onSaved(saved);
    } catch (err) {
      setState({ busy: false, error: errorMessage(err) });
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form className="card modal form" onClick={(e) => e.stopPropagation()} onSubmit={submit}>
        <h2>{person ? 'Sửa thông tin' : 'Thêm người'}</h2>
        <label className="field">Tên *<input type="text" value={form.name} onChange={set('name')} required maxLength={150} /></label>
        <div className="form-grid">
          <label className="field">Slug (để trống sẽ tự sinh)<input type="text" value={form.slug} onChange={set('slug')} pattern="[a-z0-9]+(-[a-z0-9]+)*" /></label>
          <label className="field">Ngày sinh<input type="date" value={form.birthDate} onChange={set('birthDate')} /></label>
          <label className="field">TMDB ID<input type="number" value={form.tmdbId} onChange={set('tmdbId')} /></label>
        </div>
        <label className="field">Tiểu sử<textarea rows={5} value={form.biography} onChange={set('biography')} /></label>
        {state.error && <div className="alert error">{state.error}</div>}
        <div className="row between">
          <button type="button" className="btn" onClick={onClose}>Hủy</button>
          <button className="btn primary" disabled={state.busy}>Lưu</button>
        </div>
      </form>
    </div>
  );
}

export default function AdminPeople() {
  const qc = useQueryClient();
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState({ keyword: '', page: 0 });
  const [editing, setEditing] = useState(null);
  const people = useQuery({ queryKey: ['people', query.keyword, query.page, 'admin'], queryFn: () => catalogApi.people(query.keyword, query.page, 20), placeholderData: keepPreviousData });
  const refresh = () => qc.invalidateQueries({ queryKey: ['people'] });

  /** Danh sách công khai không có tiểu sử: lấy chi tiết trước khi mở form sửa. */
  const edit = async (p) => setEditing(await catalogApi.person(p.slug));
  const remove = async (p) => {
    if (!confirm(`Xóa "${p.name}"?`)) return;
    try { await adminApi.deletePerson(p.id); refresh(); } catch (e) { alert(errorMessage(e)); }
  };
  const upload = async (p, file) => {
    try { await adminApi.uploadPersonAvatar(p.id, file); refresh(); } catch (e) { alert(errorMessage(e)); }
  };

  return (
    <div className="stack">
      <div className="row between">
        <h1>Đạo diễn, diễn viên</h1>
        <button className="btn primary" onClick={() => setEditing({})}>＋ Thêm người</button>
      </div>
      <form className="row" onSubmit={(e) => { e.preventDefault(); setQuery({ keyword, page: 0 }); }}>
        <input type="search" placeholder="Tìm theo tên..." value={keyword} onChange={(e) => setKeyword(e.target.value)} className="grow" aria-label="Tên" />
        <button className="btn">Tìm</button>
      </form>
      <QueryState query={people} isEmpty={(d) => d.content.length === 0} empty="Không có ai.">
        {(data) => (
          <>
            <div className="table-wrap">
              <table>
                <thead><tr><th>Ảnh</th><th>Tên</th><th>Slug</th><th></th></tr></thead>
                <tbody>
                  {data.content.map((p) => (
                    <tr key={p.id}>
                      <td><Avatar src={p.avatarUrl} name={p.name} size={40} /></td>
                      <td><Link to={`/nguoi/${p.slug}`}><strong>{p.name}</strong></Link></td>
                      <td className="muted">{p.slug}</td>
                      <td className="row" style={{ flexWrap: 'nowrap' }}>
                        <button className="btn small" onClick={() => edit(p)}>Sửa</button>
                        <label className="btn small">Ảnh
                          <input type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ''; if (f) upload(p, f); }} />
                        </label>
                        <button className="btn small danger" onClick={() => remove(p)}>Xóa</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => setQuery({ ...query, page: p })} />
          </>
        )}
      </QueryState>
      {editing && (
        <PersonModal person={editing.id ? editing : null} onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); refresh(); }} />
      )}
    </div>
  );
}
