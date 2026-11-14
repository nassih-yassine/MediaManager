export type Kind = "music" | "video" | "picture" | "book";

export type JobStatus = "queued" | "running" | "done" | "failed" | "cancelled";

export interface Item {
  id: string;
  kind: Kind;
  title: string;
  sourceUrl: string | null;
  mime: string;
  bytes: number;
  durationMs: number | null;
  width: number | null;
  height: number | null;
  positionMs: number;
  lastPlayedAt: number | null;
  createdAt: number;
  fileUrl: string;
  thumbUrl: string | null;
}

export interface Job {
  id: string;
  url: string;
  kind: Kind;
  mode: "audio+thumb" | "full";
  status: JobStatus;
  stage: string | null;
  progress: number;
  error: string | null;
  itemId: string | null;
  attempts: number;
  maxAttempts: number;
  createdAt: number;
  updatedAt: number;
}

export interface ItemsPage {
  items: Item[];
  total: number;
  nextCursor: string | null;
}

export interface PlaylistSummary {
  id: string;
  name: string;
  itemCount: number;
  thumbUrl: string | null;
  thumbUrls: string[];
  createdAt: number;
  updatedAt: number;
}

export interface PlaylistDetail {
  id: string;
  name: string;
  createdAt: number;
  updatedAt: number;
  items: Item[];
}

export const KINDS: { value: Kind | "all"; label: string }[] = [
  { value: "all", label: "All" },
  { value: "music", label: "Audio" },
  { value: "video", label: "Video" },
  { value: "picture", label: "Pictures" },
  { value: "book", label: "Books" },
];
