import { type ButtonHTMLAttributes, forwardRef } from "react";
import { clsx } from "clsx";
import { Loader2 } from "lucide-react";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md" | "lg";
  loading?: boolean;
}

const variantClasses: Record<NonNullable<ButtonProps["variant"]>, string> = {
  primary:
    "bg-gradient-to-r from-brand-600 via-brand-500 to-accent-pink bg-300% text-white shadow-lg shadow-brand-500/30 " +
    "hover:shadow-brand-500/50 hover:brightness-110 active:brightness-95 disabled:opacity-40 disabled:shadow-none " +
    "hover:bg-right transition-[background-position,box-shadow,filter] duration-500",
  secondary:
    "bg-gray-100 text-gray-900 hover:bg-gray-200 active:bg-gray-300 " +
    "dark:bg-white/10 dark:text-gray-100 dark:hover:bg-white/20 dark:active:bg-white/25",
  ghost:
    "bg-transparent text-gray-700 hover:bg-gray-100 active:bg-gray-200 " +
    "dark:text-gray-300 dark:hover:bg-white/10 dark:active:bg-white/15",
  danger:
    "bg-gradient-to-r from-red-600 to-rose-500 text-white hover:brightness-110 active:brightness-95 disabled:opacity-40",
};

const sizeClasses: Record<NonNullable<ButtonProps["size"]>, string> = {
  sm: "text-sm px-3 py-1.5 rounded-lg",
  md: "text-sm px-4 py-2.5 rounded-xl",
  lg: "text-base px-5 py-3 rounded-xl",
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = "primary", size = "md", loading, disabled, className, children, ...props }, ref) => (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={clsx(
        "inline-flex items-center justify-center gap-2 font-medium transition-colors",
        "focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-surface-dark",
        "disabled:cursor-not-allowed",
        variantClasses[variant],
        sizeClasses[size],
        className,
      )}
      {...props}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" />}
      {children}
    </button>
  ),
);
Button.displayName = "Button";
