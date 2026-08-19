import { clsx } from "clsx";

interface AvatarProps {
  name: string;
  src?: string | null;
  size?: "sm" | "md" | "lg";
  online?: boolean;
  showOnlineIndicator?: boolean;
}

const sizeClasses = { sm: "h-8 w-8 text-xs", md: "h-10 w-10 text-sm", lg: "h-14 w-14 text-lg" };
const indicatorSize = { sm: "h-2.5 w-2.5", md: "h-3 w-3", lg: "h-4 w-4" };
const ringPad = { sm: "p-[2px]", md: "p-[2.5px]", lg: "p-[3px]" };

const GRADIENTS = [
  "from-rose-500 to-orange-400",
  "from-orange-500 to-amber-400",
  "from-amber-500 to-lime-400",
  "from-emerald-500 to-teal-400",
  "from-teal-500 to-cyan-400",
  "from-sky-500 to-brand-400",
  "from-brand-500 to-violet-400",
  "from-violet-500 to-fuchsia-400",
  "from-fuchsia-500 to-pink-400",
];

function gradientForName(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return GRADIENTS[Math.abs(hash) % GRADIENTS.length];
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function Avatar({ name, src, size = "md", online, showOnlineIndicator }: AvatarProps) {
  return (
    <div className="relative shrink-0">
      {/* Gradient ring frame — reads as a small "level" badge around every avatar */}
      <div
        className={clsx(
          "rounded-full bg-gradient-to-br",
          gradientForName(name),
          ringPad[size],
          online && "animate-glow-pulse",
        )}
      >
        {src ? (
          <img
            src={src}
            alt={name}
            className={clsx("rounded-full object-cover ring-2 ring-white dark:ring-surface-dark", sizeClasses[size])}
          />
        ) : (
          <div
            className={clsx(
              "flex items-center justify-center rounded-full font-semibold text-white ring-2 ring-white dark:ring-surface-dark",
              "bg-gradient-to-br",
              gradientForName(name),
              sizeClasses[size],
            )}
          >
            {initials(name)}
          </div>
        )}
      </div>
      {showOnlineIndicator && (
        <span className="absolute -bottom-0.5 -right-0.5">
          {online && (
            <span
              className={clsx(
                "absolute inset-0 rounded-full bg-accent-lime animate-ring-expand",
                indicatorSize[size],
              )}
            />
          )}
          <span
            className={clsx(
              "relative block rounded-full border-2 border-white dark:border-surface-dark",
              indicatorSize[size],
              online ? "bg-accent-lime" : "bg-gray-300 dark:bg-gray-600",
            )}
            aria-label={online ? "Online" : "Offline"}
          />
        </span>
      )}
    </div>
  );
}
