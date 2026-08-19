import { apiClient } from "@/lib/apiClient";
import type { ConversationResponse, MessageResponse, PageResponse } from "@/types";

export const conversationApi = {
  list: () => apiClient.get<ConversationResponse[]>("/conversations").then((r) => r.data),

  startOrGet: (userId: string) =>
    apiClient.post<ConversationResponse>("/conversations", { userId }).then((r) => r.data),

  getById: (conversationId: string) =>
    apiClient.get<ConversationResponse>(`/conversations/${conversationId}`).then((r) => r.data),
};

export const messageApi = {
  history: (conversationId: string, page = 0, size = 30) =>
    apiClient
      .get<PageResponse<MessageResponse>>(`/conversations/${conversationId}/messages`, { params: { page, size } })
      .then((r) => r.data),

  searchInConversation: (conversationId: string, query: string, page = 0) =>
    apiClient
      .get<PageResponse<MessageResponse>>(`/conversations/${conversationId}/messages/search`, {
        params: { q: query, page },
      })
      .then((r) => r.data),

  searchAll: (query: string, page = 0) =>
    apiClient
      .get<PageResponse<MessageResponse>>("/messages/search", { params: { q: query, page } })
      .then((r) => r.data),

  deleteMessage: (messageId: string, forEveryone: boolean) =>
    apiClient.delete<void>(`/messages/${messageId}`, { params: { forEveryone } }).then((r) => r.data),

  markConversationRead: (conversationId: string) =>
    apiClient.post<void>(`/conversations/${conversationId}/read`).then((r) => r.data),
};
