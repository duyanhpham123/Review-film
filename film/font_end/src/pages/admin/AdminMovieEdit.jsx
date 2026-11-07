import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi, catalogApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { Poster } from '../../components/MovieCard';
import { MOVIE_STATUS, MOVIE_TYPE, Spinner, formatDate } from '../../components/ui';

const EMPTY = {
  title: '', originalTitle: '', slug: '', description: '', posterUrl: '', bannerUrl: '', trailerUrl: '',
  releaseDate: '', durationMinutes: '', originalLanguage: '', type: 'MOVIE', status: 'COMPLETED', ageRating: '',
  tmdbId: '', genreIds: [], countryIds: [],
};

function toForm(m) {
  return {
    ...EMPTY,
    ...Object.fromEntries(Object.keys(EMPTY).map((k) => [k, m[k] ?? EMPTY[k]])),
    genreIds: m.genres.map((g) => g.id),
    countryIds: m.countries.map((c) => c.id),
    // Không đưa URL ảnh đã upload (MinIO) vào ô nhập để không ghi đè bằng URL đầy đủ
    posterUrl: '', bannerUrl: '',
  };
}

function toBody(f) {
  const n = (v) => (v === '' || v == null ? null : v);
  return {
    ...f,
    originalTitle: n(f.originalTitle), slug: n(f.slug), description: n(f.description),
    posterUrl: f.posterUrl.trim() ? f.posterUrl.trim() : null, bannerUrl: f.bannerUrl.trim() ? f.bannerUrl.trim() : null,
    trailerUrl: n(f.trailerUrl), releaseDate: n(f.releaseDate), originalLanguage: n(f.originalLanguage), ageRating: n(f.ageRating),
    durationMinutes: f.durationMinutes === '' ? null : Number(f.durationMinutes),
    tmdbId: f.tmdbId === '' ? null : Number(f.tmdbId),
  };
}

