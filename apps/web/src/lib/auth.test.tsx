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

  it("loads the theme after signing in", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/auth/status") return Promise.resolve({ needsSetup: false });
      if (path === "/api/auth/me") return Promise.resolve({ user: null }).catch(() => null);
      if (path === "/api/auth/login") return Promise.resolve({ user: ADMIN });
      if (path === "/api/profile")
        return Promise.resolve({ theme: "ocean", themeMode: "dark" });
      return Promise.resolve(null);
    });
    function LoginProbe() {
      const { login, preferences } = useAuth();
      return (
        <>
          <button type="button" onClick={() => void login("a@example.com", "x")}>
            go
          </button>
          <span data-testid="theme">{preferences?.theme ?? "none"}</span>
        </>
      );
    }
    render(
      <AuthProvider>
        <LoginProbe />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("theme")).toHaveTextContent("none"));
    screen.getByRole("button", { name: "go" }).click();
    await waitFor(() => expect(screen.getByTestId("theme")).toHaveTextContent("ocean"));
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

describe("AuthProvider account actions", () => {
  beforeEach(() => {
    mocks.api.mockReset();
    localStorage.clear();
  });

  it("creates the first admin and loads its preferences", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/auth/status") return Promise.resolve({ needsSetup: true });
      if (path === "/api/auth/setup") return Promise.resolve({ user: ADMIN });
      if (path === "/api/profile")
        return Promise.resolve({ theme: "moss", themeMode: "light" });
      return Promise.reject(new Error("unexpected"));
    });

    function SetupProbe() {
      const { setup, preferences, needsSetup } = useAuth();
      return (
        <>
          <button type="button" onClick={() => void setup("a@example.com", "Ann", "secret123")}>
            create
          </button>
          <span data-testid="state">
            {needsSetup ? "setup" : `${preferences?.theme ?? "none"}`}
          </span>
        </>
      );
    }

    render(
      <AuthProvider>
        <SetupProbe />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("setup"));

    screen.getByRole("button", { name: "create" }).click();

    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("moss"));
    expect(mocks.api).toHaveBeenCalledWith("/api/auth/setup", {
      method: "POST",
      body: JSON.stringify({
        email: "a@example.com",
        name: "Ann",
        password: "secret123",
      }),
    });
  });

  it("applies a preference locally and PATCHes the profile", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/auth/status") return Promise.resolve({ needsSetup: false });
      if (path === "/api/auth/me") return Promise.resolve({ user: ADMIN });
      if (path === "/api/profile")
        return Promise.resolve({ theme: "ocean", themeMode: "dark" });
      return Promise.resolve(undefined);
    });

    function PrefProbe() {
      const { savePreferences, preferences } = useAuth();
      return (
        <>
          <button
            type="button"
            onClick={() => void savePreferences({ theme: "moss", themeMode: "light" })}
          >
            save
          </button>
          <span data-testid="state">{`${preferences?.theme}/${preferences?.themeMode}`}</span>
        </>
      );
    }

    render(
      <AuthProvider>
        <PrefProbe />
      </AuthProvider>,
    );
    await waitFor(() =>
      expect(screen.getByTestId("state")).toHaveTextContent("ocean/dark"),
    );

    screen.getByRole("button", { name: "save" }).click();

    await waitFor(() =>
      expect(mocks.api).toHaveBeenCalledWith("/api/profile", {
        method: "PATCH",
        body: JSON.stringify({ theme: "moss", themeMode: "light" }),
      }),
    );
    expect(screen.getByTestId("state")).toHaveTextContent("moss/light");
    expect(document.documentElement.dataset.theme).toBe("moss");
  });

  it("keeps a local preference when the save fails", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/auth/status") return Promise.resolve({ needsSetup: false });
      if (path === "/api/auth/me") return Promise.resolve({ user: ADMIN });
      if (path === "/api/profile") return Promise.reject(new Error("offline"));
      return Promise.reject(new Error("unexpected"));
    });

    function PrefProbe() {
      const { savePreferences, preferences } = useAuth();
      return (
        <>
          <button type="button" onClick={() => void savePreferences({ theme: "moss" })}>
            save
          </button>
          <span data-testid="state">{preferences?.theme ?? "none"}</span>
        </>
      );
    }

    render(
      <AuthProvider>
        <PrefProbe />
      </AuthProvider>,
    );
    expect(screen.getByTestId("state")).toHaveTextContent("none");

    screen.getByRole("button", { name: "save" }).click();

    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("moss"));
    expect(document.documentElement.dataset.theme).toBe("moss");
  });

  it("treats a status failure as signed out", async () => {
    mocks.api.mockRejectedValue(new Error("network down"));
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("anon"));
  });

  it("clears the session even when logout fails", async () => {
    mocks.api.mockImplementation((path: string) => {
      if (path === "/api/auth/status") return Promise.resolve({ needsSetup: false });
      if (path === "/api/auth/me") return Promise.resolve({ user: ADMIN });
      if (path === "/api/auth/logout") return Promise.reject(new Error("offline"));
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

    await waitFor(() => expect(screen.getByRole("button")).toHaveTextContent("gone"));
  });
});
