import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, LogOut, Search, UserPlus } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { useChatSocket } from "@/hooks/useChatSocket";
import { conversationApi, messageApi } from "@/api/conversationApi";
import { userApi } from "@/api/userApi";
import { authApi } from "@/api/authApi";
import type { ConversationResponse, MessageResponse, MessageType, UserSearchResult } from "@/types";
import { ConversationListItem } from "@/components/chat/ConversationListItem";
import { MessageBubble } from "@/components/chat/MessageBubble";
import { MessageComposer } from "@/components/chat/MessageComposer";
import { TypingIndicator } from "@/components/chat/TypingIndicator";
import { NotificationBell } from "@/components/notifications/NotificationBell";
import { Avatar } from "@/components/ui/Avatar";
import { EmptyState, ErrorState, FullPageLoader, Spinner } from "@/components/ui/States";
import { Input } from "@/components/ui/Input";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import { StreakBadge } from "@/components/ui/StreakBadge";
import { extractErrorMessage } from "@/lib/apiClient";

interface SendPayload {
  content: string | null;
  messageType: MessageType;
  attachmentUrl?: string | null;
  attachmentName?: string | null;
  attachmentSizeBytes?: number | null;
  attachmentMimeType?: string | null;
}

export default function ChatPage() {
  const navigate = useNavigate();
  const { conversationId } = useParams<{ conversationId: string }>();
  const currentUser = useAuthStore((s) => s.user);
  const clearSession = useAuthStore((s) => s.clearSession);
  const socket = useChatSocket();

  const [conversations, setConversations] = useState<ConversationResponse[] | null>(null);
  const [conversationsError, setConversationsError] = useState<string | null>(null);
  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messagesError, setMessagesError] = useState<string | null>(null);
  const [partnerTyping, setPartnerTyping] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<UserSearchResult[]>([]);
  const [searching, setSearching] = useState(false);

  const scrollRef = useRef<HTMLDivElement>(null);
  const activeConversation = conversations?.find((c) => c.id === conversationId) ?? null;

  useEffect(() => {
    loadConversations();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function loadConversations() {
    setConversationsError(null);
    try {
      const data = await conversationApi.list();
      setConversations(data);
    } catch (err) {
      setConversationsError(extractErrorMessage(err));
    }
  }

  useEffect(() => {
    if (!conversationId) {
      setMessages([]);
      return;
    }
    setMessagesLoading(true);
    setMessagesError(null);
    messageApi
      .history(conversationId)
      .then((page) => {
        setMessages([...page.content].reverse());
        return messageApi.markConversationRead(conversationId);
      })
      .then(() => loadConversations())
      .catch((err) => setMessagesError(extractErrorMessage(err)))
      .finally(() => setMessagesLoading(false));
  }, [conversationId]);

  useEffect(() => {
    if (!socket) return;

    const unsubMessage = socket.onMessage((message) => {
      if (message.conversationId === conversationId) {
        setMessages((prev) => [...prev, message]);
        if (message.senderId !== currentUser?.id) {
          socket.markDelivered(message.id);
          socket.markRead(message.id);
        }
      }
      loadConversations();
    });

    const unsubTyping = socket.onTyping((event) => {
      if (event.conversationId === conversationId) {
        setPartnerTyping(event.typing);
      }
    });

    const unsubDelivered = socket.onDeliveryReceipt((event) => {
      setMessages((prev) =>
        prev.map((m) => (m.id === event.messageId && m.status === "SENT" ? { ...m, status: "DELIVERED" } : m)),
      );
    });

    const unsubRead = socket.onReadReceipt((event) => {
      setMessages((prev) => prev.map((m) => (m.id === event.messageId ? { ...m, status: "READ" } : m)));
    });

    return () => {
      unsubMessage();
      unsubTyping();
      unsubDelivered();
      unsubRead();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [socket, conversationId, currentUser?.id]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, partnerTyping]);

  const debouncedSearch = useMemo(() => {
    let timeout: ReturnType<typeof setTimeout>;
    return (query: string) => {
      clearTimeout(timeout);
      if (!query.trim()) {
        setSearchResults([]);
        return;
      }
      timeout = setTimeout(async () => {
        setSearching(true);
        try {
          setSearchResults(await userApi.search(query));
        } finally {
          setSearching(false);
        }
      }, 300);
    };
  }, []);

  async function handleStartConversation(userId: string) {
    const conversation = await conversationApi.startOrGet(userId);
    setSearchOpen(false);
    setSearchQuery("");
    setSearchResults([]);
    await loadConversations();
    navigate(`/chat/${conversation.id}`);
  }

  function handleSend(payload: SendPayload) {
    if (!conversationId || !socket) return;
    socket.sendMessage({ conversationId, ...payload });
  }

  function handleTypingChange(typing: boolean) {
    if (!conversationId || !socket || !currentUser) return;
    socket.sendTyping(conversationId, currentUser.id, typing);
  }

  async function handleDeleteMessage(messageId: string, forEveryone: boolean) {
    await messageApi.deleteMessage(messageId, forEveryone);
    setMessages((prev) =>
      prev.map((m) => (m.id === messageId ? { ...m, deleted: true, content: null, attachmentUrl: null } : m)),
    );
  }

  async function handleLogout() {
    try {
      await authApi.logout();
    } finally {
      clearSession();
      navigate("/login");
    }
  }

  return (
    <div className="flex h-screen overflow-hidden bg-surface-light dark:bg-surface-dark">
      <aside
        className={`w-full shrink-0 border-r border-gray-100 bg-white/90 backdrop-blur-xl dark:border-white/10 dark:bg-surface-darkcard/60 sm:w-80 ${
          conversationId ? "hidden sm:flex sm:flex-col" : "flex flex-col"
        }`}
      >
        <div className="flex items-center justify-between gap-2 border-b border-gray-100 bg-gradient-to-r from-brand-500/5 to-accent-pink/5 p-4 dark:border-white/10">
          <div className="flex min-w-0 items-center gap-2">
            <Avatar name={currentUser?.displayName ?? "?"} src={currentUser?.avatarUrl} size="sm" online showOnlineIndicator />
            <p className="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">{currentUser?.displayName}</p>
          </div>
          <div className="flex shrink-0 items-center gap-1">
            <StreakBadge />
            <ThemeToggle className="scale-90" />
            <NotificationBell />
            <button
              onClick={() => setSearchOpen((o) => !o)}
              className="rounded-full p-2 text-gray-500 transition-colors hover:bg-gray-100 hover:text-brand-600 dark:text-gray-400 dark:hover:bg-white/10 dark:hover:text-brand-300"
              aria-label="Start new conversation"
            >
              <UserPlus className="h-4.5 w-4.5" />
            </button>
            <button
              onClick={handleLogout}
              className="rounded-full p-2 text-gray-500 transition-colors hover:bg-red-50 hover:text-red-500 dark:text-gray-400 dark:hover:bg-red-500/10 dark:hover:text-red-400"
              aria-label="Log out"
            >
              <LogOut className="h-4.5 w-4.5" />
            </button>
          </div>
        </div>

        {searchOpen && (
          <div className="animate-pop-in border-b border-gray-100 p-3 dark:border-white/10">
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
              <Input
                placeholder="Search users…"
                className="pl-9"
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  debouncedSearch(e.target.value);
                }}
                autoFocus
              />
            </div>
            {searching && (
              <div className="flex justify-center py-2">
                <Spinner className="h-4 w-4" />
              </div>
            )}
            {!searching && searchResults.length > 0 && (
              <div className="mt-2 space-y-1">
                {searchResults.map((user) => (
                  <button
                    key={user.id}
                    onClick={() => handleStartConversation(user.id)}
                    className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left hover:bg-gray-50 dark:hover:bg-white/5"
                  >
                    <Avatar
                      name={user.displayName}
                      src={user.avatarUrl}
                      size="sm"
                      online={user.online}
                      showOnlineIndicator
                    />
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{user.displayName}</p>
                      <p className="truncate text-xs text-gray-400 dark:text-gray-500">@{user.username}</p>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        <div className="flex-1 overflow-y-auto scrollbar-thin p-2">
          {conversations === null && !conversationsError && <FullPageLoader />}
          {conversationsError && <ErrorState message={conversationsError} onRetry={loadConversations} />}
          {conversations !== null && conversations.length === 0 && (
            <EmptyState
              title="No conversations yet"
              description="Search for someone to start chatting."
              icon={<UserPlus className="h-10 w-10" />}
            />
          )}
          {conversations?.map((c) => (
            <ConversationListItem
              key={c.id}
              conversation={c}
              active={c.id === conversationId}
              onClick={() => navigate(`/chat/${c.id}`)}
            />
          ))}
        </div>
      </aside>

      <main className={`aurora-bg flex flex-1 flex-col bg-surface-light dark:bg-surface-dark ${conversationId ? "flex" : "hidden sm:flex"}`}>
        {!conversationId && (
          <EmptyState title="Select a conversation" description="Pick someone from the list to start chatting." />
        )}

        {conversationId && (
          <>
            <div className="flex items-center gap-3 border-b border-gray-100 bg-white/80 p-3 backdrop-blur-xl dark:border-white/10 dark:bg-surface-dark/70">
              <button
                onClick={() => navigate("/chat")}
                className="rounded-full p-2 text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-white/10 sm:hidden"
                aria-label="Back to conversations"
              >
                <ArrowLeft className="h-5 w-5" />
              </button>
              {activeConversation && (
                <>
                  <Avatar
                    name={activeConversation.otherDisplayName ?? "?"}
                    src={activeConversation.otherAvatarUrl}
                    online={activeConversation.otherOnline}
                    showOnlineIndicator
                  />
                  <div>
                    <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">{activeConversation.otherDisplayName}</p>
                    <p className={`text-xs ${activeConversation.otherOnline ? "text-accent-lime font-medium" : "text-gray-400 dark:text-gray-500"}`}>
                      {activeConversation.otherOnline ? "● Online" : "Offline"}
                    </p>
                  </div>
                </>
              )}
            </div>

            <div ref={scrollRef} className="flex-1 space-y-2 overflow-y-auto scrollbar-thin p-4">
              {messagesLoading && <FullPageLoader />}
              {messagesError && <ErrorState message={messagesError} />}
              {!messagesLoading && !messagesError && messages.length === 0 && (
                <EmptyState title="No messages yet" description="Say hello 👋" />
              )}
              {messages.map((m) => (
                <MessageBubble
                  key={m.id}
                  message={m}
                  isOwn={m.senderId === currentUser?.id}
                  onDelete={(forEveryone) => handleDeleteMessage(m.id, forEveryone)}
                />
              ))}
              {partnerTyping && <TypingIndicator />}
            </div>

            <MessageComposer onSend={handleSend} onTypingChange={handleTypingChange} />
          </>
        )}
      </main>
    </div>
  );
}
