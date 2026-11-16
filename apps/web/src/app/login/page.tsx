"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

import { FirstRunLanguage } from "@/components/FirstRunLanguage";
import { useAuth } from "@/lib/auth";
import { useI18n } from "@/lib/i18n/I18nProvider";

function LoginForm() {
  const { login, user, needsSetup, loading } = useAuth();
  const { t } = useI18n();
  const router = useRouter();
  const params = useSearchParams();
  const requested = params.get("next");
  // Only same-site paths; "//evil" and absolute URLs are open redirects.
  const next =
    requested && requested.startsWith("/") && !requested.startsWith("//")
      ? requested
      : "/";
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!loading && user) router.replace(next);
  }, [loading, user, next, router]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      router.replace(next);
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center px-4">
      <FirstRunLanguage />
      <div className="w-full max-w-sm rounded-2xl border border-line bg-panel p-6 shadow-2xl">
        <span className="flex h-9 w-9 items-center justify-center rounded-[11px] bg-accent text-base font-bold text-accent-ink">
          M
        </span>
        <h1 className="mt-4 text-xl font-semibold tracking-tight">{t("auth.signIn")}</h1>
        <p className="mt-1 text-sm text-muted">{t("auth.privateNote")}</p>

        {needsSetup ? (
          <p className="mt-4 rounded-xl border border-line bg-panel-2 px-3 py-2 text-sm">
            {t("auth.firstRun")}{" "}
            <Link href="/setup" className="text-accent underline">
              {t("auth.createAdminLink")}
            </Link>
            .
          </p>
        ) : null}

        <form onSubmit={submit} className="mt-5 space-y-3">
          <label className="block text-xs font-medium text-muted">
            {t("auth.email")}
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
            {t("auth.password")}
            <input
              type="password"
              required
              autoComplete="current-password"
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
            {busy ? t("auth.signingIn") : t("auth.signIn")}
          </button>
        </form>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<div className="min-h-dvh" />}>
      <LoginForm />
    </Suspense>
  );
}
