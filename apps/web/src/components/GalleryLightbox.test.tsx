import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { GalleryLightbox } from "./GalleryLightbox";
import type { Item } from "@/lib/types";

function item(id: string, title: string): Item {
  return {
    id,
    kind: "picture",
    title,
    sourceUrl: null,
    mime: "image/png",
    bytes: 10,
    durationMs: null,
    width: 96,
    height: 96,
    positionMs: 0,
    lastPlayedAt: null,
    evictedAt: null,
    createdAt: 1,
    fileUrl: `/files/picture/${id}.png`,
    thumbUrl: `/thumbs/picture/${id}.jpg`,
  };
}

const ITEMS = [item("a", "Alpha"), item("b", "Beta"), item("c", "Gamma")];

function setup(index = 0) {
  const onIndexChange = vi.fn();
  const onClose = vi.fn();
  render(
    <GalleryLightbox
      items={ITEMS}
      index={index}
      onIndexChange={onIndexChange}
      onClose={onClose}
    />,
  );
  return { onIndexChange, onClose };
}

describe("GalleryLightbox", () => {
  it("shows the current position and title", () => {
    setup(1);
    expect(screen.getByRole("dialog", { name: "Gallery viewer" })).toBeVisible();
    expect(screen.getByText("Beta")).toBeVisible();
    expect(screen.getByText("2 / 3")).toBeVisible();
  });

  it("advances and wraps with the arrow keys", async () => {
    const { onIndexChange } = setup(2);
    await userEvent.keyboard("{ArrowRight}");
    expect(onIndexChange).toHaveBeenCalledWith(0);
    await userEvent.keyboard("{ArrowLeft}");
    expect(onIndexChange).toHaveBeenCalledWith(1);
  });

  it("moves through the next/previous buttons", async () => {
    const { onIndexChange } = setup(0);
    await userEvent.click(screen.getByRole("button", { name: "Next picture" }));
    expect(onIndexChange).toHaveBeenCalledWith(1);
    await userEvent.click(screen.getByRole("button", { name: "Previous picture" }));
    expect(onIndexChange).toHaveBeenCalledWith(2);
  });

  it("jumps to a thumbnail and closes on Escape", async () => {
    const { onIndexChange, onClose } = setup(0);
    await userEvent.click(screen.getByRole("button", { name: "Show Gamma" }));
    expect(onIndexChange).toHaveBeenCalledWith(2);
    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalled();
  });

  it("hides navigation when there is only one picture", () => {
    render(
      <GalleryLightbox
        items={[ITEMS[0]]}
        index={0}
        onIndexChange={vi.fn()}
        onClose={vi.fn()}
      />,
    );
    expect(screen.queryByRole("button", { name: "Next picture" })).toBeNull();
    expect(screen.getByText("1 / 1")).toBeVisible();
  });
});
