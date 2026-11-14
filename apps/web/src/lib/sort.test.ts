import { describe, expect, it } from "vitest";

import { sortItems } from "./sort";
import type { Item } from "./types";

function item(partial: Partial<Item> & { id: string; title: string }): Item {
  return {
    kind: "music",
    sourceUrl: null,
    mime: "audio/mpeg",
    bytes: 0,
    durationMs: null,
    width: null,
    height: null,
    positionMs: 0,
    lastPlayedAt: null,
    createdAt: 0,
    fileUrl: `/files/${partial.id}`,
    thumbUrl: null,
    ...partial,
  };
}

describe("sortItems", () => {
  const items = [
    item({ id: "a", title: "beta", createdAt: 10, bytes: 100, durationMs: 5_000 }),
    item({ id: "b", title: "Alpha", createdAt: 30, bytes: 300, durationMs: null }),
    item({ id: "c", title: "gamma", createdAt: 20, bytes: 200, durationMs: 9_000 }),
  ];

  it("defaults to newest first", () => {
    expect(sortItems(items, "newest").map((i) => i.id)).toEqual(["b", "c", "a"]);
  });

  it("sorts oldest first", () => {
    expect(sortItems(items, "oldest").map((i) => i.id)).toEqual(["a", "c", "b"]);
  });

  it("sorts titles case-insensitively", () => {
    expect(sortItems(items, "title").map((i) => i.title)).toEqual([
      "Alpha",
      "beta",
      "gamma",
    ]);
  });

  it("sorts by size and duration", () => {
    expect(sortItems(items, "largest").map((i) => i.id)).toEqual(["b", "c", "a"]);
    expect(sortItems(items, "duration").map((i) => i.id)).toEqual(["c", "a", "b"]);
  });

  it("does not mutate the input", () => {
    const copy = [...items];
    sortItems(items, "title");
    expect(items).toEqual(copy);
  });
});
