"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { useAuth } from "@/lib/auth";

export default function SetupPage() {
  const { setup, user, needsSetup, loading } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (loading) return;
    if (user) router.replace("/");
    else if (!needsSetup) router.replace("/login");
  }, [loading, user, needsSetup, router]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await setup(email, name, password);
      router.replace("/");
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center px-4">
      <div className="w-full max-w-sm rounded-2xl border border-line bg-panel p-6 shadow-2xl">
        <span className="flex h-9 w-9 items-center justify-center rounded-[11px] bg-accent text-base font-bold text-accent-ink">
          M
        </span>
        <h1 className="mt-4 text-xl font-semibold tracking-tight">
          Create the admin account
        </h1>
        <p className="mt-1 text-sm text-muted">
          Existing media and playlists are moved into this account.
        </p>

        <form onSubmit={submit} className="mt-5 space-y-3">
          <label className="block text-xs font-medium text-muted">
            Email
            <input
              type="email"
              required
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className="mt-1 w-full rounded-lg border border-line bg-canvas px-3 py-2 text-sm text-ink outline-none focus:border-accent"
            />
          </label>
          <label className="block text-xs font-medium text-muted">
            Display name
            <input
              required
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="mt-1 w-full rounded-lg border border-line bg-canvas px-3 py-2 text-sm text-ink outline-none focus:border-accent"
            />
          </label>
          <label className="block text-xs font-medium text-muted">
            Password (8+ characters)
            <input
              type="password"
              required
              minLength={8}
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="mt-1 w-full rounded-lg border border-line bg-canvas px-3 py-2 text-sm text-ink outline-none focus:border-accent"
            />
          </label>
          {error ? <p className="text-sm text-danger">{error}</p> : null}
          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-50"
          >
            {busy ? "Creating…" : "Create admin"}
          </button>
        </form>

        <Link href="/login" className="mt-4 block text-center text-xs text-muted hover:text-ink">
          Back to sign in
        </Link>
      </div>
    </div>
  );
}
