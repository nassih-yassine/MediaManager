import type { Item } from "./types";

export type SortKey = "newest" | "oldest" | "title" | "largest" | "duration";

export const SORT_OPTIONS: { value: SortKey; label: string }[] = [
  { value: "newest", label: "Newest first" },
  { value: "oldest", label: "Oldest first" },
  { value: "title", label: "Title A–Z" },
  { value: "largest", label: "Largest first" },
  { value: "duration", label: "Longest first" },
];

const collator = new Intl.Collator(undefined, {
  sensitivity: "base",
  numeric: true,
});

/**
 * Sorts a page of items. The API also supports `sort`, but this keeps an
 * already-loaded page stable when the user flips the control.
 */
export function sortItems(items: Item[], key: SortKey): Item[] {
  const next = [...items];
  switch (key) {
    case "oldest":
      return next.sort((a, b) => a.createdAt - b.createdAt);
    case "title":
      return next.sort((a, b) => collator.compare(a.title, b.title));
    case "largest":
      return next.sort((a, b) => b.bytes - a.bytes);
    case "duration":
      return next.sort((a, b) => (b.durationMs ?? 0) - (a.durationMs ?? 0));
    case "newest":
    default:
      return next.sort((a, b) => b.createdAt - a.createdAt);
  }
}
