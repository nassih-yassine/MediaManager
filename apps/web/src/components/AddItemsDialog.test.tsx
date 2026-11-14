import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ToastProvider } from "@/components/ui/Toast";
import type { Item } from "@/lib/types";
import { AddItemsDialog } from "./AddItemsDialog";

const mocks = vi.hoisted(() => ({ api: vi.fn() }));

vi.mock("@/lib/api", () => ({ api: mocks.api }));

function item(id: string, title: string): Item {
  return {
    id,
    kind: "picture",
    title,
    sourceUrl: null,
    mime: "image/jpeg",
    bytes: 100,
    durationMs: null,
    width: null,
    height: null,
    positionMs: 0,
    lastPlayedAt: null,
    createdAt: 0,
    fileUrl: `/files/${id}`,
    thumbUrl: null,
  };
}

function renderDialog(overrides: Partial<React.ComponentProps<typeof AddItemsDialog>> = {}) {
  const onClose = vi.fn();
  const onAdded = vi.fn();
  render(
    <ToastProvider>
      <AddItemsDialog
        open
        collectionId="c1"
        kind="playlist"
        existingIds={[]}
        onClose={onClose}
        onAdded={onAdded}
        {...overrides}
      />
    </ToastProvider>,
  );
  return { onClose, onAdded };
}

beforeEach(() => {
  mocks.api.mockReset();
  mocks.api.mockImplementation(async () => ({
    items: [item("a", "Alpha"), item("b", "Beta"), item("c", "Gamma")],
    total: 3,
    nextCursor: null,
  }));
});

describe("AddItemsDialog", () => {
  it("hides items already in the collection and adds the selection", async () => {
    const onAdded = vi.fn();
    const user = userEvent.setup();
    renderDialog({ existingIds: ["b"], onAdded });

    await screen.findByText("Alpha");
    expect(screen.queryByText("Beta")).not.toBeInTheDocument();

    await user.click(screen.getByText("Alpha"));
    await user.click(screen.getByText("Gamma"));
    await user.click(screen.getByRole("button", { name: "Add 2" }));

    await waitFor(() =>
      expect(mocks.api).toHaveBeenCalledWith("/api/playlists/c1/items", {
        method: "POST",
        body: JSON.stringify({ itemIds: ["a", "c"] }),
      }),
    );
    expect(onAdded).toHaveBeenCalled();
    expect(await screen.findByText("Added 2 items")).toBeInTheDocument();
  });

  it("filters the library to pictures for galleries", async () => {
    const user = userEvent.setup();
    renderDialog({ kind: "gallery" });

    await screen.findByText("Alpha");
    expect(mocks.api).toHaveBeenCalledWith(
      expect.stringContaining("kind=picture"),
    );
    expect(
      screen.getByText("Add pictures to the gallery"),
    ).toBeInTheDocument();
    await user.keyboard("{Escape}");
  });
});
