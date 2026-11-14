export const dynamic = "force-dynamic";
export const runtime = "nodejs";

const API = process.env.MM_API_URL ?? "http://127.0.0.1:8787";

/**
 * Streaming proxy for the API's SSE endpoint. The generic /api rewrite buffers
 * until the first bytes arrive, which makes EventSource open late and drop the
 * first event; a route handler passes the stream through immediately.
 */
export async function GET() {
  const upstream = await fetch(`${API}/api/events`, {
    headers: { accept: "text/event-stream" },
    cache: "no-store",
  });

  if (!upstream.ok || !upstream.body) {
    return new Response("events unavailable", { status: 502 });
  }

  return new Response(upstream.body, {
    headers: {
      "content-type": "text/event-stream",
      "cache-control": "no-cache, no-transform",
      connection: "keep-alive",
      "x-accel-buffering": "no",
    },
  });
}
