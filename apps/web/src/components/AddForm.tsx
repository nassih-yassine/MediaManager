"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";

import {
  BookIcon,
  DiscIcon,
  ImageIcon,
  LinkIcon,
  MovieIcon,
  PlusIcon,
  TrashIcon,
} from "@/components/icons";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { useToast } from "@/components/ui/Toast";
import { api, formatBytes } from "@/lib/api";
import { useRealtime } from "@/lib/realtime";
import { SelectMenu } from "@/components/ui/SelectMenu";
import {
  type CollectionKind,
  type Item,
  type Job,
  type Kind,
  type PlaylistSummary,
} from "@/lib/types";

const KIND_OPTIONS: {
  value: Kind;
  label: string;
  hint: string;
  icon: typeof DiscIcon;
}[] = [
  { value: "music", label: "Audio", hint: "Sound + Cover", icon: DiscIcon },
  { value: "video", label: "Video", hint: "Full quality", icon: MovieIcon },
  { value: "picture", label: "Picture", hint: "Image file", icon: ImageIcon },
  { value: "book", label: "Book", hint: "PDF or EPUB", icon: BookIcon },
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
  if (job.status === "done") return "Saved to library";
  if (job.status === "queued") return "Waiting for a worker";
  return STAGE_LABEL[job.stage ?? "fetching"] ?? "Working";
}

function prettyUrl(raw: string) {
  try {
    const url = new URL(raw);
    const path = url.pathname === "/" ? "" : url.pathname;
    return `${url.hostname.replace(/^www\./, "")}${path}`.slice(0, 64);
  } catch {
    return raw;
  }
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
  const Icon =
    KIND_OPTIONS.find((option) => option.value === job.kind)?.icon ?? DiscIcon;

  return (
    <li className="rounded-xl border border-line bg-panel px-3.5 py-3">
      <div className="flex items-start gap-3">
        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-panel-2 text-muted">
          <Icon className="h-4 w-4" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-[13px] font-medium">
            {prettyUrl(job.url)}
          </p>
          <p
            className={`mt-0.5 text-xs ${
              job.status === "failed" ? "text-danger" : "text-muted"
            }`}
          >
            {statusLine(job)}
            {job.status === "running" ? ` · ${pct}%` : ""}
            {job.attempts > 1 ? ` · attempt ${job.attempts}/${job.maxAttempts}` : ""}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-1.5">
          {job.status === "done" && job.itemId ? (
            <Link
              href={
                job.kind === "music" || job.kind === "video"
                  ? `/watch/${job.itemId}`
                  : `/read/${job.itemId}`
              }
              className="rounded-md border border-line px-2 py-1 text-[11px] text-muted hover:text-ink"
            >
              Open
            </Link>
          ) : null}
          {active ? (
            <button
              type="button"
              onClick={() => onCancel(job.id)}
              className="rounded-md border border-line px-2 py-1 text-[11px] text-muted hover:border-danger/50 hover:text-danger"
            >
              Cancel
            </button>
          ) : null}
        </div>
      </div>
      {active ? (
        <div className="mt-2.5 h-1.5 overflow-hidden rounded-full bg-panel-2">
          <div
            className="h-full rounded-full bg-accent transition-[width] duration-500"
            style={{ width: `${Math.max(3, pct)}%` }}
          />
        </div>
      ) : null}
      {job.status === "failed" && job.error ? (
        <p className="mt-2 line-clamp-2 text-[11px] leading-relaxed text-danger/80">
          {job.error}
        </p>
      ) : null}
    </li>
  );
}

