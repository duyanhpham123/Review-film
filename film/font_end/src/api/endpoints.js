import { api } from './client';

const data = (p) => p.then((r) => r.data);

/** Bỏ các tham số rỗng để URL gọn và key cache ổn định. */
function clean(params) {
  return Object.fromEntries(Object.entries(params || {}).filter(([, v]) => v !== undefined && v !== null && v !== ''));
}

function form(file) {
  const fd = new FormData();
  fd.append('file', file);
  return fd;
}

export const authApi = {
  register: (body) => data(api.post('/auth/register', body)),
  login: (body) => data(api.post('/auth/login', body)),
  logout: () => data(api.post('/auth/logout')),
  forgotPassword: (email) => data(api.post('/auth/forgot-password', { email })),
  resetPassword: (token, newPassword) => data(api.post('/auth/reset-password', { token, newPassword })),
  verifyEmail: (token) => data(api.post('/auth/verify-email', { token })),
  resendVerification: (email) => data(api.post('/auth/resend-verification', { email })),
};

export const userApi = {
  me: () => data(api.get('/users/me')),
  updateProfile: (body) => data(api.patch('/users/me', body)),
  changePassword: (body) => data(api.put('/users/me/password', body)),
  updateAvatar: (file) => data(api.put('/users/me/avatar', form(file))),
  profile: (username) => data(api.get(`/users/${encodeURIComponent(username)}`)),
  reviews: (username, page = 0) => data(api.get(`/users/${encodeURIComponent(username)}/reviews`, { params: { page } })),
};

export const movieApi = {
  list: (params) => data(api.get('/movies', { params: clean(params) })),
  trending: (period = 'week', limit = 10) => data(api.get('/movies/trending', { params: { period, limit } })),
  detail: (slug) => data(api.get(`/movies/${encodeURIComponent(slug)}`)),
  me: (id) => data(api.get(`/movies/${id}/me`)),
  episodes: (slug) => data(api.get(`/movies/${encodeURIComponent(slug)}/episodes`)),
  view: (id) => data(api.post(`/movies/${id}/views`)),
  rating: (id) => data(api.get(`/movies/${id}/rating`)),
  rate: (id, score) => data(api.put(`/movies/${id}/rating`, { score })),
  unrate: (id) => data(api.delete(`/movies/${id}/rating`)),
  favorite: (id) => data(api.post(`/movies/${id}/favorite`)),
  unfavorite: (id) => data(api.delete(`/movies/${id}/favorite`)),
};

export const catalogApi = {
  genres: () => data(api.get('/genres')),
  countries: () => data(api.get('/countries')),
  people: (keyword, page = 0, size = 20) => data(api.get('/people', { params: clean({ keyword, page, size }) })),
  person: (slug) => data(api.get(`/people/${encodeURIComponent(slug)}`)),
};

export const reviewApi = {
  byMovie: (movieId, sort = 'latest', page = 0) => data(api.get(`/movies/${movieId}/reviews`, { params: { sort, page } })),
  get: (id) => data(api.get(`/reviews/${id}`)),
  create: (movieId, body) => data(api.post(`/movies/${movieId}/reviews`, body)),
  update: (id, body) => data(api.put(`/reviews/${id}`, body)),
  remove: (id) => data(api.delete(`/reviews/${id}`)),
  like: (id) => data(api.put(`/reviews/${id}/like`)),
  unlike: (id) => data(api.delete(`/reviews/${id}/like`)),
  report: (id, body) => data(api.post(`/reviews/${id}/reports`, body)),
};

export const commentApi = {
  forMovie: (movieId, page = 0) => data(api.get(`/movies/${movieId}/comments`, { params: { page } })),
  forReview: (reviewId, page = 0) => data(api.get(`/reviews/${reviewId}/comments`, { params: { page } })),
  onMovie: (movieId, body) => data(api.post(`/movies/${movieId}/comments`, body)),
  onReview: (reviewId, body) => data(api.post(`/reviews/${reviewId}/comments`, body)),
  update: (id, content) => data(api.put(`/comments/${id}`, { content })),
  remove: (id) => data(api.delete(`/comments/${id}`)),
  report: (id, body) => data(api.post(`/comments/${id}/reports`, body)),
};

