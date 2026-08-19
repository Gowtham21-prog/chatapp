import type { ReactNode } from "react";
import { AlertCircle, Inbox, Loader2 } from "lucide-react";
import { Button } from "./Button";

export function Spinner({ className = "h-6 w-6" }: { className?: string }) {
  return <Loader2 className={`animate-spin text-brand-500 dark:text-brand-400 ${className}`} />;
}

export function FullPageLoader() {
  return (
    <div className="flex h-full w-full items-center justify-center">
      <Spinner className="h-8 w-8" />
    </div>
  );
}

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 px-6 py-12 text-center">
      <div className="rounded-2xl bg-gradient-to-br from-brand-100 to-accent-cyan/20 p-4 text-brand-500 dark:from-white/10 dark:to-accent-cyan/10 dark:text-brand-300">
        {icon ?? <Inbox className="h-10 w-10" />}
      </div>
      <p className="font-display font-semibold text-gray-800 dark:text-gray-100">{title}</p>
      {description && <p className="max-w-xs text-sm text-gray-500 dark:text-gray-400">{description}</p>}
      {action}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 px-6 py-12 text-center">
      <div className="rounded-2xl bg-red-50 p-4 dark:bg-red-500/10">
        <AlertCircle className="h-10 w-10 text-red-400" />
      </div>
      <p className="max-w-sm text-sm text-gray-600 dark:text-gray-300">{message}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}
