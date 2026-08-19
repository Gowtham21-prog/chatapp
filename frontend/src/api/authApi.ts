import { apiClient } from "@/lib/apiClient";
import type { AuthResponse } from "@/types";

export const authApi = {
  register: (data: { username: string; email: string; password: string; displayName: string }) =>
    apiClient.post<AuthResponse>("/auth/register", data).then((r) => r.data),

  login: (data: { usernameOrEmail: string; password: string }) =>
    apiClient.post<AuthResponse>("/auth/login", data).then((r) => r.data),

  logout: () => apiClient.post<void>("/auth/logout").then((r) => r.data),
};
