import axios from 'axios';

/**
 * Axios dùng chung. Access token chỉ giữ trong bộ nhớ (không localStorage) để giảm rủi ro XSS;
 * refresh token nằm trong cookie HttpOnly do backend đặt.
 * Gặp 401 thì tự gọi /auth/refresh một lần (các request đồng thời dùng chung một lần refresh) rồi gửi lại.
 */
export const api = axios.create({ baseURL: '/api/v1', withCredentials: true });

let accessToken = null;
let refreshPromise = null;
let onSessionExpired = () => {};

export function setAccessToken(token) {
  accessToken = token;
}

export function setSessionExpiredHandler(fn) {
  onSessionExpired = fn;
}

/** Gọi refresh, nhiều nơi gọi cùng lúc chỉ gửi một request. Trả về AuthResponse hoặc null. */
export function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = axios
      .post('/api/v1/auth/refresh', null, { withCredentials: true })
      .then((res) => {
        accessToken = res.data.accessToken;
        return res.data;
      })
      .catch(() => {
        accessToken = null;
        return null;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;
    const isAuthCall = original?.url?.startsWith('/auth/');
    if (status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      const session = await refreshSession();
      if (session) {
        original.headers.Authorization = `Bearer ${session.accessToken}`;
        return api(original);
      }
      onSessionExpired();
    }
    return Promise.reject(error);
  },
);

/** Thông báo lỗi dễ đọc từ định dạng lỗi chung của backend. */
export function errorMessage(err, fallback = 'Có lỗi xảy ra, vui lòng thử lại') {
  const data = err?.response?.data;
  if (data?.errors?.length) return data.errors.map((e) => e.message).join('. ');
  if (data?.message) return data.message;
  if (!err?.response) return 'Không kết nối được máy chủ';
  return fallback;
}

export function errorCode(err) {
  return err?.response?.data?.code;
}
