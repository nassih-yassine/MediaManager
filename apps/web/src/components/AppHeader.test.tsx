import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AppHeader } from "./AppHeader";

const mocks = vi.hoisted(() => ({
  logout: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("@/components/NotificationBell", () => ({
  NotificationBell: () => <span data-testid="bell" />,
}));
vi.mock("@/lib/auth", () => ({
  useAuth: () => ({
    user: { id: "u1", email: "a@example.com", name: "Ann Admin", role: "admin" },
    logout: mocks.logout,
  }),
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
}));

describe("AppHeader", () => {
  beforeEach(() => {
    mocks.logout.mockReset().mockResolvedValue(undefined);
    mocks.replace.mockReset();
  });

  it("puts the bell and account menu in the top right", async () => {
    render(<AppHeader />);
    expect(screen.getByTestId("bell")).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "Account menu" }));
    expect(screen.getByRole("link", { name: /Profile/ })).toHaveAttribute(
      "href",
      "/profile",
    );
    expect(screen.getByRole("link", { name: "Admin" })).toHaveAttribute(
      "href",
      "/admin",
    );
  });

  it("signs out from the account menu", async () => {
    render(<AppHeader />);
    await userEvent.click(screen.getByRole("button", { name: "Account menu" }));
    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));
    await waitFor(() => expect(mocks.logout).toHaveBeenCalled());
    expect(mocks.replace).toHaveBeenCalledWith("/login");
  });
});
