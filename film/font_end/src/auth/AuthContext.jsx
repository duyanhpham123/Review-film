import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { refreshSession, setAccessToken, setSessionExpiredHandler } from '../api/client';
import { authApi, userApi } from '../api/endpoints';

const AuthContext = createContext(null);

/**
 * Khi mở trang: thử /auth/refresh bằng cookie để khôi phục phiên (access token không lưu lại sau F5).
 * user = undefined: đang kiểm tra; null: chưa đăng nhập.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(undefined);
  const queryClient = useQueryClient();

  useEffect(() => {
    setSessionExpiredHandler(() => {
      setAccessToken(null);
      setUser(null);
    });
    refreshSession().then((session) => setUser(session ? session.user : null));
  }, []);

  const login = useCallback(async (login, password, rememberMe) => {
    const res = await authApi.login({ login, password, rememberMe });
    setAccessToken(res.accessToken);
    setUser(res.user);
    queryClient.invalidateQueries();
    return res.user;
  }, [queryClient]);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setAccessToken(null);
      setUser(null);
      queryClient.clear();
    }
  }, [queryClient]);

  const reloadUser = useCallback(async () => {
    const me = await userApi.me();
    setUser(me);
    return me;
  }, []);

  const value = useMemo(() => {
    const roles = user?.roles || [];
    return {
      user,
      loading: user === undefined,
      isAdmin: roles.includes('ROLE_ADMIN'),
      isModerator: roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MODERATOR'),
      login,
      logout,
      reloadUser,
      setUser,
    };
  }, [user, login, logout, reloadUser]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
