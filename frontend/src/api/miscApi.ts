import axios from "axios";
import { apiClient } from "@/lib/apiClient";
import type {
  AnonymousSessionResponse,
  MatchResultResponse,
  NotificationResponse,
  PageResponse,
} from "@/types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

export const storageApi = {
  createPresignedUpload: (data: { fileName: string; contentType: string; sizeBytes: number }) =>
    apiClient
      .post<{ uploadUrl: string; downloadUrl: string; objectKey: string; expiresInSeconds: number }>(
        "/storage/presigned-upload",
        data,
      )
      .then((r) => r.data),

  /** Uploads directly to MinIO/S3 using the presigned URL - never touches our backend. */
  uploadToPresignedUrl: (uploadUrl: string, file: File) =>
    fetch(uploadUrl, { method: "PUT", body: file, headers: { "Content-Type": file.type } }),
};

export const moderationApi = {
  blockUser: (userId: string) => apiClient.post<void>("/blocks", { userId }).then((r) => r.data),
  unblockUser: (userId: string) => apiClient.delete<void>(`/blocks/${userId}`).then((r) => r.data),
  listBlocked: () => apiClient.get("/blocks").then((r) => r.data),
  report: (data: {
    context: "DIRECT" | "ANONYMOUS";
    reportedUserId?: string;
    reportedAnonymousId?: string;
    reason: string;
    details?: string;
    messageId?: string;
  }) => apiClient.post<void>("/reports", data).then((r) => r.data),
};

export const notificationApi = {
  list: (page = 0) =>
    apiClient.get<PageResponse<NotificationResponse>>("/notifications", { params: { page } }).then((r) => r.data),
  unreadCount: () => apiClient.get<number>("/notifications/unread-count").then((r) => r.data),
  markAllRead: () => apiClient.post<void>("/notifications/mark-all-read").then((r) => r.data),
};

// Anonymous endpoints use bare axios (not the JWT-bearing apiClient) since
// anonymous sessions are unauthenticated by design - no Authorization
// header, no refresh token, just the X-Anonymous-Token header.
export const anonymousApi = {
  createSession: (interests: string[]) =>
    axios
      .post<AnonymousSessionResponse>(`${API_BASE_URL}/anonymous/session`, { interests })
      .then((r) => r.data),

  requestMatch: (token: string) =>
    axios
      .post<MatchResultResponse>(`${API_BASE_URL}/anonymous/match`, null, {
        headers: { "X-Anonymous-Token": token },
      })
      .then((r) => r.data),

  pollMatch: (token: string) =>
    axios
      .get<MatchResultResponse>(`${API_BASE_URL}/anonymous/match`, {
        headers: { "X-Anonymous-Token": token },
      })
      .then((r) => r.data),

  next: (token: string) =>
    axios
      .post<MatchResultResponse>(`${API_BASE_URL}/anonymous/next`, null, {
        headers: { "X-Anonymous-Token": token },
      })
      .then((r) => r.data),

  leave: (token: string) =>
    axios.post<void>(`${API_BASE_URL}/anonymous/leave`, null, {
      headers: { "X-Anonymous-Token": token },
    }),

  blockCurrentPartner: (token: string) =>
    axios.post<void>(`${API_BASE_URL}/anonymous/block`, null, {
      headers: { "X-Anonymous-Token": token },
    }),

  reportAnonymous: (token: string, data: { reportedAnonymousId: string; reason: string; details?: string }) =>
    axios.post<void>(
      `${API_BASE_URL}/reports/anonymous`,
      { context: "ANONYMOUS", ...data },
      { headers: { "X-Anonymous-Token": token } },
    ),
};
