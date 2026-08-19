import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { MessageCircle } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import { authApi } from "@/api/authApi";
import { useAuthStore } from "@/store/authStore";
import { extractErrorMessage } from "@/lib/apiClient";

export default function RegisterPage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);

  const [form, setForm] = useState({ username: "", email: "", password: "", displayName: "" });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function update<K extends keyof typeof form>(key: K, value: string) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const auth = await authApi.register(form);
      setSession(auth);
      navigate("/chat");
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="aurora-bg flex min-h-screen items-center justify-center bg-surface-light px-4 py-12 dark:bg-surface-dark">
      <div className="absolute right-4 top-4">
        <ThemeToggle />
      </div>

      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center gap-3">
          <div className="rounded-2xl bg-gradient-to-br from-brand-600 via-brand-500 to-accent-pink bg-300% p-3.5 text-white shadow-lg shadow-brand-500/40 animate-gradient-shift">
            <MessageCircle className="h-6 w-6" />
          </div>
          <h1 className="font-display text-2xl font-bold text-gray-900 dark:text-gray-50">
            Join <span className="gradient-text">Pulse</span>
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">Start chatting in a minute</p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="glass-strong space-y-4 rounded-2xl p-6 shadow-xl shadow-brand-900/5 dark:shadow-black/40"
        >
          <Input
            label="Display name"
            name="displayName"
            value={form.displayName}
            onChange={(e) => update("displayName", e.target.value)}
            required
          />
          <Input
            label="Username"
            name="username"
            autoComplete="username"
            value={form.username}
            onChange={(e) => update("username", e.target.value)}
            pattern="^[a-zA-Z0-9_]+$"
            title="Letters, numbers, and underscores only"
            required
          />
          <Input
            label="Email"
            type="email"
            name="email"
            autoComplete="email"
            value={form.email}
            onChange={(e) => update("email", e.target.value)}
            required
          />
          <Input
            label="Password"
            type="password"
            name="password"
            autoComplete="new-password"
            value={form.password}
            onChange={(e) => update("password", e.target.value)}
            minLength={8}
            required
          />
          <p className="text-xs text-gray-500 dark:text-gray-500">
            At least 8 characters, with an uppercase letter, a lowercase letter, and a number.
          </p>
          {error && (
            <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300">
              {error}
            </p>
          )}
          <Button type="submit" className="w-full" loading={loading}>
            Create account
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-gray-600 dark:text-gray-400">
          Already have an account?{" "}
          <Link to="/login" className="font-medium text-brand-600 hover:text-brand-700 dark:text-brand-300 dark:hover:text-brand-200">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