function MovieForm({ movie, onSaved }) {
  const genres = useQuery({ queryKey: ['genres'], queryFn: catalogApi.genres });
  const countries = useQuery({ queryKey: ['countries'], queryFn: catalogApi.countries });
  const [form, setForm] = useState(movie ? toForm(movie) : EMPTY);
  const [state, setState] = useState({ busy: false, error: '', msg: '' });
  useEffect(() => { if (movie) setForm(toForm(movie)); }, [movie]);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const toggleId = (k, id) => setForm((f) => ({ ...f, [k]: f[k].includes(id) ? f[k].filter((x) => x !== id) : [...f[k], id] }));

  const submit = async (e) => {
    e.preventDefault();
    setState({ busy: true, error: '', msg: '' });
    try {
      const body = toBody(form);
      const saved = movie ? await adminApi.updateMovie(movie.id, body) : await adminApi.createMovie(body);
      setState({ busy: false, error: '', msg: 'Đã lưu.' });
      onSaved(saved);
    } catch (err) {
      setState({ busy: false, error: errorMessage(err), msg: '' });
    }
  };

  return (
    <form className="card form" onSubmit={submit}>
      <h2>Thông tin phim</h2>
      <div className="form-grid">
        <label className="field">Tên phim *<input type="text" value={form.title} onChange={set('title')} required maxLength={255} /></label>
        <label className="field">Tên gốc<input type="text" value={form.originalTitle} onChange={set('originalTitle')} maxLength={255} /></label>
        <label className="field">Slug (để trống sẽ tự sinh)<input type="text" value={form.slug} onChange={set('slug')} pattern="[a-z0-9]+(-[a-z0-9]+)*" /></label>
        <label className="field">Loại
          <select value={form.type} onChange={set('type')}>{Object.entries(MOVIE_TYPE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
        </label>
        <label className="field">Trạng thái
          <select value={form.status} onChange={set('status')}>{Object.entries(MOVIE_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
        </label>
        <label className="field">Ngày phát hành<input type="date" value={form.releaseDate} onChange={set('releaseDate')} /></label>
        <label className="field">Thời lượng (phút)<input type="number" min={1} value={form.durationMinutes} onChange={set('durationMinutes')} /></label>
        <label className="field">Ngôn ngữ gốc (vi, en, ko...)<input type="text" value={form.originalLanguage} onChange={set('originalLanguage')} maxLength={10} /></label>
        <label className="field">Phân loại tuổi (P, T13, T16, T18...)<input type="text" value={form.ageRating} onChange={set('ageRating')} maxLength={10} /></label>
        <label className="field">TMDB ID<input type="number" value={form.tmdbId} onChange={set('tmdbId')} /></label>
      </div>
      <label className="field">Link trailer (YouTube)<input type="text" value={form.trailerUrl} onChange={set('trailerUrl')} maxLength={500} /></label>
      <div className="form-grid">
        <label className="field">URL poster ngoài (không bắt buộc, hoặc upload bên dưới)<input type="text" value={form.posterUrl} onChange={set('posterUrl')} maxLength={500} placeholder="https://..." /></label>
        <label className="field">URL banner ngoài<input type="text" value={form.bannerUrl} onChange={set('bannerUrl')} maxLength={500} placeholder="https://..." /></label>
      </div>
      <label className="field">Mô tả<textarea rows={5} value={form.description} onChange={set('description')} /></label>
      <fieldset className="card" style={{ padding: 12 }}>
        <legend className="muted">Thể loại</legend>
        <div className="row">
          {(genres.data || []).map((g) => (
            <label key={g.id} className="check"><input type="checkbox" checked={form.genreIds.includes(g.id)} onChange={() => toggleId('genreIds', g.id)} /> {g.name}</label>
          ))}
        </div>
      </fieldset>
      <fieldset className="card" style={{ padding: 12 }}>
        <legend className="muted">Quốc gia</legend>
        <div className="row">
          {(countries.data || []).map((c) => (
            <label key={c.id} className="check"><input type="checkbox" checked={form.countryIds.includes(c.id)} onChange={() => toggleId('countryIds', c.id)} /> {c.name}</label>
          ))}
        </div>
      </fieldset>
      {state.error && <div className="alert error">{state.error}</div>}
      {state.msg && <div className="alert success">{state.msg}</div>}
      <div className="row">
        <button className="btn primary" disabled={state.busy}>{movie ? 'Lưu thay đổi' : 'Tạo phim (ở trạng thái ẩn)'}</button>
      </div>
    </form>
  );
}

function ImageUpload({ label, url, onUpload }) {
  const [state, setState] = useState({ busy: false, error: '' });
  return (
    <div className="stack" style={{ gap: 8 }}>
      <strong>{label}</strong>
      {url ? <img src={url} alt={label} style={{ maxHeight: 180, borderRadius: 8, objectFit: 'cover' }} /> : <div className="muted small">Chưa có ảnh</div>}
      <label className="btn small" style={{ alignSelf: 'flex-start' }}>
        {state.busy ? 'Đang tải lên...' : 'Chọn ảnh'}
        <input type="file" accept="image/jpeg,image/png,image/webp" hidden disabled={state.busy} onChange={async (e) => {
          const file = e.target.files?.[0];
          e.target.value = '';
          if (!file) return;
          setState({ busy: true, error: '' });
          try { await onUpload(file); setState({ busy: false, error: '' }); } catch (err) { setState({ busy: false, error: errorMessage(err) }); }
        }} />
      </label>
      {state.error && <div className="alert error small">{state.error}</div>}
    </div>
  );
}

const ROLE = { DIRECTOR: 'Đạo diễn', ACTOR: 'Diễn viên', WRITER: 'Biên kịch', PRODUCER: 'Nhà sản xuất' };

function PersonPicker({ onPick }) {
  const [keyword, setKeyword] = useState('');
  const people = useQuery({ queryKey: ['people-pick', keyword], queryFn: () => catalogApi.people(keyword, 0, 8), enabled: keyword.trim().length >= 1 });
  return (
    <div className="suggest grow">
      <input type="search" placeholder="Tìm người để thêm vào ê-kíp..." value={keyword} onChange={(e) => setKeyword(e.target.value)} style={{ width: '100%' }} aria-label="Tìm người" />
      {keyword && people.data?.content?.length > 0 && (
        <div className="suggest-list">
          {people.data.content.map((p) => (
            <button type="button" key={p.id} onClick={() => { onPick(p); setKeyword(''); }}>{p.name} <span className="muted small">/{p.slug}</span></button>
          ))}
        </div>
      )}
      {keyword && people.data?.content?.length === 0 && (
        <div className="suggest-list"><div className="muted small" style={{ padding: 10 }}>Không thấy. <Link to="/admin/nguoi" className="link-btn">Thêm người mới</Link></div></div>
      )}
    </div>
  );
}

function CreditsEditor({ movie, onSaved }) {
  const initial = [...movie.directors, ...movie.writers, ...movie.cast, ...movie.producers].map((c) => ({
    personId: c.personId, name: c.name, role: c.role, characterName: c.characterName || '', displayOrder: c.displayOrder,
  }));
  const [rows, setRows] = useState(initial);
  const [state, setState] = useState({ busy: false, error: '', msg: '' });
  useEffect(() => { setRows(initial); }, [movie]); // eslint-disable-line react-hooks/exhaustive-deps
  const update = (i, k, v) => setRows((r) => r.map((row, j) => (j === i ? { ...row, [k]: v } : row)));

  const save = async () => {
    setState({ busy: true, error: '', msg: '' });
    try {
      const saved = await adminApi.setCredits(movie.id, rows.map((r) => ({
        personId: r.personId, role: r.role, characterName: r.role === 'ACTOR' ? r.characterName.trim() || null : null, displayOrder: Number(r.displayOrder) || 0,
      })));
      setState({ busy: false, error: '', msg: 'Đã lưu ê-kíp.' });
      onSaved(saved);
    } catch (err) {
      setState({ busy: false, error: errorMessage(err), msg: '' });
    }
  };

  return (
    <div className="card stack">
      <h2>Ê-kíp ({rows.length})</h2>
      <PersonPicker onPick={(p) => setRows((r) => [...r, { personId: p.id, name: p.name, role: 'ACTOR', characterName: '', displayOrder: r.length }])} />
      {rows.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Người</th><th>Vai trò</th><th>Tên nhân vật</th><th>Thứ tự</th><th></th></tr></thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={`${r.personId}-${i}`}>
                  <td>{r.name}</td>
                  <td><select value={r.role} onChange={(e) => update(i, 'role', e.target.value)}>{Object.entries(ROLE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></td>
                  <td><input type="text" value={r.characterName} disabled={r.role !== 'ACTOR'} onChange={(e) => update(i, 'characterName', e.target.value)} maxLength={150} /></td>
                  <td><input type="number" value={r.displayOrder} onChange={(e) => update(i, 'displayOrder', e.target.value)} style={{ width: 70 }} /></td>
                  <td><button className="btn small danger" onClick={() => setRows((rs) => rs.filter((_, j) => j !== i))}>Bỏ</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {state.error && <div className="alert error">{state.error}</div>}
      {state.msg && <div className="alert success">{state.msg}</div>}
      <div><button className="btn primary" onClick={save} disabled={state.busy}>Lưu ê-kíp</button></div>
    </div>
  );
}

function EpisodesEditor({ movieId }) {
  const qc = useQueryClient();
  const episodes = useQuery({ queryKey: ['admin', 'episodes', movieId], queryFn: () => adminApi.episodes(movieId) });
  const [form, setForm] = useState({ seasonNumber: 1, episodeNumber: 1, title: '', durationMinutes: '', airDate: '' });
  const [error, setError] = useState('');
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin', 'episodes', movieId] });

  const add = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await adminApi.createEpisode(movieId, {
        seasonNumber: Number(form.seasonNumber), episodeNumber: Number(form.episodeNumber), title: form.title || null,
        durationMinutes: form.durationMinutes ? Number(form.durationMinutes) : null, airDate: form.airDate || null,
      });
      setForm((f) => ({ ...f, episodeNumber: Number(f.episodeNumber) + 1, title: '' }));
      refresh();
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  return (
    <div className="card stack">
      <h2>Tập phim</h2>
      <form className="row" onSubmit={add}>
        <label className="field">Mùa<input type="number" min={1} value={form.seasonNumber} onChange={(e) => setForm({ ...form, seasonNumber: e.target.value })} style={{ width: 80 }} /></label>
        <label className="field">Tập<input type="number" min={1} value={form.episodeNumber} onChange={(e) => setForm({ ...form, episodeNumber: e.target.value })} style={{ width: 80 }} /></label>
        <label className="field grow">Tên tập<input type="text" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></label>
        <label className="field">Phút<input type="number" min={1} value={form.durationMinutes} onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })} style={{ width: 80 }} /></label>
        <label className="field">Ngày phát<input type="date" value={form.airDate} onChange={(e) => setForm({ ...form, airDate: e.target.value })} /></label>
        <button className="btn primary" style={{ alignSelf: 'flex-end' }}>Thêm tập</button>
      </form>
      {error && <div className="alert error">{error}</div>}
      {episodes.isPending ? <Spinner /> : (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Mùa</th><th>Tập</th><th>Tên</th><th>Phút</th><th>Ngày phát</th><th></th></tr></thead>
            <tbody>
              {(episodes.data || []).map((ep) => (
                <tr key={ep.id}>
                  <td>{ep.seasonNumber}</td><td>{ep.episodeNumber}</td><td>{ep.title}</td><td>{ep.durationMinutes}</td><td>{formatDate(ep.airDate)}</td>
                  <td><button className="btn small danger" onClick={async () => { if (confirm('Xóa tập này?')) { await adminApi.deleteEpisode(ep.id); refresh(); } }}>Xóa</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default function AdminMovieEdit() {
  const { id } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const movie = useQuery({ queryKey: ['admin', 'movie', id], queryFn: () => adminApi.movie(id), enabled: !!id });
  const setMovie = (m) => {
    qc.setQueryData(['admin', 'movie', String(m.id)], m);
    qc.invalidateQueries({ queryKey: ['admin', 'movies'] });
  };

  if (!id) {
    return (
      <div className="stack">
        <h1>Thêm phim</h1>
        <MovieForm onSaved={(m) => { setMovie(m); navigate(`/admin/phim/${m.id}`, { replace: true }); }} />
      </div>
    );
  }
  if (movie.isPending) return <Spinner />;
  if (movie.isError) return <div className="alert error">{errorMessage(movie.error)}</div>;
  const m = movie.data;

  return (
    <div className="stack" style={{ gap: 20 }}>
      <div className="row between">
        <h1>{m.title}</h1>
        <div className="row">
          <span className={`tag ${m.hidden ? 'danger' : 'success'}`}>{m.hidden ? 'Đang ẩn' : 'Đang hiện'}</span>
          <button className="btn" onClick={async () => setMovie(await adminApi.setVisibility(m.id, !m.hidden))}>{m.hidden ? 'Hiện phim' : 'Ẩn phim'}</button>
          {!m.hidden && <Link to={`/phim/${m.slug}`} className="btn">Xem trang phim</Link>}
        </div>
      </div>
      <div className="card form-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))' }}>
        <div style={{ maxWidth: 160 }}><Poster src={m.posterUrl} title={m.title} /></div>
        <ImageUpload label="Poster" url={m.posterUrl} onUpload={async (f) => setMovie(await adminApi.uploadPoster(m.id, f))} />
        <ImageUpload label="Banner" url={m.bannerUrl} onUpload={async (f) => setMovie(await adminApi.uploadBanner(m.id, f))} />
      </div>
      <MovieForm movie={m} onSaved={setMovie} />
      <CreditsEditor movie={m} onSaved={setMovie} />
      {m.type === 'SERIES' && <EpisodesEditor movieId={m.id} />}
    </div>
  );
}
