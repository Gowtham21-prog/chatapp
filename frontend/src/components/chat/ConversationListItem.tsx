import { clsx } from "clsx";
import { formatDistanceToNowStrict } from "date-fns";
import type { ConversationResponse } from "@/types";
import { Avatar } from "@/components/ui/Avatar";

export function ConversationListItem({
  conversation,
  active,
  onClick,
}: {
  conversation: ConversationResponse;
  active: boolean;
  onClick: () => void;
}) {
  const name = conversation.otherDisplayName ?? "Unknown user";
  const preview = conversation.lastMessage
    ? conversation.lastMessage.deleted
      ? "This message was deleted"
      : conversation.lastMessage.messageType === "TEXT"
        ? conversation.lastMessage.content
        : `📎 ${conversation.lastMessage.attachmentName ?? "Attachment"}`
    : "No messages yet";

  return (
    <button
      onClick={onClick}
      className={clsx(
        "flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-all duration-200",
        active
          ? "bg-gradient-to-r from-brand-500/15 to-accent-pink/10 ring-1 ring-brand-400/30 dark:from-brand-500/20 dark:to-accent-pink/10"
          : "hover:bg-gray-50 dark:hover:bg-white/5",
      )}
    >
      <Avatar name={name} src={conversation.otherAvatarUrl} online={conversation.otherOnline} showOnlineIndicator />
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <p className="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{name}</p>
          {conversation.lastMessage && (
            <span className="shrink-0 text-xs text-gray-400 dark:text-gray-500">
              {formatDistanceToNowStrict(new Date(conversation.lastMessage.createdAt), { addSuffix: false })}
            </span>
          )}
        </div>
        <div className="flex items-center justify-between gap-2">
          <p className="truncate text-xs text-gray-500 dark:text-gray-400">{preview}</p>
          {conversation.unreadCount > 0 && (
            <span className="flex h-5 min-w-5 shrink-0 animate-pop-in items-center justify-center rounded-full bg-gradient-to-br from-brand-500 to-accent-pink px-1.5 text-[11px] font-semibold text-white shadow-sm shadow-brand-500/40">
              {conversation.unreadCount > 99 ? "99+" : conversation.unreadCount}
            </span>
          )}
        </div>
      </div>
    </button>
  );
}
