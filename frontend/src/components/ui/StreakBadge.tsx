import { useEffect, useState } from "react";
import { Flame } from "lucide-react";

const STORAGE_KEY = "chatapp-streak";

interface StreakData {
  count: number;
  lastActiveDay: string;
}

function todayKey() {
  return new Date().toISOString().slice(0, 10);
}

function computeStreak(): number {
  if (typeof window === "undefined") return 1;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    const today = todayKey();
    const yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10);

    if (!raw) {
      const fresh: StreakData = { count: 1, lastActiveDay: today };
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(fresh));
      return 1;
    }

    const data: StreakData = JSON.parse(raw);
    if (data.lastActiveDay === today) return data.count;

    const next: StreakData = data.lastActiveDay === yesterday
      ? { count: data.count + 1, lastActiveDay: today }
      : { count: 1, lastActiveDay: today };

    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    return next.count;
  } catch {
    return 1;
  }
}

/** Purely cosmetic, local-only "chat streak" — a small daily-visit counter to add a game-like touch. */
export function StreakBadge() {
  const [streak, setStreak] = useState<number | null>(null);

  useEffect(() => {
    setStreak(computeStreak());
  }, []);

  if (!streak) return null;

  return (
    <div
      className="flex items-center gap-1 rounded-full bg-gradient-to-r from-accent-amber/20 to-orange-400/20 px-2.5 py-1 ring-1 ring-accent-amber/30"
      title={`${streak} day streak`}
    >
      <Flame className="h-3.5 w-3.5 animate-streak-flicker text-accent-amber" />
      <span className="text-xs font-semibold text-orange-600 dark:text-accent-amber">{streak}</span>
    </div>
  );
}
