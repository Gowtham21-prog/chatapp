import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AuthResponse, UserProfile } from "@/types";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserProfile | null;
  setSession: (auth: AuthResponse) => void;
  updateUser: (user: UserProfile) => void;
  clearSession: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setSession: (auth) =>
        set({ accessToken: auth.accessToken, refreshToken: auth.refreshToken, user: auth.user }),
      updateUser: (user) => set({ user }),
      clearSession: () => set({ accessToken: null, refreshToken: null, user: null }),
      isAuthenticated: () => get().accessToken !== null,
    }),
    { name: "chatapp-auth" },
  ),
);
