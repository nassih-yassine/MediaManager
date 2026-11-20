import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ToastProvider } from "@/components/ui/Toast";
import type { Item } from "@/lib/types";

import { ItemActions } from "./ItemActions";

const mocks = vi.hoisted(() => ({
  player: {
    current: null as Item | null,
    playing: false,
    toggle: vi.fn(),
    playItem: vi.fn(),
    playNext: vi.fn(),
    addToQueue: vi.fn(),
  },
}));

vi.mock("@/lib/player", () => ({ usePlayer: () => mocks.player }));
vi.mock("@/components/PlaylistMenu", () => ({
  PlaylistMenu: (props: { itemIds: string[]; kind: string; variant: string }) => (
    <div
      data-testid="playlist-menu"
      data-item-ids={props.itemIds.join(",")}
      data-kind={props.kind}
      data-variant={props.variant}
    />
  ),
}));

function makeItem(overrides: Partial<Item> = {}): Item {
  return {
    id: "i1",
    kind: "music",
    title: "Track",
    sourceUrl: null,
    mime: "audio/mpeg",
    bytes: 1024,
    durationMs: 60_000,
    width: null,
    height: null,
    positionMs: 0,
    lastPlayedAt: null,
    evictedAt: null,
    createdAt: 1_700_000_000_000,
    fileUrl: "/files/music/aa/i1.mp3",
    thumbUrl: null,
    ...overrides,
  };
}

function renderActions(item: Item) {
  return render(
    <ToastProvider>
      <ItemActions item={item} />
    </ToastProvider>,
  );
}

describe("ItemActions", () => {
  beforeEach(() => {
    mocks.player.current = null;
    mocks.player.playing = false;
    mocks.player.toggle.mockReset();
    mocks.player.playItem.mockReset();
    mocks.player.playNext.mockReset();
    mocks.player.addToQueue.mockReset();
  });

  it("plays an item from the start", async () => {
    const item = makeItem();
    const user = userEvent.setup();
    renderActions(item);

    await user.click(screen.getByRole("button", { name: /^Play$/ }));
    expect(mocks.player.playItem).toHaveBeenCalledWith(item);
  });

  it("labels a stored position as Resume", () => {
    renderActions(makeItem({ positionMs: 30_000 }));
    expect(screen.getByRole("button", { name: /Resume/ })).toBeInTheDocument();
  });

  it("pauses the current item instead of restarting it", async () => {
    const item = makeItem();
    mocks.player.current = item;
    mocks.player.playing = true;
    const user = userEvent.setup();
    renderActions(item);

    await user.click(screen.getByRole("button", { name: /Pause/ }));
    expect(mocks.player.toggle).toHaveBeenCalledTimes(1);
    expect(mocks.player.playItem).not.toHaveBeenCalled();
  });

  it("plays the current item when it is paused", async () => {
    const item = makeItem();
    mocks.player.current = item;
    mocks.player.playing = false;
    const user = userEvent.setup();
    renderActions(item);

    await user.click(screen.getByRole("button", { name: /^Play$/ }));
    expect(mocks.player.toggle).toHaveBeenCalledTimes(1);
  });

  it("queues an item to play next and confirms with a toast", async () => {
    const item = makeItem();
    const user = userEvent.setup();
    renderActions(item);

    await user.click(screen.getByRole("button", { name: "Play next" }));
    expect(mocks.player.playNext).toHaveBeenCalledWith(item);
    expect(await screen.findByText("Playing next")).toBeInTheDocument();
  });

  it("adds an item to the queue and confirms with a toast", async () => {
    const item = makeItem();
    const user = userEvent.setup();
    renderActions(item);

    await user.click(screen.getByRole("button", { name: /Add to queue/ }));
    expect(mocks.player.addToQueue).toHaveBeenCalledWith([item]);
    expect(await screen.findByText("Added to queue")).toBeInTheDocument();
  });

  it("hides playback buttons for kinds the media element cannot play", () => {
    renderActions(makeItem({ kind: "book", mime: "application/pdf" }));

    expect(screen.queryByRole("button", { name: /^Play$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Play next" })).not.toBeInTheDocument();
    expect(screen.getByTestId("playlist-menu")).toBeInTheDocument();
  });

  it("targets galleries for pictures and playlists for everything else", () => {
    const { unmount } = renderActions(makeItem({ kind: "picture", mime: "image/png" }));
    expect(screen.getByTestId("playlist-menu")).toHaveAttribute("data-kind", "gallery");
    unmount();

    renderActions(makeItem());
    expect(screen.getByTestId("playlist-menu")).toHaveAttribute("data-kind", "playlist");
    expect(screen.getByTestId("playlist-menu")).toHaveAttribute("data-item-ids", "i1");
    expect(screen.getByTestId("playlist-menu")).toHaveAttribute("data-variant", "pill");
  });
});