export function AddForm() {
  const toast = useToast();
  const [url, setUrl] = useState("");
  const [kind, setKind] = useState<Kind>("music");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [files, setFiles] = useState<File[]>([]);
  const [dragging, setDragging] = useState(false);
  const [collections, setCollections] = useState<PlaylistSummary[]>([]);
  const [collectionId, setCollectionId] = useState("");
  const [clearOpen, setClearOpen] = useState(false);
  const [clearing, setClearing] = useState(false);
  const fileInput = useRef<HTMLInputElement | null>(null);
  const knownStatus = useRef(new Map<string, string>());
  const firstPoll = useRef(true);

  const isYoutubeKind = kind === "music" || kind === "video";
  const acceptsFile = kind === "book" || kind === "picture";
  const collectionKind: CollectionKind = kind === "picture" ? "gallery" : "playlist";
  const availableCollections = collections.filter(
    (collection) => collection.kind === collectionKind,
  );
  const targetCollection = availableCollections.find(
    (collection) => collection.id === collectionId,
  );

  const refreshJobs = useCallback(async () => {
    try {
      const data = await api<{ jobs: Job[] }>("/api/jobs?limit=12");
      for (const job of data.jobs) {
        const previous = knownStatus.current.get(job.id);
        if (!firstPoll.current && previous && previous !== job.status) {
          if (job.status === "done") {
            toast(`Saved ${prettyUrl(job.url)}`, "success");
          } else if (job.status === "failed") {
            toast(`Failed: ${prettyUrl(job.url)}`, "error");
          }
        }
        knownStatus.current.set(job.id, job.status);
      }
      firstPoll.current = false;
      setJobs(data.jobs);
    } catch {
      // submit errors carry the useful message
    }
  }, [toast]);

  useEffect(() => {
    void refreshJobs();
    const timer = setInterval(refreshJobs, 2000);
    return () => clearInterval(timer);
  }, [refreshJobs]);

  useEffect(() => {
    api<{ playlists: PlaylistSummary[] }>("/api/playlists")
      .then((data) => setCollections(data.playlists))
      .catch(() => {});
  }, []);

  useRealtime((event) => {
    if (event.type === "item.created") void refreshJobs();
  });

  useEffect(() => {
    setCollectionId("");
  }, [kind]);

  function pickKind(next: Kind) {
    setKind(next);
    setError(null);
  }

  function addFiles(next: FileList | File[] | null) {
    const list = next ? Array.from(next) : [];
    if (list.length > 0) {
      setFiles(list);
      setError(null);
    }
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);

    if (acceptsFile && files.length > 0) {
      setSubmitting(true);
      let added = 0;
      const failures: string[] = [];
      for (const file of files) {
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
          const created = (await res.json()) as Item;
          if (collectionId) {
            await api(`/api/playlists/${collectionId}/items`, {
              method: "POST",
              body: JSON.stringify({ itemIds: [created.id] }),
            });
          }
          added += 1;
        } catch (err) {
          failures.push(`${file.name}: ${(err as Error).message}`);
        }
      }
      if (collectionId) {
        window.dispatchEvent(new Event("mm:playlists-changed"));
      }
      if (added > 0) {
        toast(
          targetCollection
            ? `Added ${added} ${added === 1 ? "file" : "files"} to “${targetCollection.name}”`
            : `Added ${added} ${added === 1 ? "file" : "files"} to the library`,
          "success",
        );
      }
      if (failures.length > 0) {
        setError(failures.join(" · "));
      }
      setFiles([]);
      if (fileInput.current) fileInput.current.value = "";
      setSubmitting(false);
      return;
    }

    if (!url.trim()) {
      setError("Paste a link first, or choose a file.");
      return;
    }

    setSubmitting(true);
    try {
      await api<Job>("/api/jobs", {
        method: "POST",
        body: JSON.stringify({
          url: url.trim(),
          kind,
          mode: kind === "music" ? "audio+thumb" : "full",
          collectionId: collectionId || undefined,
        }),
      });
      toast(
        targetCollection
          ? `Queued. It will be added to “${targetCollection.name}” when it finishes.`
          : "Queued. It will appear in the library when it finishes.",
        "success",
      );
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
      toast("Job cancelled", "info");
      await refreshJobs();
    } catch (err) {
      toast((err as Error).message, "error");
    }
  }

  async function clearJobs() {
    setClearing(true);
    try {
      await api("/api/jobs/clear", {
        method: "POST",
        body: JSON.stringify({ scope: "all" }),
      });
      toast("Download queue cleared", "success");
      await refreshJobs();
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setClearing(false);
      setClearOpen(false);
    }
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_360px]">
      <div>
        <p className="text-[10px] font-semibold tracking-[0.18em] text-muted uppercase">
          New download
        </p>
        <h1 className="mt-1 text-[2.1rem] leading-none font-semibold tracking-tight sm:text-[2.6rem]">
          Add media
        </h1>
        <p className="mt-2 max-w-lg text-sm leading-relaxed text-muted">
          Paste a link and choose what to keep. Audio saves the sound and its
          cover; video saves the whole file.
        </p>

        <form
          onSubmit={submit}
          className="mt-6 rounded-2xl border border-line bg-panel p-5 sm:p-6"
        >
          <label
            htmlFor="url"
            className="text-[11px] font-semibold tracking-[0.12em] text-muted uppercase"
          >
            Link
          </label>
          <div className="relative mt-2">
            <LinkIcon className="pointer-events-none absolute top-1/2 left-3.5 h-4 w-4 -translate-y-1/2 text-muted" />
            <input
              id="url"
              type="url"
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder={
                isYoutubeKind
                  ? "https://youtu.be/…"
                  : "https://example.com/file.pdf"
              }
              className="h-12 w-full rounded-xl border border-line bg-canvas pr-3.5 pl-10 text-sm placeholder:text-muted/60 focus:border-line-strong focus:outline-none"
            />
          </div>

          <fieldset className="mt-6">
            <legend className="text-[11px] font-semibold tracking-[0.12em] text-muted uppercase">
              Keep as
            </legend>
            <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
              {KIND_OPTIONS.map((option) => {
                const Icon = option.icon;
                const active = kind === option.value;
                return (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => pickKind(option.value)}
                    aria-pressed={active}
                    className={`flex flex-col items-start gap-2 rounded-xl border px-3 py-3 text-left transition-all ${
                      active
                        ? "border-accent bg-accent/10 text-ink"
                        : "border-line bg-canvas text-muted hover:border-line-strong hover:text-ink"
                    }`}
                  >
                    <Icon className={`h-4 w-4 ${active ? "text-accent" : ""}`} />
                    <span>
                      <span className="block text-sm font-medium">{option.label}</span>
                      <span className="mt-0.5 block text-[11px] leading-tight opacity-75">
                        {option.hint}
                      </span>
                    </span>
                  </button>
                );
              })}
            </div>
          </fieldset>

          {kind === "music" ? (
            <p className="mt-5 rounded-xl border border-line bg-canvas px-3.5 py-2.5 text-xs leading-relaxed text-muted">
              Audio is saved as an MP3 with its cover art. For the full video,
              choose <span className="text-ink">Video</span> above.
            </p>
          ) : null}

          <div className="mt-5">
            <label
              htmlFor="collection"
              className="text-[11px] font-semibold tracking-[0.12em] text-muted uppercase"
            >
              Add to {kind === "picture" ? "gallery" : "playlist"}{" "}
              <span className="font-normal tracking-normal normal-case">(optional)</span>
            </label>
            <SelectMenu
              value={collectionId}
              options={[
                { value: "", label: "Don't add" },
                ...availableCollections.map((collection) => ({
                  value: collection.id,
                  label: collection.name,
                })),
              ]}
              onChange={setCollectionId}
              ariaLabel={`Add to ${kind === "picture" ? "gallery" : "playlist"}`}
              className="mt-2 h-10 w-full rounded-lg border border-line bg-canvas px-3 text-sm text-muted"
              panelWidth={280}
            />
            {availableCollections.length === 0 ? (
              <p className="mt-1.5 text-xs text-muted">
                No {kind === "picture" ? "galleries" : "playlists"} yet. Create
                one from the library or the Playlists page.
              </p>
            ) : null}
          </div>

          {acceptsFile ? (
            <div className="mt-6">
              <p className="text-[11px] font-semibold tracking-[0.12em] text-muted uppercase">
                Or upload from this machine
              </p>
              <label
                htmlFor="file"
                onDragOver={(event) => {
                  event.preventDefault();
                  setDragging(true);
                }}
                onDragLeave={() => setDragging(false)}
                onDrop={(event) => {
                  event.preventDefault();
                  setDragging(false);
                  addFiles(event.dataTransfer.files);
                }}
                className={`mt-2 flex cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed px-4 py-7 text-center transition-colors ${
                  dragging
                    ? "border-accent bg-accent/10"
                    : "border-line bg-canvas hover:border-line-strong"
                }`}
              >
                <PlusIcon className="h-5 w-5 text-muted" />
                <span className="mt-2 text-sm">
                  {files.length > 0
                    ? `${files.length} ${files.length === 1 ? "file" : "files"} ready`
                    : "Drop files here, or browse"}
                </span>
                <span className="mt-1 max-w-full truncate text-xs text-muted">
                  {files.length > 0
                    ? `${formatBytes(files.reduce((sum, file) => sum + file.size, 0))} · ${files
                        .slice(0, 3)
                        .map((file) => file.name)
                        .join(", ")}${files.length > 3 ? "…" : ""}`
                    : kind === "book"
                      ? "PDF, EPUB — select one or many"
                      : "JPG, PNG, WebP, GIF — select one or many"}
                </span>
                <input
                  ref={fileInput}
                  id="file"
                  type="file"
                  multiple
                  className="hidden"
                  onChange={(event) => addFiles(event.target.files)}
                />
              </label>
              {files.length > 0 ? (
                <button
                  type="button"
                  onClick={() => {
                    setFiles([]);
                    if (fileInput.current) fileInput.current.value = "";
                  }}
                  className="mt-2 text-xs text-muted hover:text-ink"
                >
                  Clear files
                </button>
              ) : null}
            </div>
          ) : null}

          {error ? (
            <p className="mt-5 rounded-xl border border-danger/40 bg-danger/5 px-3.5 py-2.5 text-sm text-danger">
              {error}
            </p>
          ) : null}

          <button
            type="submit"
            disabled={submitting}
            className="mt-6 h-12 w-full rounded-xl bg-accent px-5 text-sm font-semibold text-accent-ink transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {submitting
              ? "Starting…"
              : acceptsFile && files.length > 0
                ? files.length === 1
                  ? "Upload"
                  : `Upload ${files.length} files`
                : "Start download"}
          </button>
        </form>
      </div>

      <aside className="lg:sticky lg:top-6 lg:self-start">
        <div className="flex items-baseline justify-between">
          <div>
            <p className="text-[10px] font-semibold tracking-[0.18em] text-muted uppercase">
              Activity
            </p>
            <h2 className="mt-1 text-lg font-semibold tracking-tight">Queue</h2>
          </div>
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={refreshJobs}
              className="text-xs text-muted hover:text-ink"
            >
              Refresh
            </button>
            <button
              type="button"
              onClick={() => setClearOpen(true)}
              disabled={jobs.length === 0}
              className="flex items-center gap-1 text-xs text-muted hover:text-danger disabled:opacity-40"
            >
              <TrashIcon className="h-3 w-3" />
              Clear
            </button>
          </div>
        </div>

        <ul className="mt-3 space-y-2">
          {jobs.length === 0 ? (
            <li className="rounded-xl border border-dashed border-line px-4 py-8 text-center text-sm text-muted">
              Nothing downloading right now.
            </li>
          ) : (
            jobs.map((job) => (
              <JobRow key={job.id} job={job} onCancel={cancel} />
            ))
          )}
        </ul>

        <p className="mt-4 text-[11px] leading-relaxed text-muted">
          Jobs keep running if you close this tab. Failed downloads retry three
          times unless the source is gone.
        </p>
      </aside>

      <ConfirmDialog
        open={clearOpen}
        busy={clearing}
        title="Clear download queue?"
        description="Active downloads are cancelled. Finished, failed and cancelled entries are removed. Files already saved to the library stay."
        confirmLabel="Clear queue"
        onCancel={() => setClearOpen(false)}
        onConfirm={clearJobs}
      />
    </div>
  );
}
