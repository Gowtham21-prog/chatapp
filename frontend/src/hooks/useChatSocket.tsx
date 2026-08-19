import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { ChatSocket } from "@/lib/chatSocket";
import { useAuthStore } from "@/store/authStore";

const ChatSocketContext = createContext<ChatSocket | null>(null);

/**
 * Owns exactly one ChatSocket for the lifetime of a logged-in session,
 * reconnecting only when the access token actually changes (login/refresh),
 * not on every component that wants to send/receive. Components read the
 * shared instance via useChatSocket() instead of each creating their own
 * connection.
 */
export function ChatSocketProvider({ children }: { children: ReactNode }) {
  const accessToken = useAuthStore((s) => s.accessToken);
  const socketRef = useRef<ChatSocket | null>(null);
  const [, forceRender] = useState(0);

  useEffect(() => {
    if (!accessToken) {
      socketRef.current?.disconnect();
      socketRef.current = null;
      forceRender((n) => n + 1);
      return;
    }

    const socket = new ChatSocket(accessToken);
    socket.connect();
    socketRef.current = socket;
    forceRender((n) => n + 1);

    return () => {
      socket.disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken]);

  return <ChatSocketContext.Provider value={socketRef.current}>{children}</ChatSocketContext.Provider>;
}

export function useChatSocket(): ChatSocket | null {
  return useContext(ChatSocketContext);
}
