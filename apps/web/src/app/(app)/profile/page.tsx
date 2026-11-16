"use client";

import { useEffect, useState } from "react";

import { ThemePicker } from "@/components/ThemePicker";
import { SelectMenu } from "@/components/ui/SelectMenu";
import { useToast } from "@/components/ui/Toast";
import { api, formatDate } from "@/lib/api";
import { useAuth } from "@/lib/auth";

interface Report {
  id: string;
  kind: "bug" | "feature";
  title: string;
  body: string;
  status: "open" | "resolved";
  createdAt: number;
}

export default function ProfilePage() {
  const { user, refresh } = useAuth();
  const toast = useToast();
  const [name, setName] = useState(user?.name ?? "");
  const [savingName, setSavingName] = useState(false);
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [savingPassword, setSavingPassword] = useState(false);
  const [reports, setReports] = useState<Report[]>([]);
  const [kind, setKind] = useState<"bug" | "feature">("feature");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [sending, setSending] = useState(false);

  useEffect(() => {
    setName(user?.name ?? "");
  }, [user?.name]);

  const isAdmin = user?.role === "admin";

  useEffect(() => {
    if (isAdmin) return;
    api<{ reports: Report[] }>("/api/reports")
      .then((data) => setReports(data.reports))
      .catch(() => {});
  }, [isAdmin]);

  async function saveName(event: React.FormEvent) {
    event.preventDefault();
    setSavingName(true);
    try {
      await api("/api/profile", {
        method: "PATCH",
        body: JSON.stringify({ name }),
      });
      await refresh();
      toast("Name updated", "success");
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setSavingName(false);
    }
  }

  async function savePassword(event: React.FormEvent) {
    event.preventDefault();
    if (next.length < 8) {
      toast("New password must be at least 8 characters", "error");
      return;
    }
    setSavingPassword(true);
    try {
      await api("/api/auth/password", {
        method: "POST",
        body: JSON.stringify({ currentPassword: current, newPassword: next }),
      });
      setCurrent("");
      setNext("");
      toast("Password changed", "success");
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setSavingPassword(false);
    }
  }

  async function sendReport(event: React.FormEvent) {
    event.preventDefault();
    setSending(true);
    try {
      const data = await api<{ report: Report }>("/api/reports", {
        method: "POST",
        body: JSON.stringify({ kind, title, body }),
      });
      setReports((current) => [data.report, ...current]);
      setTitle("");
      setBody("");
      toast("Sent to the admin", "success");
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <p className="text-[10px] font-semibold tracking-[0.18em] text-muted uppercase">
        Account
      </p>
      <h1 className="mt-1 text-2xl font-semibold tracking-tight">Profile</h1>
      <p className="mt-1 text-sm text-muted">
        {user?.email} · {user?.role}
      </p>

      <div className="mt-6 grid gap-4 sm:grid-cols-2">
        <form
          onSubmit={saveName}
          className="rounded-2xl border border-line bg-panel p-4"
        >
          <h2 className="text-sm font-semibold">Display name</h2>
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            aria-label="Display name"
            className="mt-3 w-full rounded-lg border border-line bg-canvas px-3 py-2 text-sm"
          />
          <button
            type="submit"
            disabled={savingName || !name.trim()}
            className="mt-3 rounded-lg bg-accent px-3 py-1.5 text-sm font-medium text-accent-ink disabled:opacity-50"
          >
            {savingName ? "Saving…" : "Save name"}
          </button>
        </form>

        <form
          onSubmit={savePassword}
          className="rounded-2xl border border-line bg-panel p-4"
        >
          <h2 className="text-sm font-semibold">Password</h2>
          <input
            type="password"
            autoComplete="current-password"
            placeholder="Current password"
            value={current}
            onChange={(event) => setCurrent(event.target.value)}
            className="mt-3 w-full rounded-lg border border-line bg-canvas px-3 py-2 text-sm"
          />
          <input
            type="password"
            autoComplete="new-password"
            placeholder="New password (8+)"
            value={next}
            onChange={(event) => setNext(event.target.value)}
            className="mt-2 w-full rounded-lg border border-line bg-canvas px-3 py-2 text-sm"
          />
          <button
            type="submit"
            disabled={savingPassword || !current || !next}
            className="mt-3 rounded-lg border border-line px-3 py-1.5 text-sm text-muted hover:text-ink disabled:opacity-50"
          >
            {savingPassword ? "Changing…" : "Change password"}
          </button>
          <p className="mt-2 text-[11px] text-muted">
            Other sessions are signed out.
          </p>
        </form>
      </div>

      <section className="mt-4 rounded-2xl border border-line bg-panel p-4">
        <h2 className="text-sm font-semibold">Appearance</h2>
        <p className="mt-1 text-xs text-muted">
          Saved to your account, so it follows you to another browser.
        </p>
        <div className="mt-2">
          <ThemePicker />
        </div>
      </section>

      {!isAdmin ? (
      <section className="mt-4 rounded-2xl border border-line bg-panel p-4">
        <h2 className="text-sm font-semibold">Report a bug or request a feature</h2>
        <form onSubmit={sendReport} className="mt-3 space-y-2">
          <SelectMenu
            value={kind}
            options={[
              { value: "feature", label: "Feature request" },
              { value: "bug", label: "Bug report" },
            ]}
            onChange={(value) => setKind(value as "bug" | "feature")}
            ariaLabel="Report kind"
            className="h-9 rounded-lg border border-line bg-canvas px-3 text-sm"
            panelWidth={180}
          />
          <input
            placeholder="Short title"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            required
            minLength={3}
            maxLength={120}
            className="w-full rounded-lg border border-line bg-canvas px-3 py-2 text-sm"
          />
          <textarea
            placeholder="What happened, or what would help?"
            value={body}
            onChange={(event) => setBody(event.target.value)}
            required
            minLength={10}
            maxLength={4000}
            rows={4}
            className="w-full rounded-lg border border-line bg-canvas px-3 py-2 text-sm"
          />
          <button
            type="submit"
            disabled={sending}
            className="rounded-lg bg-accent px-3.5 py-2 text-sm font-medium text-accent-ink disabled:opacity-50"
          >
            {sending ? "Sending…" : "Send to admin"}
          </button>
        </form>
      </section>
      ) : null}

      {!isAdmin ? (
      <section className="mt-4 rounded-2xl border border-line bg-panel p-4">
        <h2 className="text-sm font-semibold">My reports</h2>
        {reports.length === 0 ? (
          <p className="mt-2 text-xs text-muted">Nothing submitted yet.</p>
        ) : (
          <ul className="mt-3 space-y-2">
            {reports.map((report) => (
              <li
                key={report.id}
                className="rounded-xl border border-line bg-canvas px-3 py-2"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-sm font-medium">{report.title}</span>
                  <span className="flex items-center gap-2">
                    <span className="rounded-full border border-line px-2 py-0.5 text-[10px] uppercase tracking-[0.08em] text-muted">
                      {report.kind}
                    </span>
                    <span
                      className={`rounded-full px-2 py-0.5 text-[10px] uppercase tracking-[0.08em] ${
                        report.status === "resolved"
                          ? "bg-accent/15 text-accent"
                          : "bg-panel-2 text-muted"
                      }`}
                    >
                      {report.status}
                    </span>
                  </span>
                </div>
                <p className="mt-1 text-xs leading-relaxed text-muted">{report.body}</p>
                <p className="mt-1 font-mono text-[10px] text-muted">
                  {formatDate(report.createdAt)}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>
      ) : null}
    </div>
  );
}
