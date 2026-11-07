import { useState } from 'react';
import { Link } from 'react-router-dom';
import { authApi, userApi } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { Avatar } from '../../components/ui';

function useAction() {
  const [state, setState] = useState({ busy: false, msg: '', error: '' });
  const run = async (fn, msg) => {
    setState({ busy: true, msg: '', error: '' });
    try {
      await fn();
      setState({ busy: false, msg, error: '' });
    } catch (err) {
      setState({ busy: false, msg: '', error: errorMessage(err) });
    }
  };
  const Feedback = () => (
    <>
      {state.msg && <div className="alert success">{state.msg}</div>}
      {state.error && <div className="alert error">{state.error}</div>}
    </>
  );
  return { ...state, run, Feedback };
}

export default function Settings() {
  const { user, setUser } = useAuth();
  const [profile, setProfile] = useState({ fullName: user.fullName || '', bio: user.bio || '' });
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const profileAction = useAction();
  const avatarAction = useAction();
  const pwAction = useAction();
  const verifyAction = useAction();

  return (
    <div className="stack" style={{ maxWidth: 720, gap: 20 }}>
      <title>Tài khoản · ReviewFilm</title>
      <div className="row between">
        <h1>Tài khoản</h1>
        <Link to={`/u/${user.username}`} className="btn small">Xem trang cá nhân</Link>
      </div>

      {!user.emailVerified && (
        <div className="alert info row between">
          <span>Email <strong>{user.email}</strong> chưa được xác thực. Bạn cần xác thực để viết review và bình luận.</span>
          <button className="btn small" disabled={verifyAction.busy}
            onClick={() => verifyAction.run(() => authApi.resendVerification(user.email), 'Đã gửi lại link xác thực, hãy kiểm tra email.')}>Gửi lại link</button>
        </div>
      )}
      <verifyAction.Feedback />

      <section className="card form">
        <h2>Ảnh đại diện</h2>
        <div className="row">
          <Avatar src={user.avatarUrl} name={user.fullName || user.username} size={72} />
          <label className="btn">
            Chọn ảnh (JPG, PNG, WEBP, tối đa 5MB)
            <input type="file" accept="image/jpeg,image/png,image/webp" hidden disabled={avatarAction.busy}
              onChange={(e) => {
                const file = e.target.files?.[0];
                e.target.value = '';
                if (file) avatarAction.run(async () => setUser(await userApi.updateAvatar(file)), 'Đã cập nhật ảnh đại diện.');
              }} />
          </label>
        </div>
        <avatarAction.Feedback />
      </section>

      <form className="card form" onSubmit={(e) => { e.preventDefault(); profileAction.run(async () => setUser(await userApi.updateProfile(profile)), 'Đã lưu thông tin.'); }}>
        <h2>Thông tin cá nhân</h2>
        <div className="form-grid">
          <label className="field">Username<input type="text" value={user.username} disabled /></label>
          <label className="field">Email<input type="email" value={user.email} disabled /></label>
        </div>
        <label className="field">Họ tên
          <input type="text" maxLength={100} value={profile.fullName} onChange={(e) => setProfile({ ...profile, fullName: e.target.value })} />
        </label>
        <label className="field">Giới thiệu
          <textarea maxLength={500} value={profile.bio} onChange={(e) => setProfile({ ...profile, bio: e.target.value })} />
        </label>
        <profileAction.Feedback />
        <div><button className="btn primary" disabled={profileAction.busy}>Lưu</button></div>
      </form>

      <form className="card form" onSubmit={(e) => {
        e.preventDefault();
        if (pw.newPassword !== pw.confirm) { pwAction.run(() => Promise.reject({ response: { data: { message: 'Mật khẩu nhập lại không khớp' } } })); return; }
        pwAction.run(async () => {
          await userApi.changePassword({ currentPassword: pw.currentPassword, newPassword: pw.newPassword });
          setPw({ currentPassword: '', newPassword: '', confirm: '' });
        }, 'Đã đổi mật khẩu. Các thiết bị khác đã bị đăng xuất.');
      }}>
        <h2>Đổi mật khẩu</h2>
        <label className="field">Mật khẩu hiện tại
          <input type="password" autoComplete="current-password" value={pw.currentPassword} onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })} required />
        </label>
        <div className="form-grid">
          <label className="field">Mật khẩu mới
            <input type="password" autoComplete="new-password" minLength={8} maxLength={72} value={pw.newPassword} onChange={(e) => setPw({ ...pw, newPassword: e.target.value })} required />
          </label>
          <label className="field">Nhập lại mật khẩu mới
            <input type="password" autoComplete="new-password" value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} required />
          </label>
        </div>
        <pwAction.Feedback />
        <div><button className="btn primary" disabled={pwAction.busy}>Đổi mật khẩu</button></div>
      </form>
    </div>
  );
}
