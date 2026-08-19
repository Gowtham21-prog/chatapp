export interface UserProfile {
  id: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  bio: string | null;
  createdAt: string;
}

export interface UserSearchResult {
  id: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  online: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: UserProfile;
}

export type MessageType = "TEXT" | "IMAGE" | "FILE";
export type MessageStatus = "SENT" | "DELIVERED" | "READ";

export interface MessageResponse {
  id: string;
  conversationId: string;
  senderId: string;
  content: string | null;
  messageType: MessageType;
  attachmentUrl: string | null;
  attachmentName: string | null;
  attachmentSizeBytes: number | null;
  attachmentMimeType: string | null;
  status: MessageStatus;
  deleted: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationResponse {
  id: string;
  otherUserId: string | null;
  otherUsername: string | null;
  otherDisplayName: string | null;
  otherAvatarUrl: string | null;
  otherOnline: boolean;
  lastMessage: MessageResponse | null;
  unreadCount: number;
  updatedAt: string;
}

export interface TypingEvent {
  conversationId: string;
  userId: string;
  typing: boolean;
}

export interface ReadReceiptEvent {
  conversationId: string;
  messageId: string;
  readByUserId: string;
  readAt: string;
}

export interface PresenceUpdateMessage {
  userId: string;
  online: boolean;
}

export interface NotificationResponse {
  id: string;
  type: "NEW_MESSAGE" | "MENTION" | "SYSTEM";
  title: string;
  body: string | null;
  metadata: Record<string, unknown> | null;
  read: boolean;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  last: boolean;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}

// ---- Anonymous chat ----

export interface AnonymousSessionResponse {
  sessionId: string;
  accessToken: string;
  interests: string[];
  expiresInSeconds: number;
}

export type MatchStatus = "WAITING" | "MATCHED";

export interface MatchResultResponse {
  status: MatchStatus;
  roomId: string | null;
  partnerSessionId: string | null;
  sharedInterests: string[] | null;
}

export type AnonymousEventType = "MESSAGE" | "TYPING" | "PARTNER_LEFT" | "PARTNER_DISCONNECTED";

export interface AnonymousChatEvent {
  type: AnonymousEventType;
  roomId: string;
  senderSessionId: string;
  content: string | null;
  timestamp: string;
}
