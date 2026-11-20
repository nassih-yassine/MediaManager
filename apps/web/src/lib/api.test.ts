import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  ApiError,
  api,
  formatBytes,
  formatDate,
  formatDuration,
} from "./api";

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  } as unknown as Response;
}

function brokenJsonResponse(status: number) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.reject(new SyntaxError("Unexpected token <")),
  } as unknown as Response;
}

const fetchMock = vi.fn();

describe("api", () => {
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("parses a JSON body", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ user: { id: "u1" } }));

    await expect(api<{ user: { id: string } }>("/api/auth/me")).resolves.toEqual({
      user: { id: "u1" },
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("sends JSON content type, same-origin credentials and no cache", async () => {
    fetchMock.mockResolvedValue(jsonResponse({}));

    await api("/api/items", { method: "POST", body: JSON.stringify({ a: 1 }) });

    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe("/api/items");
    expect(init.headers).toMatchObject({ "content-type": "application/json" });
    expect(init.credentials).toBe("same-origin");
    expect(init.cache).toBe("no-store");
    expect(init.method).toBe("POST");
    expect(init.signal).toBeInstanceOf(AbortSignal);
  });

  it("keeps caller headers and signal", async () => {
    fetchMock.mockResolvedValue(jsonResponse({}));
    const controller = new AbortController();

    await api("/api/items", {
      headers: { "x-test": "1" },
      signal: controller.signal,
    });

    const init = fetchMock.mock.calls[0][1];
    expect(init.headers).toMatchObject({
      "content-type": "application/json",
      "x-test": "1",
    });
    expect(init.signal).toBe(controller.signal);
  });

  it("returns undefined for 204 without parsing a body", async () => {
    const json = vi.fn();
    fetchMock.mockResolvedValue({ ok: true, status: 204, json } as unknown as Response);

    await expect(api("/api/items/1", { method: "DELETE" })).resolves.toBeUndefined();
    expect(json).not.toHaveBeenCalled();
  });

  it("throws ApiError carrying status and code", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ error: "not yours", code: "not_found" }, 404),
    );

    const error = await api("/api/items/1").catch((err: unknown) => err);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      message: "not yours",
      status: 404,
      code: "not_found",
    });
    expect((error as Error).name).toBe("ApiError");
  });

  it("falls back to a status message when the error body is not JSON", async () => {
    fetchMock.mockResolvedValue(brokenJsonResponse(502));

    await expect(api("/api/items")).rejects.toMatchObject({
      message: "Request failed (502)",
      status: 502,
      code: "unknown",
    });
  });

  it("emits mm:unauthorized on 401 outside the auth routes", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ error: "sign in" }, 401));
    const listener = vi.fn();
    window.addEventListener("mm:unauthorized", listener);

    await expect(api("/api/items")).rejects.toThrow("sign in");
    expect(listener).toHaveBeenCalledTimes(1);

    listener.mockClear();
    await expect(api("/api/auth/login", { method: "POST" })).rejects.toThrow("sign in");
    expect(listener).not.toHaveBeenCalled();

    window.removeEventListener("mm:unauthorized", listener);
  });
});

describe("ApiError", () => {
  it("keeps its message, status and code", () => {
    const error = new ApiError("nope", 403, "forbidden");
    expect(error.message).toBe("nope");
    expect(error.status).toBe(403);
    expect(error.code).toBe("forbidden");
  });
});

describe("formatBytes", () => {
  it("uses binary units", () => {
    expect(formatBytes(0)).toBe("0 B");
    expect(formatBytes(900)).toBe("900 B");
    expect(formatBytes(1024)).toBe("1.0 KB");
    expect(formatBytes(10 * 1024 * 1024)).toBe("10 MB");
    expect(formatBytes(1.5 * 1024 ** 3)).toBe("1.5 GB");
  });

  it("handles junk input", () => {
    expect(formatBytes(-5)).toBe("0 B");
    expect(formatBytes(Number.NaN)).toBe("0 B");
    expect(formatBytes(Number.POSITIVE_INFINITY)).toBe("0 B");
  });
});

describe("formatDuration", () => {
  it("formats minutes and hours", () => {
    expect(formatDuration(null)).toBeNull();
    expect(formatDuration(0)).toBeNull();
    expect(formatDuration(65_000)).toBe("1:05");
    expect(formatDuration(3_723_000)).toBe("1:02:03");
  });

  it("returns null for negative durations", () => {
    expect(formatDuration(-1)).toBeNull();
  });
});

describe("formatDate", () => {
  it("formats a timestamp with a short month", () => {
    const formatted = formatDate(new Date(2024, 0, 5).getTime());
    expect(formatted).toContain("2024");
    expect(formatted).toContain("Jan");
  });
});
