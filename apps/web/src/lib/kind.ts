import type { Kind } from "./types";

/**
 * User-facing names. The stored kind stays `music` for compatibility with
 * existing rows, files and jobs; only the label changes.
 */
export const KIND_LABEL: Record<Kind, string> = {
  music: "Audio",
  video: "Video",
  picture: "Picture",
  book: "Book",
};

export function kindLabel(kind: Kind | string): string {
  return KIND_LABEL[kind as Kind] ?? kind;
}
