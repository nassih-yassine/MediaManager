import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { GET } from "./route";

const fetchMock = vi.fn();

function request(cookie?: string) {
  return new Request("http://localhost/api/events", {
    headers: cookie ? { cookie } : {},
  });
}

describe("GET /api/events", () => {
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("forwards the cookie and streams the upstream body", async () => {
    const body = new ReadableStream();
    fetchMock.mockResolvedValue(
      new Response(body, { status: 200, headers: { "content-type": "text/event-stream" } }),
    );

    const response = await GET(request("mm_session=abc"));

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/events$/);
    expect(init.headers).toMatchObject({
      accept: "text/event-stream",
      cookie: "mm_session=abc",
    });
    expect(init.cache).toBe("no-store");
    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("text/event-stream");
    expect(response.headers.get("cache-control")).toBe("no-cache, no-transform");
    expect(response.headers.get("x-accel-buffering")).toBe("no");
  });

  it("omits the cookie header for anonymous requests", async () => {
    fetchMock.mockResolvedValue(new Response(new ReadableStream(), { status: 200 }));

    await GET(request());

    expect(fetchMock.mock.calls[0][1].headers).not.toHaveProperty("cookie");
  });

  it("passes through a 401 instead of opening a stream", async () => {
    fetchMock.mockResolvedValue(new Response("nope", { status: 401 }));

    const response = await GET(request());

    expect(response.status).toBe(401);
    expect(await response.text()).toBe("unauthorized");
  });

  it("answers 502 when the backend fails or sends no stream", async () => {
    fetchMock.mockResolvedValue(new Response("boom", { status: 500 }));
    expect((await GET(request())).status).toBe(502);

    fetchMock.mockResolvedValue({ ok: true, status: 200, body: null });
    const empty = await GET(request());
    expect(empty.status).toBe(502);
    expect(await empty.text()).toBe("events unavailable");
  });
});
