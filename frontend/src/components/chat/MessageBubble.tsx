import { useState } from "react";
import { clsx } from "clsx";
import { format } from "date-fns";
import { Check, CheckCheck, MoreVertical, Trash2, Download } from "lucide-react";
import type { MessageResponse } from "@/types";

export function MessageBubble({
  message,
  isOwn,
  onDelete,
}: {
  message: MessageResponse;
  isOwn: boolean;
  onDelete: (forEveryone: boolean) => void;
}) {
  const [menuOpen, setMenuOpen] = useState(false);

  if (message.deleted) {
    return (
      <div className={clsx("flex", isOwn ? "justify-end" : "justify-start")}>
        <div className="max-w-[75%] rounded-2xl bg-gray-100 px-4 py-2 text-sm italic text-gray-400 dark:bg-white/5 dark:text-gray-500">
          This message was deleted
        </div>
      </div>
    );
  }

  return (
    <div className={clsx("group flex", isOwn ? "justify-end" : "justify-start")}>
      <div className={clsx("flex max-w-[75%] items-end gap-1", isOwn && "flex-row-reverse")}>
        <div
          className={clsx(
            "animate-pop-in rounded-2xl px-4 py-2 text-sm shadow-sm",
            isOwn
              ? "rounded-br-sm bg-gradient-to-br from-brand-600 to-accent-pink text-white shadow-brand-500/25"
              : "rounded-bl-sm bg-white text-gray-900 ring-1 ring-gray-100 dark:bg-surface-darkcard dark:text-gray-100 dark:ring-white/10",
          )}
        >
          {message.messageType === "TEXT" && <p className="whitespace-pre-wrap break-words">{message.content}</p>}

          {message.messageType === "IMAGE" && message.attachmentUrl && (
            <a href={message.attachmentUrl} target="_blank" rel="noreferrer">
              <img
                src={message.attachmentUrl}
                alt={message.attachmentName ?? "Shared image"}
                className="max-h-64 rounded-lg object-cover"
              />
            </a>
          )}

          {message.messageType === "FILE" && message.attachmentUrl && (
            <a
              href={message.attachmentUrl}
              target="_blank"
              rel="noreferrer"
              className={clsx(
                "flex items-center gap-2 rounded-lg px-2 py-1.5",
                isOwn ? "bg-white/15" : "bg-gray-50 dark:bg-white/5",
              )}
            >
              <Download className="h-4 w-4 shrink-0" />
              <span className="truncate text-sm">{message.attachmentName ?? "File"}</span>
            </a>
          )}

          <div
            className={clsx(
              "mt-1 flex items-center gap-1 text-[11px]",
              isOwn ? "text-white/80" : "text-gray-400 dark:text-gray-500",
            )}
          >
            <span>{format(new Date(message.createdAt), "HH:mm")}</span>
            {isOwn && (
              <>
                {message.status === "READ" ? (
                  <CheckCheck className="h-3.5 w-3.5 text-accent-lime" />
                ) : message.status === "DELIVERED" ? (
                  <CheckCheck className="h-3.5 w-3.5 opacity-70" />
                ) : (
                  <Check className="h-3.5 w-3.5 opacity-70" />
                )}
              </>
            )}
          </div>
        </div>

        {isOwn && (
          <div className="relative opacity-0 transition-opacity group-hover:opacity-100">
            <button
              onClick={() => setMenuOpen((o) => !o)}
              className="rounded-full p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:text-gray-500 dark:hover:bg-white/10 dark:hover:text-gray-300"
              aria-label="Message options"
            >
              <MoreVertical className="h-4 w-4" />
            </button>
            {menuOpen && (
              <div className="absolute right-0 z-10 mt-1 w-44 rounded-xl bg-white py-1 text-sm shadow-lg ring-1 ring-gray-200 dark:bg-surface-darkraise dark:ring-white/10">
                <button
                  onClick={() => {
                    onDelete(false);
                    setMenuOpen(false);
                  }}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-gray-200 dark:hover:bg-white/5"
                >
                  <Trash2 className="h-3.5 w-3.5" /> Delete for me
                </button>
                <button
                  onClick={() => {
                    onDelete(true);
                    setMenuOpen(false);
                  }}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10"
                >
                  <Trash2 className="h-3.5 w-3.5" /> Delete for everyone
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
