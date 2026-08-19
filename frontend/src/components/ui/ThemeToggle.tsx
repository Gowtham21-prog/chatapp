import { Moon, Sun } from "lucide-react";
import { clsx } from "clsx";
import { useThemeStore } from "@/store/themeStore";

export function ThemeToggle({ className }: { className?: string }) {
  const theme = useThemeStore((s) => s.theme);
  const toggle = useThemeStore((s) => s.toggle);
  const isDark = theme === "dark";

  return (
    <button
      type="button"
      onClick={toggle}
      role="switch"
      aria-checked={isDark}
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
      className={clsx(
        "relative inline-flex h-8 w-14 shrink-0 items-center rounded-full p-1 transition-colors duration-300",
        "focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-surface-dark",
        isDark ? "bg-gradient-to-r from-brand-700 to-brand-500" : "bg-gradient-to-r from-amber-300 to-amber-400",
        className,
      )}
    >
      <span
        className={clsx(
          "flex h-6 w-6 items-center justify-center rounded-full bg-white shadow-md transition-transform duration-300",
          isDark ? "translate-x-6" : "translate-x-0",
        )}
      >
        {isDark ? <Moon className="h-3.5 w-3.5 text-brand-600" /> : <Sun className="h-3.5 w-3.5 text-amber-500" />}
      </span>
    </button>
  );
}
