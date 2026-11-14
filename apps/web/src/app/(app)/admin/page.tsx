"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { PlusIcon, TrashIcon } from "@/components/icons";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { SelectMenu } from "@/components/ui/SelectMenu";
import { useToast } from "@/components/ui/Toast";
import { api, formatBytes, formatDate } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { AdminUserRow } from "@/lib/types";

interface CreateForm {
  email: string;
  name: string;
  password: string;
  role: "user" | "admin";
}

const EMPTY: CreateForm = { email: "", name: "", password: "", role: "user" };

export default function AdminPage() {
  const { user } = useAuth();
  const router = useRouter();
  const toast = useToast();
  const [users, setUsers] = useState<AdminUserRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState<CreateForm>(EMPTY);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [tab, setTab] = useState<"users" | "reports" | "announce">(() => {
    if (typeof window === "undefined") return "users";
    const value = new URLSearchParams(window.location.search).get("tab");
    return value === "reports" || value === "announce" ? value : "users";
  });
  const [reports, setReports] = useState<
    Array<{
      id: string;
      kind: string;
      title: string;
      body: string;
      status: string;
      createdAt: number;
      userName?: string;
      userEmail?: string;
    }>
  >([]);
  const [announceTitle, setAnnounceTitle] = useState("");
  const [announceBody, setAnnounceBody] = useState("");
  const [sending, setSending] = useState(false);
  const [resetFor, setResetFor] = useState<AdminUserRow | null>(null);
  const [resetPassword, setResetPassword] = useState("");
  const [deleteFor, setDeleteFor] = useState<AdminUserRow | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await api<{ users: AdminUserRow[] }>("/api/admin/users");
      setUsers(data.users);
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    if (user && user.role !== "admin") router.replace("/");
  }, [user, router]);

  useEffect(() => {
    if (user?.role === "admin") void load();
  }, [user, load]);

  const loadReports = useCallback(async () => {
    try {
      const data = await api<{ reports: typeof reports }>("/api/admin/reports");
      setReports(data.reports);
    } catch (err) {
      toast((err as Error).message, "error");
    }
  }, [toast]);

  useEffect(() => {
    if (user?.role === "admin" && tab === "reports") void loadReports();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, tab]);

  async function resolveReport(id: string) {
    try {
      await api(`/api/admin/reports/${id}`, {
        method: "PATCH",
        body: JSON.stringify({ status: "resolved" }),
      });
      await loadReports();
      toast("Report resolved", "success");
    } catch (err) {
      toast((err as Error).message, "error");
    }
  }

  async function sendAnnouncement(event: React.FormEvent) {
    event.preventDefault();
    setSending(true);
    try {
      const data = await api<{ created: number }>("/api/admin/announcements", {
        method: "POST",
        body: JSON.stringify({ title: announceTitle, body: announceBody }),
      });
      toast(`Sent to ${data.created} users`, "success");
      setAnnounceTitle("");
      setAnnounceBody("");
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setSending(false);
    }
  }

  async function createUser(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      await api("/api/admin/users", {
        method: "POST",
        body: JSON.stringify(form),
      });
      toast(`Created ${form.email}`, "success");
      setForm(EMPTY);
      setCreating(false);
      await load();
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  }

  async function patchUser(id: string, body: Record<string, unknown>) {
    setBusy(true);
    try {
      await api(`/api/admin/users/${id}`, {
        method: "PATCH",
        body: JSON.stringify(body),
      });
      await load();
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  }

  async function submitReset(event: React.FormEvent) {
    event.preventDefault();
    if (!resetFor) return;
    setBusy(true);
    try {
      await api(`/api/admin/users/${resetFor.id}/password`, {
        method: "POST",
        body: JSON.stringify({ password: resetPassword }),
      });
      toast(`Password reset for ${resetFor.email}`, "success");
      setResetFor(null);
      setResetPassword("");
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  }

  async function removeUser() {
    if (!deleteFor) return;
    setBusy(true);
    try {
      await api(`/api/admin/users/${deleteFor.id}?deleteContent=true`, {
        method: "DELETE",
      });
      toast(`Deleted ${deleteFor.email} and their content`, "success");
      setDeleteFor(null);
      await load();
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <p className="text-sm text-muted">Loading users…</p>;
  }

  return (
    <div className="mx-auto max-w-5xl">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-[10px] font-semibold tracking-[0.18em] text-muted uppercase">
            Admin
          </p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">Users</h1>
          <p className="mt-1 text-sm text-muted">
            Each account has its own private library. Deleting a user removes
            their files too.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setCreating((value) => !value)}
          className="flex items-center gap-1.5 rounded-full bg-accent px-3.5 py-2 text-sm font-medium text-accent-ink"
        >
          <PlusIcon className="h-3.5 w-3.5" />
          {creating ? "Close" : "Add user"}
        </button>
      </div>

      <div className="mt-5 flex gap-1 rounded-full border border-line bg-panel p-1 text-sm w-fit">
        {(
          [
            ["users", "Users"],
            ["reports", "Reports"],
            ["announce", "Announce"],
          ] as const
        ).map(([value, label]) => (
          <button
            key={value}
            type="button"
            onClick={() => setTab(value)}
            className={`rounded-full px-3 py-1.5 transition-colors ${
              tab === value
                ? "bg-panel-2 text-ink"
                : "text-muted hover:text-ink"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === "users" ? (
        <>
      {creating ? (
        <form
          onSubmit={createUser}
          className="mt-5 grid gap-3 rounded-2xl border border-line bg-panel p-4 sm:grid-cols-2"
        >
          <input
            type="email"
            required
            placeholder="email@example.com"
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
            className="rounded-lg border border-line bg-canvas px-3 py-2 text-sm"
          />
          <input
            required
            placeholder="Display name"
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
            className="rounded-lg border border-line bg-canvas px-3 py-2 text-sm"
          />
          <input
            type="password"
            required
            minLength={8}
            placeholder="Temporary password (8+)"
            value={form.password}
            onChange={(event) => setForm({ ...form, password: event.target.value })}
            className="rounded-lg border border-line bg-canvas px-3 py-2 text-sm"
          />
          <div className="flex items-center gap-2">
            <SelectMenu
              value={form.role}
              options={[
                { value: "user", label: "User" },
                { value: "admin", label: "Admin" },
              ]}
              onChange={(value) =>
                setForm({ ...form, role: value as "user" | "admin" })
              }
              ariaLabel="Role"
              className="h-9 flex-1 rounded-lg border border-line bg-canvas px-3 text-sm"
              panelWidth={140}
            />
            <button
              type="submit"
              disabled={busy}
              className="rounded-lg bg-accent px-3.5 py-2 text-sm font-medium text-accent-ink disabled:opacity-50"
            >
              Create
            </button>
          </div>
        </form>
      ) : null}

      <div className="mt-6 overflow-x-auto rounded-2xl border border-line bg-panel">
        <table className="w-full min-w-[760px] text-left text-sm">
          <thead className="border-b border-line text-[11px] uppercase tracking-[0.1em] text-muted">
            <tr>
              <th className="px-4 py-3 font-medium">Account</th>
              <th className="px-4 py-3 font-medium">Role</th>
              <th className="px-4 py-3 font-medium">Library</th>
              <th className="px-4 py-3 font-medium">Created</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {users.map((row) => {
              const self = row.id === user?.id;
              return (
                <tr key={row.id} className="border-b border-line/60 last:border-0">
                  <td className="px-4 py-3">
                    <span className="block font-medium">
                      {row.name}
                      {self ? " (you)" : ""}
                    </span>
                    <span className="block text-xs text-muted">{row.email}</span>
                    {row.disabled ? (
                      <span className="mt-1 inline-block rounded-full border border-danger/40 px-2 py-0.5 text-[10px] text-danger">
                        Disabled
                      </span>
                    ) : null}
                  </td>
                  <td className="px-4 py-3">
                    {self ? (
                      <span className="text-xs text-muted capitalize">{row.role}</span>
                    ) : (
                      <SelectMenu
                        value={row.role}
                        options={[
                          { value: "user", label: "User" },
                          { value: "admin", label: "Admin" },
                        ]}
                        onChange={(value) =>
                          void patchUser(row.id, { role: value })
                        }
                        ariaLabel={`Role for ${row.name}`}
                        className="h-8 rounded-full border border-line px-2.5 text-xs"
                        panelWidth={140}
                      />
                    )}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-muted">
                    {row.items} items · {formatBytes(row.bytes)}
                    {row.activeJobs > 0 ? ` · ${row.activeJobs} downloading` : ""}
                  </td>
                  <td className="px-4 py-3 text-xs text-muted">
                    {formatDate(row.createdAt)}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1.5">
                      <button
                        type="button"
                        onClick={() => {
                          setResetFor(row);
                          setResetPassword("");
                        }}
                        className="rounded-full border border-line px-2.5 py-1 text-xs text-muted hover:text-ink"
                      >
                        Reset password
                      </button>
                      <button
                        type="button"
                        disabled={self || busy}
                        onClick={() =>
                          void patchUser(row.id, { disabled: !row.disabled })
                        }
                        className="rounded-full border border-line px-2.5 py-1 text-xs text-muted hover:text-ink disabled:opacity-40"
                      >
                        {row.disabled ? "Enable" : "Disable"}
                      </button>
                      <button
                        type="button"
                        disabled={self}
                        onClick={() => setDeleteFor(row)}
                        aria-label={`Delete ${row.name}`}
                        className="rounded-full border border-line p-1.5 text-muted hover:border-danger/50 hover:text-danger disabled:opacity-40"
                      >
                        <TrashIcon className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
        </>
      ) : tab === "reports" ? (
        <div className="mt-6 space-y-2">
          {reports.length === 0 ? (
            <p className="rounded-2xl border border-dashed border-line px-4 py-10 text-center text-sm text-muted">
              No reports yet.
            </p>
          ) : (
            reports.map((report) => (
              <div
                key={report.id}
                className="rounded-2xl border border-line bg-panel px-4 py-3"
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
                <p className="mt-1 text-sm leading-relaxed text-muted">
                  {report.body}
                </p>
                <div className="mt-2 flex items-center justify-between gap-2">
                  <span className="text-[11px] text-muted">
                    {report.userName} · {report.userEmail}
                  </span>
                  {report.status === "open" ? (
                    <button
                      type="button"
                      onClick={() => void resolveReport(report.id)}
                      className="rounded-full border border-line px-3 py-1 text-xs text-muted hover:text-ink"
                    >
                      Resolve
                    </button>
                  ) : null}
                </div>
              </div>
            ))
          )}
        </div>
      ) : (
        <form
          onSubmit={sendAnnouncement}
          className="mt-6 space-y-3 rounded-2xl border border-line bg-panel p-4"
        >
          <p className="text-sm text-muted">
            Every enabled user gets this in their notification bell.
          </p>
          <input
            placeholder="Title"
            required
            minLength={3}
            maxLength={120}
            value={announceTitle}
            onChange={(event) => setAnnounceTitle(event.target.value)}
            className="w-full rounded-lg border border-line bg-canvas px-3 py-2 text-sm"
          />
          <textarea
            placeholder="What's new?"
            required
            minLength={3}
            maxLength={2000}
            rows={4}
            value={announceBody}
            onChange={(event) => setAnnounceBody(event.target.value)}
            className="w-full rounded-lg border border-line bg-canvas px-3 py-2 text-sm"
          />
          <button
            type="submit"
            disabled={sending}
            className="rounded-lg bg-accent px-3.5 py-2 text-sm font-medium text-accent-ink disabled:opacity-50"
          >
            {sending ? "Sending…" : "Send announcement"}
          </button>
        </form>
      )}

      {resetFor ? (
        <div className="fixed inset-0 z-[80] flex items-center justify-center bg-black/50 p-4">
          <form
            onSubmit={submitReset}
            role="dialog"
            aria-modal="true"
            aria-label="Reset password"
            className="w-full max-w-sm rounded-2xl border border-line bg-panel p-5 shadow-2xl"
          >
            <h2 className="text-base font-semibold">Reset password</h2>
            <p className="mt-1 text-sm text-muted">
              New password for {resetFor.email}. They stay signed in on other
              devices.
            </p>
            <input
              type="password"
              required
              minLength={8}
              value={resetPassword}
              onChange={(event) => setResetPassword(event.target.value)}
              className="mt-4 w-full rounded-lg border border-line bg-canvas px-3 py-2 text-sm"
            />
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setResetFor(null)}
                className="rounded-lg border border-line px-3 py-1.5 text-sm text-muted"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={busy}
                className="rounded-lg bg-accent px-3 py-1.5 text-sm font-medium text-accent-ink disabled:opacity-50"
              >
                Save
              </button>
            </div>
          </form>
        </div>
      ) : null}

      <ConfirmDialog
        open={deleteFor !== null}
        busy={busy}
        title={`Delete ${deleteFor?.name ?? "user"}?`}
        description="Their downloads are cancelled, and their items, files, playlists and galleries are deleted. This cannot be undone."
        confirmLabel="Delete user and content"
        onCancel={() => setDeleteFor(null)}
        onConfirm={() => void removeUser()}
      />
    </div>
  );
}
