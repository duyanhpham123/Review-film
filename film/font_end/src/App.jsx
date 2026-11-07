import { Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import { RequireAdmin, RequireAuth, RequireModerator } from './components/Guards';
import Home from './pages/Home';
import Movies from './pages/Movies';
import MovieDetail from './pages/MovieDetail';
import ReviewDetail from './pages/ReviewDetail';
import People from './pages/People';
import Person from './pages/Person';
import Profile from './pages/Profile';
import { Login, Register, VerifyEmail, ForgotPassword, ResetPassword } from './pages/AuthPages';
import Settings from './pages/me/Settings';
import Watchlist from './pages/me/Watchlist';
import Favorites from './pages/me/Favorites';
import Notifications from './pages/me/Notifications';
import AdminLayout from './pages/admin/AdminLayout';
import Dashboard from './pages/admin/Dashboard';
import AdminMovies from './pages/admin/AdminMovies';
import AdminMovieEdit from './pages/admin/AdminMovieEdit';
import AdminPeople from './pages/admin/AdminPeople';
import AdminGenres from './pages/admin/AdminGenres';
import AdminUsers from './pages/admin/AdminUsers';
import AdminReports from './pages/admin/AdminReports';
import AdminAudit from './pages/admin/AdminAudit';

function NotFound() {
  return (
    <div className="empty">
      <title>Không tìm thấy trang · ReviewFilm</title>
      <h1>404</h1>
      <p>Không tìm thấy trang bạn cần.</p>
    </div>
  );
}

const admin = (el) => <RequireAdmin>{el}</RequireAdmin>;

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="phim" element={<Movies />} />
        <Route path="phim/:slug" element={<MovieDetail />} />
        <Route path="review/:id" element={<ReviewDetail />} />
        <Route path="nguoi" element={<People />} />
        <Route path="nguoi/:slug" element={<Person />} />
        <Route path="u/:username" element={<Profile />} />

        <Route path="dang-nhap" element={<Login />} />
        <Route path="dang-ky" element={<Register />} />
        <Route path="quen-mat-khau" element={<ForgotPassword />} />
        {/* Hai đường dẫn dưới khớp với link backend gửi trong email */}
        <Route path="verify-email" element={<VerifyEmail />} />
        <Route path="reset-password" element={<ResetPassword />} />

        <Route path="tai-khoan" element={<RequireAuth><Settings /></RequireAuth>} />
        <Route path="tai-khoan/watchlist" element={<RequireAuth><Watchlist /></RequireAuth>} />
        <Route path="tai-khoan/yeu-thich" element={<RequireAuth><Favorites /></RequireAuth>} />
        <Route path="thong-bao" element={<RequireAuth><Notifications /></RequireAuth>} />

        <Route path="admin" element={<RequireModerator><AdminLayout /></RequireModerator>}>
          <Route index element={<Dashboard />} />
          <Route path="phim" element={admin(<AdminMovies />)} />
          <Route path="phim/moi" element={admin(<AdminMovieEdit />)} />
          <Route path="phim/:id" element={admin(<AdminMovieEdit />)} />
          <Route path="nguoi" element={admin(<AdminPeople />)} />
          <Route path="the-loai" element={admin(<AdminGenres />)} />
          <Route path="nguoi-dung" element={admin(<AdminUsers />)} />
          <Route path="bao-cao" element={<AdminReports />} />
          <Route path="audit" element={admin(<AdminAudit />)} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
