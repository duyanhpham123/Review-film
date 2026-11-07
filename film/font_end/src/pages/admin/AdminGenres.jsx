import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi, catalogApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { QueryState } from '../../components/ui';

function GenreRow({ genre, onChanged }) {
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ name: genre.name, slug: genre.slug, description: genre.description || '' });
  const [error, setError] = useState('');
  const save = async () => {
    setError('');
    try {
      await adminApi.updateGenre(genre.id, { name: form.name.trim(), slug: form.slug.trim() || null, description: form.description || null });
      setEditing(false);
      onChanged();
    } catch (e) {
      setError(errorMessage(e));
    }
  };
  const remove = async () => {
    if (!confirm(`Xóa thể loại "${genre.name}"?`)) return;
    try { await adminApi.deleteGenre(genre.id); onChanged(); } catch (e) { alert(errorMessage(e)); }
  };
  if (editing) {
    return (
      <tr>
        <td><input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></td>
        <td><input type="text" value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value })} /></td>
        <td><input type="text" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />{error && <div className="alert error small">{error}</div>}</td>
        <td className="row" style={{ flexWrap: 'nowrap' }}><button className="btn small primary" onClick={save}>Lưu</button><button className="btn small" onClick={() => setEditing(false)}>Hủy</button></td>
      </tr>
    );
  }
  return (
    <tr>
      <td><strong>{genre.name}</strong></td>
      <td className="muted">{genre.slug}</td>
      <td className="muted">{genre.description}</td>
      <td className="row" style={{ flexWrap: 'nowrap' }}><button className="btn small" onClick={() => setEditing(true)}>Sửa</button><button className="btn small danger" onClick={remove}>Xóa</button></td>
    </tr>
  );
}

export default function AdminGenres() {
  const qc = useQueryClient();
  const genres = useQuery({ queryKey: ['genres'], queryFn: catalogApi.genres });
  const [form, setForm] = useState({ name: '', description: '' });
  const [error, setError] = useState('');
  const refresh = () => qc.invalidateQueries({ queryKey: ['genres'] });

  const add = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await adminApi.createGenre({ name: form.name.trim(), description: form.description || null });
      setForm({ name: '', description: '' });
      refresh();
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <div className="stack">
      <h1>Thể loại</h1>
      <form className="row card" style={{ padding: 12 }} onSubmit={add}>
        <input type="text" placeholder="Tên thể loại mới" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required maxLength={100} aria-label="Tên thể loại" />
        <input type="text" placeholder="Mô tả (không bắt buộc)" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} className="grow" aria-label="Mô tả" />
        <button className="btn primary">Thêm</button>
      </form>
      {error && <div className="alert error">{error}</div>}
      <QueryState query={genres}>
        {(data) => (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Tên</th><th>Slug</th><th>Mô tả</th><th></th></tr></thead>
              <tbody>{data.map((g) => <GenreRow key={g.id} genre={g} onChanged={refresh} />)}</tbody>
            </table>
          </div>
        )}
      </QueryState>
    </div>
  );
}
