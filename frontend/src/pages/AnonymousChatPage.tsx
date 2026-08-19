import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Ban, Flag, MessageCircle, SkipForward, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Spinner } from "@/components/ui/States";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import { anonymousApi } from "@/api/miscApi";
import { AnonymousChatSocket } from "@/lib/anonymousChatSocket";
import type { AnonymousChatEvent } from "@/types";
import { extractErrorMessage } from "@/lib/apiClient";

type Stage = "setup" | "waiting" | "matched" | "ended";

interface LocalMessage {
  id: string;
  fromSelf: boolean;
  content: string;
  timestamp: string;
}

export default function AnonymousChatPage() {
  const [stage, setStage] = useState<Stage>("setup");
  const [interestInput, setInterestInput] = useState("");
  const [interests, setInterests] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [connecting, setConnecting] = useState(false);

  const [token, setToken] = useState<string | null>(null);
  const [partnerSessionId, setPartnerSessionId] = useState<string | null>(null);
  const [sharedInterests, setSharedInterests] = useState<string[]>([]);
  const [endReason, setEndReason] = useState<string | null>(null);
  const [reportSent, setReportSent] = useState(false);

  const [localMessages, setLocalMessages] = useState<LocalMessage[]>([]);
  const [partnerTyping, setPartnerTyping] = useState(false);
  const [draft, setDraft] = useState("");

  const socketRef = useRef<AnonymousChatSocket | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const typingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // sessionId is read inside the onEvent callback registered in
  // connectSocket; refs avoid stale-closure bugs without re-subscribing.
  const sessionIdRef = useRef<string | null>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [localMessages, partnerTyping]);

  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
      socketRef.current?.disconnect();
    };
  }, []);

  function addInterest() {
    const value = interestInput.trim().toLowerCase();
    if (value && !interests.includes(value) && interests.length < 10) {
      setInterests((prev) => [...prev, value]);
    }
    setInterestInput("");
  }

  const connectSocket = useCallback((anonymousToken: string) => {
    const socket = new AnonymousChatSocket(anonymousToken);
    socket.onEvent((event: AnonymousChatEvent) => {
      if (event.type === "MESSAGE" && event.content !== null) {
        setLocalMessages((prev) => [
          ...prev,
          {
            id: crypto.randomUUID(),
            fromSelf: event.senderSessionId === sessionIdRef.current,
            content: event.content!,
            timestamp: event.timestamp,
          },
        ]);
        setPartnerTyping(false);
      } else if (event.type === "TYPING") {
        setPartnerTyping(event.content === "true");
      } else if (event.type === "PARTNER_LEFT" || event.type === "PARTNER_DISCONNECTED") {
        setEndReason("Your chat partner has left.");
        setStage("ended");
      }
    });
    socket.connect();
    socketRef.current = socket;
  }, []);

  async function startMatching() {
    setError(null);
    setConnecting(true);
    try {
      const session = await anonymousApi.createSession(interests);
      setToken(session.accessToken);
      sessionIdRef.current = session.sessionId;

      const result = await anonymousApi.requestMatch(session.accessToken);
      if (result.status === "MATCHED") {
        enterMatchedState(session.accessToken, result.partnerSessionId, result.sharedInterests ?? []);
      } else {
        setStage("waiting");
        pollRef.current = setInterval(async () => {
          const poll = await anonymousApi.pollMatch(session.accessToken);
          if (poll.status === "MATCHED") {
            if (pollRef.current) clearInterval(pollRef.current);
            enterMatchedState(session.accessToken, poll.partnerSessionId, poll.sharedInterests ?? []);
          }
        }, 1500);
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setConnecting(false);
    }
  }

  function enterMatchedState(anonymousToken: string, partnerId: string | null, shared: string[]) {
    setPartnerSessionId(partnerId);
    setSharedInterests(shared);
    setLocalMessages([]);
    setReportSent(false);
    setStage("matched");
    connectSocket(anonymousToken);
  }

  function handleDraftChange(value: string) {
    setDraft(value);
    socketRef.current?.sendTyping(true);
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    typingTimeoutRef.current = setTimeout(() => socketRef.current?.sendTyping(false), 2000);
  }

  function handleSend() {
    if (!draft.trim() || !socketRef.current) return;
    socketRef.current.sendMessage(draft.trim());
    setDraft("");
    socketRef.current.sendTyping(false);
  }

  async function requeue(anonymousToken: string) {
    setStage("waiting");
    setEndReason(null);
    const result = await anonymousApi.next(anonymousToken);
    if (result.status === "MATCHED") {
      enterMatchedState(anonymousToken, result.partnerSessionId, result.sharedInterests ?? []);
    } else {
      pollRef.current = setInterval(async () => {
        const poll = await anonymousApi.pollMatch(anonymousToken);
        if (poll.status === "MATCHED") {
          if (pollRef.current) clearInterval(pollRef.current);
          enterMatchedState(anonymousToken, poll.partnerSessionId, poll.sharedInterests ?? []);
        }
      }, 1500);
    }
  }

  async function handleNext() {
    if (!token) return;
    socketRef.current?.disconnect();
    setLocalMessages([]);
    await requeue(token);
  }

  async function handleLeave() {
    if (token) await anonymousApi.leave(token).catch(() => undefined);
    socketRef.current?.disconnect();
    if (pollRef.current) clearInterval(pollRef.current);
    setStage("setup");
    setLocalMessages([]);
    setToken(null);
    setPartnerSessionId(null);
    sessionIdRef.current = null;
  }

  async function handleBlock() {
    if (!token) return;
    await anonymousApi.blockCurrentPartner(token).catch(() => undefined);
    setEndReason("You blocked this person. They won't be matched with you again.");
    socketRef.current?.disconnect();
    setStage("ended");
  }

  async function handleReport() {
    if (!token || !partnerSessionId || reportSent) return;
    try {
      await anonymousApi.reportAnonymous(token, { reportedAnonymousId: partnerSessionId, reason: "OTHER" });
      setReportSent(true);
    } catch {
      // Reporting failures are non-blocking for the chat experience; the
      // person can still block/leave even if the report call fails.
    }
  }

  if (stage === "setup") {
    return (
      <div className="aurora-bg flex min-h-screen items-center justify-center bg-surface-light px-4 dark:bg-surface-dark">
        <div className="absolute right-4 top-4">
          <ThemeToggle />
        </div>
        <div className="glass-strong w-full max-w-sm rounded-2xl p-6 shadow-xl shadow-brand-900/5 dark:shadow-black/40">
          <div className="mb-6 flex flex-col items-center gap-2 text-center">
            <div className="rounded-2xl bg-gradient-to-br from-gray-900 to-brand-700 p-3 text-white shadow-lg dark:from-brand-600 dark:to-accent-cyan">
              <MessageCircle className="h-6 w-6" />
            </div>
            <h1 className="font-display text-xl font-bold text-gray-900 dark:text-gray-50">Anonymous chat</h1>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              No account needed. Get matched instantly, chat, and move on whenever you like.
            </p>
          </div>

          <label className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Interests (optional)</label>
          <div className="flex gap-2">
            <Input
              placeholder="e.g. music, hiking"
              value={interestInput}
              onChange={(e) => setInterestInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.nativeEvent.isComposing) {
                  e.preventDefault();
                  addInterest();
                }
              }}
            />
            <Button type="button" variant="secondary" onClick={addInterest}>
              Add
            </Button>
          </div>
          {interests.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1.5">
              {interests.map((interest) => (
                <span
                  key={interest}
                  className="flex animate-pop-in items-center gap-1 rounded-full bg-gradient-to-r from-brand-100 to-accent-cyan/20 px-2.5 py-1 text-xs text-brand-700 dark:from-brand-500/20 dark:to-accent-cyan/10 dark:text-brand-200"
                >
                  {interest}
                  <button onClick={() => setInterests((prev) => prev.filter((i) => i !== interest))}>
                    <X className="h-3 w-3" />
                  </button>
                </span>
              ))}
            </div>
          )}

          {error && (
            <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300">{error}</p>
          )}

          <Button className="mt-5 w-full" size="lg" loading={connecting} onClick={startMatching}>
            Start chatting
          </Button>

          <p className="mt-4 text-center text-xs text-gray-400 dark:text-gray-500">
            Be respectful. You can block or report anyone, any time.
          </p>
          <p className="mt-2 text-center text-sm">
            <Link to="/login" className="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200">
              ← Back to sign in
            </Link>
          </p>
        </div>
      </div>
    );
  }

  if (stage === "waiting") {
    return (
      <div className="aurora-bg flex min-h-screen flex-col items-center justify-center gap-4 bg-surface-light px-4 text-center dark:bg-surface-dark">
        <div className="relative">
          <span className="absolute inset-0 animate-ring-expand rounded-full bg-brand-500/40" />
          <Spinner className="relative h-10 w-10" />
        </div>
        <p className="font-display font-medium text-gray-700 dark:text-gray-200">Looking for someone to chat with…</p>
        <Button variant="secondary" onClick={handleLeave}>
          Cancel
        </Button>
      </div>
    );
  }

  if (stage === "ended") {
    return (
      <div className="aurora-bg flex min-h-screen flex-col items-center justify-center gap-4 bg-surface-light px-4 text-center dark:bg-surface-dark">
        <p className="font-medium text-gray-700 dark:text-gray-200">{endReason ?? "Chat ended."}</p>
        <div className="flex gap-2">
          <Button onClick={() => token && requeue(token)}>Find someone new</Button>
          <Button variant="secondary" onClick={handleLeave}>
            Leave
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen flex-col bg-surface-light dark:bg-surface-dark">
      <div className="flex items-center justify-between border-b border-gray-100 bg-white/90 p-3 backdrop-blur-xl dark:border-white/10 dark:bg-surface-darkcard/80">
        <div>
          <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">Anonymous stranger</p>
          {sharedInterests.length > 0 && (
            <p className="text-xs text-gray-400 dark:text-gray-500">Shared interests: {sharedInterests.join(", ")}</p>
          )}
        </div>
        <div className="flex items-center gap-1">
          <ThemeToggle className="scale-90" />
          <button
            onClick={handleReport}
            disabled={reportSent}
            className="rounded-full p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600 disabled:opacity-40 dark:hover:bg-white/10 dark:hover:text-gray-200"
            aria-label="Report"
            title={reportSent ? "Reported" : "Report"}
          >
            <Flag className="h-4 w-4" />
          </button>
          <button
            onClick={handleBlock}
            className="rounded-full p-2 text-gray-400 hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-500/10 dark:hover:text-red-400"
            aria-label="Block"
            title="Block"
          >
            <Ban className="h-4 w-4" />
          </button>
          <Button size="sm" variant="secondary" onClick={handleNext}>
            <SkipForward className="h-3.5 w-3.5" /> Next
          </Button>
        </div>
      </div>

      <div ref={scrollRef} className="flex-1 space-y-2 overflow-y-auto scrollbar-thin p-4">
        {localMessages.length === 0 && (
          <p className="mt-8 text-center text-sm text-gray-400 dark:text-gray-500">You're connected. Say hi 👋</p>
        )}
        {localMessages.map((m) => (
          <div key={m.id} className={`flex ${m.fromSelf ? "justify-end" : "justify-start"}`}>
            <div
              className={`max-w-[75%] animate-pop-in rounded-2xl px-4 py-2 text-sm shadow-sm ${
                m.fromSelf
                  ? "rounded-br-sm bg-gradient-to-br from-gray-900 to-brand-700 text-white dark:from-brand-600 dark:to-accent-cyan"
                  : "rounded-bl-sm bg-white ring-1 ring-gray-100 dark:bg-surface-darkcard dark:text-gray-100 dark:ring-white/10"
              }`}
            >
              {m.content}
            </div>
          </div>
        ))}
        {partnerTyping && (
          <div className="flex w-fit items-center gap-1 rounded-2xl rounded-bl-sm bg-white px-4 py-3 shadow-sm ring-1 ring-gray-100 dark:bg-surface-darkcard dark:ring-white/10">
            {[0, 1, 2].map((i) => (
              <span
                key={i}
                className="h-1.5 w-1.5 animate-typing-dot rounded-full bg-gradient-to-r from-brand-500 to-accent-cyan"
                style={{ animationDelay: `${i * 0.15}s` }}
              />
            ))}
          </div>
        )}
      </div>

      <div className="border-t border-gray-100 bg-white/90 p-3 backdrop-blur-xl dark:border-white/10 dark:bg-surface-dark/90">
        <div className="flex items-center gap-2">
          <input
            value={draft}
            onChange={(e) => handleDraftChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.nativeEvent.isComposing) {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder="Type a message…"
            className="flex-1 rounded-2xl border border-gray-200 bg-white px-4 py-2.5 text-sm text-gray-900 placeholder:text-gray-400 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-brand-500 dark:border-white/10 dark:bg-white/5 dark:text-gray-100 dark:placeholder:text-gray-500"
          />
          <Button onClick={handleSend} disabled={!draft.trim()}>
            Send
          </Button>
        </div>
      </div>
    </div>
  );
}
