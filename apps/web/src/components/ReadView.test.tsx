import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ToastProvider } from "@/components/ui/Toast";
import type { Item } from "@/lib/types";

import { ReadView } from "./ReadView";

const mocks = vi.hoisted(() => ({
  api: vi.fn(),
  id: "item-1",
}));

vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return { ...actual, api: mocks.api };
});
vi.mock("next/navigation", () => ({
  useParams: () => ({ id: mocks.id }),
}));
vi.mock("@/components/DeleteButton", () => ({
  DeleteButton: ({ id }: { id: string }) => <button type="button">Delete {id}</button>,
}));
vi.mock("@/components/ItemActions", () => ({
  ItemActions: ({ item }: { item: Item }) => <div data-testid="item-actions">{item.title}</div>,
}));

function makeItem(overrides: Partial<Item> = {}): Item {
  return {
    id: "item-1",
    kind: "picture",
    title: "Sunset",
    sourceUrl: null,
    mime: "image/jpeg",
    bytes: 2048,
    durationMs: null,
    width: 800,
    height: 600,
    positionMs: 0,
    lastPlayedAt: null,
    evictedAt: null,
    createdAt: new Date(2024, 4, 2).getTime(),
    fileUrl: "/files/picture/ab/item-1.jpg",
    thumbUrl: "/thumbs/ab/item-1.jpg",
    ...overrides,
  };
}

function renderView() {
  return render(
    <ToastProvider>
      <ReadView />
    </ToastProvider>,
  );
}

describe("ReadView", () => {
  beforeEach(() => {
    mocks.api.mockReset();
    mocks.id = "item-1";
  });

  it("renders metadata for a picture", async () => {
    mocks.api.mockResolvedValue(makeItem());
    renderView();

    expect(await screen.findByRole("heading", { name: "Sunset" })).toBeInTheDocument();
    expect(screen.getByText("2.0 KB")).toBeInTheDocument();
    expect(screen.getByText("jpeg")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Sunset" })).toHaveAttribute(
      "src",
      "/files/picture/ab/item-1.jpg",
    );
    expect(screen.getByTestId("item-actions")).toHaveTextContent("Sunset");
    expect(screen.getByRole("button", { name: "Delete item-1" })).toBeInTheDocument();
  });

  it("shows the source link only when a source URL exists", async () => {
    mocks.api.mockResolvedValueOnce(makeItem({ sourceUrl: "https://example.com/a.jpg" }));
    const { unmount } = renderView();
    expect(await screen.findByRole("link", { name: "Source" })).toHaveAttribute(
      "href",
      "https://example.com/a.jpg",
    );
    unmount();

    mocks.api.mockResolvedValue(makeItem());
    renderView();
    await screen.findByRole("heading", { name: "Sunset" });
    expect(screen.queryByRole("link", { name: "Source" })).not.toBeInTheDocument();
  });

  it("auto-opens a book once and reports the outcome", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/items/item-1/open") return Promise.resolve({});
      return Promise.resolve(
        makeItem({ kind: "book", mime: "application/epub+zip", title: "Dune" }),
      );
    });
    const { rerender } = renderView();

    expect(await screen.findByText("Open in app")).toBeInTheDocument();
    await waitFor(() =>
      expect(mocks.api).toHaveBeenCalledWith("/api/items/item-1/open", { method: "POST" }),
    );
    expect(await screen.findByText("Opened in your default app")).toBeInTheDocument();

    rerender(
      <ToastProvider>
        <ReadView />
      </ToastProvider>,
    );
    expect(mocks.api.mock.calls.filter(([path]) => path === "/api/items/item-1/open")).toHaveLength(1);
  });

  it("surfaces a failed native open without crashing", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/items/item-1/open") return Promise.reject(new Error("no xdg-open"));
      return Promise.resolve(
        makeItem({ kind: "book", mime: "application/pdf", title: "Dune" }),
      );
    });
    renderView();

    expect(await screen.findByText("no xdg-open")).toBeInTheDocument();
  });

  it("renders the download fallback for unpublishable kinds", async () => {
    mocks.api.mockResolvedValue(
      makeItem({ kind: "music", mime: "audio/x-unknown", title: "Odd" }),
    );
    renderView();

    expect(
      await screen.findByText("This format cannot be previewed in the browser."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Download file" })).toHaveAttribute(
      "href",
      "/files/picture/ab/item-1.jpg",
    );
  });

  it("shows the API error with a way back", async () => {
    mocks.api.mockRejectedValue(new Error("not found"));
    renderView();

    expect(await screen.findByText("not found")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Back to library/ })).toHaveAttribute("href", "/");
  });

  it("shows the skeleton while loading and refetches when the id changes", async () => {
    mocks.api.mockResolvedValue(makeItem());
    const { rerender, container } = renderView();

    expect(container.querySelector(".animate-pulse")).toBeInTheDocument();
    await screen.findByRole("heading", { name: "Sunset" });

    mocks.id = "item-2";
    mocks.api.mockResolvedValue(makeItem({ id: "item-2", title: "Second" }));
    rerender(
      <ToastProvider>
        <ReadView />
      </ToastProvider>,
    );

    await waitFor(() => expect(mocks.api).toHaveBeenCalledWith("/api/items/item-2"));
    expect(await screen.findByRole("heading", { name: "Second" })).toBeInTheDocument();
  });

  it("lets the user open a book manually", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/items/item-1/open") return Promise.resolve({});
      return Promise.resolve(
        makeItem({ kind: "book", mime: "application/pdf", title: "Dune" }),
      );
    });
    const user = userEvent.setup();
    renderView();

    const button = await screen.findByRole("button", { name: /Open in app/ });
    await waitFor(() =>
      expect(mocks.api.mock.calls.filter(([path]) => path === "/api/items/item-1/open")).toHaveLength(1),
    );
    await user.click(button);
    expect(
      mocks.api.mock.calls.filter(([path]) => path === "/api/items/item-1/open"),
    ).toHaveLength(2);
  });
});
