import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ToastProvider } from "@/components/ui/Toast";
import { DeleteButton } from "./DeleteButton";

const mocks = vi.hoisted(() => ({
  api: vi.fn(),
  push: vi.fn(),
  refresh: vi.fn(),
  removeItem: vi.fn(),
}));

vi.mock("@/lib/api", () => ({ api: mocks.api }));
vi.mock("@/lib/player", () => ({ usePlayer: () => ({ removeItem: mocks.removeItem }) }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mocks.push, refresh: mocks.refresh }),
}));

function renderButton(id = "item-1") {
  return render(
    <ToastProvider>
      <DeleteButton id={id} />
    </ToastProvider>,
  );
}

describe("DeleteButton", () => {
  beforeEach(() => {
    mocks.api.mockReset();
    mocks.push.mockReset();
    mocks.removeItem.mockReset();
  });

  it("opens a confirmation before deleting", async () => {
    mocks.api.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderButton();

    await user.click(screen.getByRole("button", { name: "Delete" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(mocks.api).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Delete permanently" }));

    await waitFor(() =>
      expect(mocks.api).toHaveBeenCalledWith(
        "/api/items/item-1?delete_file=true",
        { method: "DELETE" },
      ),
    );
    expect(mocks.removeItem).toHaveBeenCalledWith("item-1");
    expect(mocks.push).toHaveBeenCalledWith("/");
  });

  it("does nothing when cancelled", async () => {
    const user = userEvent.setup();
    renderButton();

    await user.click(screen.getByRole("button", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(mocks.api).not.toHaveBeenCalled();
    expect(mocks.push).not.toHaveBeenCalled();
  });

  it("surfaces a failed delete instead of failing silently", async () => {
    mocks.api.mockRejectedValue(new Error("file is locked"));
    const user = userEvent.setup();
    renderButton();

    await user.click(screen.getByRole("button", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: "Delete permanently" }));

    expect(await screen.findByText("file is locked")).toBeInTheDocument();
    expect(mocks.push).not.toHaveBeenCalled();
    expect(mocks.removeItem).not.toHaveBeenCalled();
  });
});
