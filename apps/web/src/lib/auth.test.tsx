import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AuthProvider, useAuth } from "./auth";

const mocks = vi.hoisted(() => ({ api: vi.fn() }));

vi.mock("@/lib/api", () => ({ api: mocks.api }));

function Probe() {
  const { user, loading, needsSetup } = useAuth();
  return (
    <div>
      <span data-testid="state">
        {loading ? "loading" : needsSetup ? "setup" : (user?.name ?? "anon")}
      </span>
    </div>
  );
}

function renderProvider() {
  return render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
}

const ADMIN = { id: "u1", email: "a@example.com", name: "Ann", role: "admin" };

describe("AuthProvider", () => {
  beforeEach(() => {
    mocks.api.mockReset();
    localStorage.clear();
  });

  it("loads the current user", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/auth/status") return Promise.resolve({ needsSetup: false });
      if (path === "/api/auth/me") return Promise.resolve({ user: ADMIN });
      return Promise.reject(new Error("unexpected"));
    });
    renderProvider();
    expect(screen.getByTestId("state")).toHaveTextContent("loading");
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("Ann"));
  });

  it("reports first-run setup instead of calling /me", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/auth/status") return Promise.resolve({ needsSetup: true });
      return Promise.reject(new Error("unexpected"));
    });
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("setup"));
    expect(mocks.api).not.toHaveBeenCalledWith("/api/auth/me");
  });

  it("clears the user when the API answers 401", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/auth/status") return Promise.resolve({ needsSetup: false });
      if (path === "/api/auth/me") return Promise.resolve({ user: ADMIN });
      return Promise.reject(new Error("unexpected"));
    });
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("Ann"));
    window.dispatchEvent(new Event("mm:unauthorized"));
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("anon"));
  });

  it("loads account-level theme preferences", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/auth/status") return Promise.resolve({ needsSetup: false });
      if (path === "/api/auth/me") return Promise.resolve({ user: ADMIN });
      if (path === "/api/profile")
        return Promise.resolve({ theme: "ocean", themeMode: "light" });
      return Promise.reject(new Error("unexpected"));
    });
    function ThemeProbe() {
      const { preferences } = useAuth();
      return <span data-testid="theme">{`${preferences?.theme}/${preferences?.themeMode}`}</span>;
    }
    render(
      <AuthProvider>
        <ThemeProbe />
      </AuthProvider>,
    );
    await waitFor(() =>
      expect(screen.getByTestId("theme")).toHaveTextContent("ocean/light"),
    );
    expect(document.documentElement.dataset.theme).toBe("ocean");
    expect(document.documentElement.dataset.mode).toBe("light");
  });

  it("clears the previous account's queue when the session expires", async () => {
    localStorage.setItem("mm.player.v1", "{\"queue\":[\"someone-else\"]}");
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/auth/status") return Promise.resolve({ needsSetup: false });
      if (path === "/api/auth/me") return Promise.resolve({ user: ADMIN });
      return Promise.reject(new Error("unexpected"));
    });
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("Ann"));
    window.dispatchEvent(new Event("mm:unauthorized"));
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("anon"));
    expect(localStorage.getItem("mm.player.v1")).toBeNull();
  });

  it("drops the stored play queue on logout", async () => {
    localStorage.setItem("mm.player.v1", "{\"queue\":[]}");
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/auth/status") return Promise.resolve({ needsSetup: false });
      if (path === "/api/auth/me") return Promise.resolve({ user: ADMIN });
      if (path === "/api/auth/logout") return Promise.resolve(undefined);
      return Promise.reject(new Error("unexpected"));
    });
    function LogoutProbe() {
      const { logout, user } = useAuth();
      return (
        <button type="button" onClick={() => void logout()}>
          {user ? "out" : "gone"}
        </button>
      );
    }
    render(
      <AuthProvider>
        <LogoutProbe />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByRole("button")).toHaveTextContent("out"));
    screen.getByRole("button").click();
    await waitFor(() =>
      expect(localStorage.getItem("mm.player.v1")).toBeNull(),
    );
  });
});
