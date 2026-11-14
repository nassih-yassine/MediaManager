"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { api, formatBytes } from "@/lib/api";
import { type Job, type Kind } from "@/lib/types";

const KIND_OPTIONS: { value: Kind; label: string; hint: string }[] = [
  { value: "music", label: "Music", hint: "Audio with cover art" },
  { value: "video", label: "Video", hint: "Full video file" },
  { value: "picture", label: "Picture", hint: "Image from a URL or upload" },
  { value: "book", label: "Book", hint: "PDF / EPUB from a URL or upload" },
];

const STAGE_LABEL: Record<string, string> = {
  fetching: "Downloading",
  converting: "Converting",
  thumbnail: "Making thumbnail",
  finalizing: "Finishing",
  done: "Done",
};

function statusLine(job: Job) {
  if (job.status === "failed") return job.error ?? "Failed";
  if (job.status === "cancelled") return "Cancelled";
  if (job.status === "done") return "Saved";
  if (job.status === "queued") return "Waiting in queue";
  return STAGE_LABEL[job.stage ?? "fetching"] ?? "Working";
}

function JobRow({
  job,
  onCancel,
}: {
  job: Job;
  onCancel: (id: string) => void;
}) {
  const active = job.status === "queued" || job.status === "running";
  const pct = Math.round(Math.min(1, Math.max(0, job.progress)) * 100);

  return (
    <div className="rounded-xl border border-line bg-panel px-4 py-3.5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{job.url}</p>
          <p
            className={`mt-0.5 truncate text-xs ${
              job.status === "failed" ? "text-danger" : "text-muted"
            }`}
            title={job.error ?? undefined}
          >
            {job.kind} · {statusLine(job)}
            {job.status === "running" ? ` · ${pct}%` : ""}
            {job.attempts > 1 ? ` · attempt ${job.attempts}/${job.maxAttempts}` : ""}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {job.status === "done" && job.itemId ? (
            <Link
              href={
                job.kind === "music" || job.kind === "video"
                  ? `/watch/${job.itemId}`
                  : `/read/${job.itemId}`
              }
              className="rounded-md border border-line px-2.5 py-1 text-xs text-muted transition-colors hover:text-ink"
            >
              Open
            </Link>
          ) : null}
          {active ? (
            <button
              type="button"
              onClick={() => onCancel(job.id)}
              className="rounded-md border border-line px-2.5 py-1 text-xs text-muted transition-colors hover:border-danger/50 hover:text-danger"
            >
              Cancel
            </button>
          ) : null}
        </div>
      </div>
      {active ? (
        <div className="mt-3 h-1 overflow-hidden rounded-full bg-panel-2">
          <div
            className="h-full rounded-full bg-accent transition-[width] duration-500"
            style={{ width: `${Math.max(2, pct)}%` }}
          />
        </div>
      ) : null}
    </div>
  );
}

