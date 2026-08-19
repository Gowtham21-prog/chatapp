import { apiClient } from "@/lib/apiClient";
import type { UserProfile, UserSearchResult } from "@/types";

export const userApi = {
  me: () => apiClient.get<UserProfile>("/users/me").then((r) => r.data),

  updateProfile: (data: { displayName?: string; bio?: string; avatarUrl?: string }) =>
    apiClient.patch<UserProfile>("/users/me", data).then((r) => r.data),

  getProfile: (userId: string) => apiClient.get<UserProfile>(`/users/${userId}`).then((r) => r.data),

  search: (query: string) =>
    apiClient.get<UserSearchResult[]>("/users/search", { params: { q: query } }).then((r) => r.data),
};
