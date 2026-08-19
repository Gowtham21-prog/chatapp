export function TypingIndicator() {
  return (
    <div className="flex w-fit animate-pop-in items-center gap-1 rounded-2xl rounded-bl-sm bg-white px-4 py-3 shadow-sm ring-1 ring-gray-100 dark:bg-surface-darkcard dark:ring-white/10">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="h-1.5 w-1.5 animate-typing-dot rounded-full bg-gradient-to-r from-brand-500 to-accent-pink"
          style={{ animationDelay: `${i * 0.15}s` }}
        />
      ))}
    </div>
  );
}
