import { useRef, useState, type FormEvent } from "react";
import { Paperclip, Send, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { storageApi } from "@/api/miscApi";

interface ComposerSendPayload {
  content: string | null;
  messageType: "TEXT" | "IMAGE" | "FILE";
  attachmentUrl?: string | null;
  attachmentName?: string | null;
  attachmentSizeBytes?: number | null;
  attachmentMimeType?: string | null;
}

interface ComposerProps {
  onSend: (payload: ComposerSendPayload) => void;
  onTypingChange: (typing: boolean) => void;
}

const TYPING_STOP_DELAY_MS = 2000;
const MAX_FILE_SIZE_BYTES = 15_000_000;

export function MessageComposer({ onSend, onTypingChange }: ComposerProps) {
  const [text, setText] = useState("");
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const typingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  function handleTextChange(value: string) {
    setText(value);
    onTypingChange(true);
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    typingTimeoutRef.current = setTimeout(() => onTypingChange(false), TYPING_STOP_DELAY_MS);
  }

  function handleFileSelected(file: File) {
    setUploadError(null);
    if (file.size > MAX_FILE_SIZE_BYTES) {
      setUploadError("File is too large (max 15MB)");
      return;
    }
    setPendingFile(file);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    onTypingChange(false);
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);

    if (pendingFile) {
      setUploading(true);
      try {
        const presigned = await storageApi.createPresignedUpload({
          fileName: pendingFile.name,
          contentType: pendingFile.type,
          sizeBytes: pendingFile.size,
        });
        const uploadResult = await storageApi.uploadToPresignedUrl(presigned.uploadUrl, pendingFile);
        if (!uploadResult.ok) {
          throw new Error("Upload failed");
        }
        onSend({
          content: text.trim() || null,
          messageType: pendingFile.type.startsWith("image/") ? "IMAGE" : "FILE",
          attachmentUrl: presigned.downloadUrl,
          attachmentName: pendingFile.name,
          attachmentSizeBytes: pendingFile.size,
          attachmentMimeType: pendingFile.type,
        });
        setPendingFile(null);
        setText("");
      } catch {
        setUploadError("Couldn't upload that file. Please try again.");
      } finally {
        setUploading(false);
      }
      return;
    }

    if (!text.trim()) return;
    onSend({ content: text.trim(), messageType: "TEXT" });
    setText("");
  }

  const canSend = !!text.trim() || !!pendingFile;

  return (
    <form
      onSubmit={handleSubmit}
      className="border-t border-gray-100 bg-white/80 p-3 backdrop-blur-xl dark:border-white/10 dark:bg-surface-dark/80"
    >
      {uploadError && <p className="mb-2 text-xs text-red-600 dark:text-red-400">{uploadError}</p>}

      {pendingFile && (
        <div className="mb-2 flex animate-pop-in items-center gap-2 rounded-lg bg-gray-50 px-3 py-2 text-sm dark:bg-white/5">
          <Paperclip className="h-4 w-4 shrink-0 text-gray-400" />
          <span className="flex-1 truncate">{pendingFile.name}</span>
          <button
            type="button"
            onClick={() => setPendingFile(null)}
            className="rounded-full p-0.5 text-gray-400 hover:bg-gray-200 dark:hover:bg-white/10"
            aria-label="Remove attachment"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      )}

      <div className="flex items-end gap-2">
        <input
          ref={fileInputRef}
          type="file"
          className="hidden"
          onChange={(e) => e.target.files?.[0] && handleFileSelected(e.target.files[0])}
        />
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading}
          className="shrink-0 rounded-full p-2.5 text-gray-500 transition-colors hover:bg-gray-100 hover:text-brand-600 disabled:opacity-40 dark:text-gray-400 dark:hover:bg-white/10 dark:hover:text-brand-300"
          aria-label="Attach a file"
        >
          <Paperclip className="h-5 w-5" />
        </button>

        <textarea
          value={text}
          onChange={(e) => handleTextChange(e.target.value)}
          onKeyDown={(e) => {
            // isComposing guards against IME text entry (e.g. Japanese,
            // Chinese, Korean input methods) where pressing Enter confirms
            // a character composition rather than submitting the message -
            // without this check, composing text would send prematurely.
            if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault();
              handleSubmit(e);
            }
          }}
          placeholder="Type a message…"
          rows={1}
          className="max-h-32 flex-1 resize-none rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-sm text-gray-900 placeholder:text-gray-400 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-500 dark:border-white/10 dark:bg-white/5 dark:text-gray-100 dark:placeholder:text-gray-500"
        />

        <Button
          type="submit"
          size="md"
          loading={uploading}
          disabled={!canSend}
          className={`shrink-0 !rounded-full !p-2.5 ${canSend ? "animate-glow-pulse" : ""}`}
        >
          <Send className="h-4 w-4" />
        </Button>
      </div>
    </form>
  );
}