export function AddForm() {
  const [url, setUrl] = useState("");
  const [kind, setKind] = useState<Kind>("music");
  const [mode, setMode] = useState<"audio+thumb" | "full">("audio+thumb");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [file, setFile] = useState<File | null>(null);

  const isYoutubeKind = kind === "music" || kind === "video";

  const refreshJobs = useCallback(async () => {
    try {
      const data = await api<{ jobs: Job[] }>("/api/jobs?limit=10");
      setJobs(data.jobs);
    } catch {
      // backend down; the form's submit error will surface it
    }
  }, []);

  useEffect(() => {
    refreshJobs();
    const timer = setInterval(refreshJobs, 2000);
    return () => clearInterval(timer);
  }, [refreshJobs]);

  function pickKind(next: Kind) {
    setKind(next);
    setMode(next === "music" ? "audio+thumb" : "full");
    setError(null);
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);

    if (kind === "book" || kind === "picture") {
      if (!file && !url.trim()) {
        setError("Choose a file or paste a direct link.");
        return;
      }
      if (file) {
        setSubmitting(true);
        try {
          const body = new FormData();
          body.append("file", file);
          const res = await fetch(`/api/uploads?kind=${kind}`, {
            method: "POST",
            body,
          });
          if (!res.ok) {
            const data = (await res.json().catch(() => null)) as {
              error?: string;
            } | null;
            throw new Error(data?.error ?? "Upload failed");
          }
          setFile(null);
          setUrl("");
          await refreshJobs();
        } catch (err) {
          setError((err as Error).message);
        } finally {
          setSubmitting(false);
        }
        return;
      }
    }

    if (!url.trim()) {
      setError("Paste a link first.");
      return;
    }

    setSubmitting(true);
    try {
      await api<Job>("/api/jobs", {
        method: "POST",
        body: JSON.stringify({
          url: url.trim(),
          kind,
          mode: isYoutubeKind ? mode : "full",
        }),
      });
      setUrl("");
      await refreshJobs();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSubmitting(false);
    }
  }

  async function cancel(id: string) {
    try {
      await api(`/api/jobs/${id}`, { method: "DELETE" });
      await refreshJobs();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div className="max-w-3xl">
      <h1 className="text-2xl font-semibold tracking-tight">Add media</h1>
      <p className="mt-1 text-sm text-muted">
        Paste a link, choose what to keep. Music downloads keep their cover.
      </p>

      <form
        onSubmit={submit}
        className="mt-6 rounded-2xl border border-line bg-panel p-5 sm:p-6"
      >
        <label
          htmlFor="url"
          className="text-xs font-medium tracking-wide text-muted uppercase"
        >
          {isYoutubeKind ? "Video or audio link" : "Direct file link"}
        </label>
        <input
          id="url"
          type="url"
          value={url}
          onChange={(event) => setUrl(event.target.value)}
          placeholder={
            isYoutubeKind
              ? "https://www.youtube.com/watch?v=…"
              : "https://example.com/file.pdf"
          }
          className="mt-2 h-11 w-full rounded-lg border border-line bg-canvas px-3.5 text-sm placeholder:text-muted/60 focus:border-line-strong focus:outline-none"
        />

        <div className="mt-5">
          <span className="text-xs font-medium tracking-wide text-muted uppercase">
            Keep as
          </span>
          <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
            {KIND_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => pickKind(option.value)}
                className={`rounded-lg border px-3 py-2.5 text-left transition-colors ${
                  kind === option.value
                    ? "border-accent/60 bg-accent/10 text-ink"
                    : "border-line bg-canvas text-muted hover:text-ink"
                }`}
              >
                <span className="block text-sm font-medium">{option.label}</span>
                <span className="mt-0.5 block text-[11px] leading-tight opacity-80">
                  {option.hint}
                </span>
              </button>
            ))}
          </div>
        </div>

        {kind === "music" ? (
          <div className="mt-5">
            <span className="text-xs font-medium tracking-wide text-muted uppercase">
              Download
            </span>
            <div className="mt-2 inline-flex rounded-lg border border-line bg-canvas p-1">
              {(
                [
                  ["audio+thumb", "Audio + cover"],
                  ["full", "Full video"],
                ] as const
              ).map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setMode(value)}
                  className={`rounded-md px-3 py-1.5 text-sm transition-colors ${
                    mode === value
                      ? "bg-panel-2 text-ink"
                      : "text-muted hover:text-ink"
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        ) : null}

        {kind === "book" || kind === "picture" ? (
          <div className="mt-5">
            <label
              htmlFor="file"
              className="text-xs font-medium tracking-wide text-muted uppercase"
            >
              Or upload from this machine
            </label>
            <input
              id="file"
              type="file"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              className="mt-2 block w-full cursor-pointer rounded-lg border border-line bg-canvas px-3 py-2.5 text-sm text-muted file:mr-3 file:rounded-md file:border-0 file:bg-panel-2 file:px-3 file:py-1.5 file:text-sm file:text-ink"
            />
            {file ? (
              <p className="mt-2 text-xs text-muted">
                {file.name} · {formatBytes(file.size)}
              </p>
            ) : null}
          </div>
        ) : null}

        {error ? (
          <p className="mt-4 rounded-lg border border-danger/40 bg-danger/5 px-3 py-2 text-sm text-danger">
            {error}
          </p>
        ) : null}

        <button
          type="submit"
          disabled={submitting}
          className="mt-5 h-11 w-full rounded-lg bg-accent px-5 text-sm font-semibold text-accent-ink transition-opacity hover:opacity-90 disabled:opacity-50 sm:w-auto"
        >
          {submitting ? "Starting…" : "Start download"}
        </button>
      </form>

      <section className="mt-8">
        <div className="flex items-baseline justify-between">
          <h2 className="text-sm font-semibold tracking-wide text-muted uppercase">
            Recent jobs
          </h2>
          <button
            type="button"
            onClick={refreshJobs}
            className="text-xs text-muted transition-colors hover:text-ink"
          >
            Refresh
          </button>
        </div>

        <div className="mt-3 space-y-2">
          {jobs.length === 0 ? (
            <p className="rounded-xl border border-dashed border-line px-4 py-6 text-center text-sm text-muted">
              No jobs yet.
            </p>
          ) : (
            jobs.map((job) => (
              <JobRow key={job.id} job={job} onCancel={cancel} />
            ))
          )}
        </div>
      </section>
    </div>
  );
}
