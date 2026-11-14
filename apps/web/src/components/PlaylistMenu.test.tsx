import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ToastProvider } from "@/components/ui/Toast";
import { PlaylistMenu } from "./PlaylistMenu";

const mocks = vi.hoisted(() => ({ api: vi.fn() }));

vi.mock("@/lib/api", () => ({ api: mocks.api }));

function renderMenu(itemIds: string[] = ["item-1"]) {
  return render(
    <ToastProvider>
      <PlaylistMenu itemIds={itemIds} />
    </ToastProvider>,
  );
}

const mix = {
  id: "p1",
  name: "Mix",
  itemCount: 2,
  contains: false,
};

beforeEach(() => {
  mocks.api.mockReset();
});

describe("PlaylistMenu", () => {
  it("shows playlists and adds a single item", async () => {
    mocks.api.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === "/api/items/item-1/playlists") {
        return { playlists: [mix] };
      }
      if (path === "/api/playlists" && !init) {
        return { playlists: [] };
      }
      return undefined;
    });

    const user = userEvent.setup();
    renderMenu();

    await user.click(screen.getByRole("button", { name: "Add to playlist" }));
    await user.click(await screen.findByRole("button", { name: /Mix/ }));

    await waitFor(() =>
      expect(mocks.api).toHaveBeenCalledWith("/api/playlists/p1/items", {
        method: "POST",
        body: JSON.stringify({ itemIds: ["item-1"] }),
      }),
    );
    expect(await screen.findByText("Added to “Mix”")).toBeInTheDocument();
  });

  it("removes an item that is already in the playlist", async () => {
    mocks.api.mockImplementation(async (path: string) => {
      if (path === "/api/items/item-1/playlists") {
        return { playlists: [{ ...mix, contains: true }] };
      }
      return undefined;
    });

    const user = userEvent.setup();
    renderMenu();

    await user.click(screen.getByRole("button", { name: "Add to playlist" }));
    await user.click(await screen.findByRole("button", { name: /Mix/ }));

    await waitFor(() =>
      expect(mocks.api).toHaveBeenCalledWith("/api/playlists/p1/items/item-1", {
        method: "DELETE",
      }),
    );
    expect(await screen.findByText("Removed from “Mix”")).toBeInTheDocument();
  });

  it("adds several items in one request", async () => {
    mocks.api.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === "/api/playlists" && !init) {
        return {
          playlists: [
            {
              ...mix,
              thumbUrl: null,
              thumbUrls: [],
              createdAt: 0,
              updatedAt: 0,
            },
          ],
        };
      }
      return undefined;
    });

    const user = userEvent.setup();
    renderMenu(["a", "b"]);

    await user.click(screen.getByRole("button", { name: "Add to playlist" }));
    await user.click(await screen.findByRole("button", { name: /Mix/ }));

    await waitFor(() =>
      expect(mocks.api).toHaveBeenCalledWith("/api/playlists/p1/items", {
        method: "POST",
        body: JSON.stringify({ itemIds: ["a", "b"] }),
      }),
    );
    expect(await screen.findByText("Added 2 items to “Mix”")).toBeInTheDocument();
  });

  it("creates a playlist and adds the item", async () => {
    mocks.api.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === "/api/items/item-1/playlists") {
        return { playlists: [] };
      }
      if (path === "/api/playlists" && init?.method === "POST") {
        return { id: "p2", name: "Chill" };
      }
      return undefined;
    });

    const user = userEvent.setup();
    renderMenu();

    await user.click(screen.getByRole("button", { name: "Add to playlist" }));
    await user.type(screen.getByPlaceholderText("New playlist"), "Chill");
    await user.keyboard("{Enter}");

    await waitFor(() =>
      expect(mocks.api).toHaveBeenCalledWith("/api/playlists/p2/items", {
        method: "POST",
        body: JSON.stringify({ itemIds: ["item-1"] }),
      }),
    );
    expect(
      await screen.findByText("Created “Chill” and added the item"),
    ).toBeInTheDocument();
  });
});
