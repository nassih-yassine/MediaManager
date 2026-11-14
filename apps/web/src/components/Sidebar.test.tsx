import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Sidebar } from "./Sidebar";

const mocks = vi.hoisted(() => ({
  api: vi.fn(),
  logout: vi.fn(),
  replace: vi.fn(),
  user: {
    id: "u1",
    email: "a@example.com",
    name: "Ann",
    role: "admin" as "admin" | "user",
  },
}));

vi.mock("@/lib/api", () => ({ api: mocks.api }));
vi.mock("@/lib/realtime", () => ({ useRealtime: () => {} }));
vi.mock("@/lib/auth", () => ({
  useAuth: () => ({ user: mocks.user, logout: mocks.logout }),
}));
vi.mock("next/navigation", () => ({
  usePathname: () => "/",
  useRouter: () => ({ replace: mocks.replace }),
}));

describe("Sidebar account menu", () => {
  beforeEach(() => {
    mocks.api.mockReset();
    mocks.api.mockResolvedValue({ playlists: [] });
    mocks.logout.mockReset();
    mocks.user = { id: "u1", email: "a@example.com", name: "Ann", role: "admin" };
  });

  it("shows the admin link only for admins", () => {
    render(<Sidebar />);
    expect(screen.getAllByText("Admin").length).toBeGreaterThan(0);
    mocks.user = { ...mocks.user, role: "user" };
    render(<Sidebar />);
    const adminLinks = screen
      .getAllByRole("link")
      .filter((link) => link.getAttribute("href") === "/admin");
    expect(adminLinks).toHaveLength(1);
  });

  it("signs out and returns to the login page", async () => {
    mocks.logout.mockResolvedValue(undefined);
    render(<Sidebar />);
    await userEvent.click(screen.getAllByText("Sign out")[0]);
    await waitFor(() => expect(mocks.logout).toHaveBeenCalled());
    expect(mocks.replace).toHaveBeenCalledWith("/login");
  });
});
