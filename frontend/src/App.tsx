import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { ChatSocketProvider } from "@/hooks/useChatSocket";
import { RequireAuth } from "@/components/layout/RequireAuth";
import LoginPage from "@/pages/LoginPage";
import RegisterPage from "@/pages/RegisterPage";
import ChatPage from "@/pages/ChatPage";
import AnonymousChatPage from "@/pages/AnonymousChatPage";

export default function App() {
  return (
    <BrowserRouter>
      <ChatSocketProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/anonymous" element={<AnonymousChatPage />} />
          <Route
            path="/chat"
            element={
              <RequireAuth>
                <ChatPage />
              </RequireAuth>
            }
          />
          <Route
            path="/chat/:conversationId"
            element={
              <RequireAuth>
                <ChatPage />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Navigate to="/chat" replace />} />
        </Routes>
      </ChatSocketProvider>
    </BrowserRouter>
  );
}