export const meApi = {
  favorites: (page = 0) => data(api.get('/me/favorites', { params: { page } })),
  watchlist: (status, page = 0) => data(api.get('/me/watchlist', { params: clean({ status, page }) })),
  setWatchlist: (movieId, body) => data(api.put(`/me/watchlist/${movieId}`, body)),
  removeWatchlist: (movieId) => data(api.delete(`/me/watchlist/${movieId}`)),
  notifications: (page = 0) => data(api.get('/me/notifications', { params: { page } })),
  unreadCount: () => data(api.get('/me/notifications/unread-count')),
  markRead: (id) => data(api.patch(`/me/notifications/${id}/read`)),
  markAllRead: () => data(api.patch('/me/notifications/read-all')),
};

export const adminApi = {
  movies: (params) => data(api.get('/admin/movies', { params: clean(params) })),
  movie: (id) => data(api.get(`/admin/movies/${id}`)),
  createMovie: (body) => data(api.post('/admin/movies', body)),
  searchTmdb: (query, type) => data(api.get('/admin/tmdb/search', { params: { query, type } })),
  importTmdb: (tmdbId, type) => data(api.post('/admin/movies/import-tmdb', { tmdbId, type })),
  updateMovie: (id, body) => data(api.put(`/admin/movies/${id}`, body)),
  deleteMovie: (id) => data(api.delete(`/admin/movies/${id}`)),
  setVisibility: (id, hidden) => data(api.patch(`/admin/movies/${id}/visibility`, { hidden })),
  uploadPoster: (id, file) => data(api.put(`/admin/movies/${id}/poster`, form(file))),
  uploadBanner: (id, file) => data(api.put(`/admin/movies/${id}/banner`, form(file))),
  setCredits: (id, credits) => data(api.put(`/admin/movies/${id}/credits`, { credits })),
  episodes: (id) => data(api.get(`/admin/movies/${id}/episodes`)),
  createEpisode: (id, body) => data(api.post(`/admin/movies/${id}/episodes`, body)),
  updateEpisode: (id, body) => data(api.put(`/admin/episodes/${id}`, body)),
  deleteEpisode: (id) => data(api.delete(`/admin/episodes/${id}`)),
  createPerson: (body) => data(api.post('/admin/people', body)),
  updatePerson: (id, body) => data(api.put(`/admin/people/${id}`, body)),
  deletePerson: (id) => data(api.delete(`/admin/people/${id}`)),
  uploadPersonAvatar: (id, file) => data(api.put(`/admin/people/${id}/avatar`, form(file))),
  createGenre: (body) => data(api.post('/admin/genres', body)),
  updateGenre: (id, body) => data(api.put(`/admin/genres/${id}`, body)),
  deleteGenre: (id) => data(api.delete(`/admin/genres/${id}`)),
  users: (params) => data(api.get('/admin/users', { params: clean(params) })),
  setUserStatus: (id, status) => data(api.patch(`/admin/users/${id}/status`, { status })),
  setUserRoles: (id, roles) => data(api.patch(`/admin/users/${id}/role`, { roles })),
  reports: (params) => data(api.get('/admin/reports', { params: clean(params) })),
  handleReport: (id, body) => data(api.patch(`/admin/reports/${id}`, body)),
  setReviewStatus: (id, status) => data(api.patch(`/admin/reviews/${id}/status`, { status })),
  setCommentStatus: (id, status) => data(api.patch(`/admin/comments/${id}/status`, { status })),
  summary: () => data(api.get('/admin/dashboard/summary')),
  topMovies: (params) => data(api.get('/admin/dashboard/top-movies', { params: clean(params) })),
  views: (params) => data(api.get('/admin/dashboard/views', { params: clean(params) })),
  newUsers: (params) => data(api.get('/admin/dashboard/new-users', { params: clean(params) })),
  reviewsDaily: (params) => data(api.get('/admin/dashboard/reviews', { params: clean(params) })),
  auditLogs: (params) => data(api.get('/admin/audit-logs', { params: clean(params) })),
};
