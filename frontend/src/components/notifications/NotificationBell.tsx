import { useEffect, useState } from "react";
import { Bell } from "lucide-react";
import { formatDistanceToNowStrict } from "date-fns";
import { notificationApi } from "@/api/miscApi";
import type { NotificationResponse } from "@/types";
import { useChatSocket } from "@/hooks/useChatSocket";
import { EmptyState, Spinner } from "@/components/ui/States";

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationResponse[] | null>(null);
  const [unreadCount, setUnreadCount] = useState(0);
  const socket = useChatSocket();

  useEffect(() => {
    notificationApi.unreadCount().then(setUnreadCount).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!socket) return;
    const unsubscribe = socket.onNotification((notification) => {
      setUnreadCount((c) => c + 1);
      setNotifications((prev) => (prev ? [notification, ...prev] : prev));
    });
    return () => {
      unsubscribe();
    };
  }, [socket]);

  async function handleOpen() {
    setOpen((o) => !o);
    if (!open) {
      setNotifications(null);
      const page = await notificationApi.list();
      setNotifications(page.content);
    }
  }

  async function handleMarkAllRead() {
    await notificationApi.markAllRead();
    setUnreadCount(0);
    setNotifications((prev) => (prev ? prev.map((n) => ({ ...n, read: true })) : prev));
  }

  return (
    <div className="relative">
      <button
        onClick={handleOpen}
        className="relative rounded-full p-2 text-gray-500 transition-colors hover:bg-gray-100 hover:text-brand-600 dark:text-gray-400 dark:hover:bg-white/10 dark:hover:text-brand-300"
        aria-label="Notifications"
      >
        <Bell className="h-4.5 w-4.5" />
        {unreadCount > 0 && (
          <span className="absolute right-1 top-1 flex h-2 w-2">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent-pink opacity-75" />
            <span className="relative inline-flex h-2 w-2 rounded-full bg-accent-pink" />
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-20 mt-2 w-80 animate-pop-in rounded-2xl bg-white py-2 shadow-xl ring-1 ring-gray-200 dark:bg-surface-darkraise dark:ring-white/10">
          <div className="flex items-center justify-between px-3 pb-2">
            <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">Notifications</p>
            {unreadCount > 0 && (
              <button
                onClick={handleMarkAllRead}
                className="text-xs font-medium text-brand-600 hover:text-brand-700 dark:text-brand-300 dark:hover:text-brand-200"
              >
                Mark all read
              </button>
            )}
          </div>
          <div className="max-h-80 overflow-y-auto scrollbar-thin">
            {notifications === null && (
              <div className="flex justify-center py-6">
                <Spinner className="h-5 w-5" />
              </div>
            )}
            {notifications !== null && notifications.length === 0 && <EmptyState title="No notifications" />}
            {notifications?.map((n) => (
              <div
                key={n.id}
                className={`px-3 py-2 text-sm ${
                  n.read ? "" : "bg-gradient-to-r from-brand-50 to-transparent dark:from-brand-500/10"
                }`}
              >
                <p className="font-medium text-gray-900 dark:text-gray-100">{n.title}</p>
                {n.body && <p className="text-xs text-gray-500 dark:text-gray-400">{n.body}</p>}
                <p className="mt-0.5 text-[11px] text-gray-400 dark:text-gray-500">
                  {formatDistanceToNowStrict(new Date(n.createdAt), { addSuffix: true })}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
