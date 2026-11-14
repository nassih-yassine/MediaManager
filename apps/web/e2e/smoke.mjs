#!/usr/bin/env node
/**
 * E2E smoke test. Requires the app and API to be running:
 *   make dev          # in another terminal
 *   npm run e2e       # or: make e2e
 *
 * It launches the system Chromium, drives it over the DevTools protocol, adds
 * a throwaway picture, plays a track, edits the queue, deletes the picture and
 * switches themes. Fails on any uncaught exception or 4xx/5xx app resource.
 */

import { spawn } from "node:child_process";
import { mkdtemp, open, readFile, rm } from "node:fs/promises";
import { existsSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

const WEB = process.env.MM_E2E_WEB ?? "http://127.0.0.1:3001";
const API = process.env.MM_E2E_API ?? "http://127.0.0.1:8787";
const CHROME =
  process.env.MM_CHROME ??
  ["chromium", "chromium-browser", "google-chrome"].find((name) =>
    existsSync(`/usr/bin/${name}`),
  );

const TITLE = "E2E DELETE ME";
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function fail(message) {
  throw new Error(message);
}

async function preflight() {
  for (const [name, url] of [
    ["web", `${WEB}/`],
    ["api", `${API}/api/health`],
  ]) {
    try {
      const res = await fetch(url);
      if (!res.ok) fail(`${name} returned ${res.status}`);
    } catch (error) {
      fail(
        `${name} is not reachable at ${url} (${error.message}). Start "make dev" first.`,
      );
    }
  }
}

async function uploadFixture(title = TITLE) {
  const png = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAIAAABt+uBvAAAACXBIWXMAAAABAAAAAQBPJcTWAAAA2ElEQVR4nO3QQQ3AIADAQEjwNT+IQfMULH2yx52CpvM5e/Bt3Q74O4OCQcGgYFAwKBgUDAoGBYOCQcGgYFAwKBgUDAoGBYOCQcGgYFAwKBgUDAoGBYOCQcGgYFAwKBgUDAoGBYOCQcGgYFAwKBgUDAoGBYOCQcGgYFAwKBgUDAoGBYOCQcGgYFAwKBgUDAoGBYOCQcGgYFAwKBgUDAoGBYOCQcGgYFAwKBgUDAoGBYOCQcGgYFAwKBgUDAoGBYOCQcGgYFAwKBgUDAoGBYOCQcGgYFAwKBgUXvpzArXcR4BgAAAAAElFTkSuQmCC",
    "base64",
  );
  const form = new FormData();
  form.append("file", new Blob([png], { type: "image/png" }), "e2e.png");
  const res = await fetch(
    `${API}/api/uploads?kind=picture&title=${encodeURIComponent(title)}`,
    { method: "POST", body: form },
  );
  if (!res.ok) fail(`fixture upload failed: ${res.status} ${await res.text()}`);
  return res.json();
}

async function deleteIfPresent(id) {
  await fetch(`${API}/api/items/${id}?delete_file=true`, {
    method: "DELETE",
  }).catch(() => {});
}

function launchBrowser(profileDir, logFile) {
  if (!CHROME) fail("No Chromium binary found. Set MM_CHROME.");
  const child = spawn(
    CHROME,
    [
      "--headless=new",
      "--no-sandbox",
      "--disable-gpu",
      "--no-first-run",
      "--autoplay-policy=no-user-gesture-required",
      // Never talk to the desktop keyring from a throwaway test profile.
      "--password-store=basic",
      "--use-mock-keychain",
      "--remote-debugging-port=0",
      `--user-data-dir=${profileDir}`,
      "--window-size=1440,900",
      "about:blank",
    ],
    { stdio: ["ignore", "ignore", logFile] },
  );
  return child;
}

async function readPort(profileDir) {
  const file = path.join(profileDir, "DevToolsActivePort");
  for (let i = 0; i < 100; i += 1) {
    try {
      const [port] = (await readFile(file, "utf8")).split("\n");
      if (port) return Number(port);
    } catch {
      // not written yet
    }
    await sleep(100);
  }
  fail("Chromium did not open a DevTools port");
}

function createClient(wsUrl) {
  const ws = new WebSocket(wsUrl);
  let id = 0;
  const pending = new Map();
  const problems = [];
  const deletes = [];

  ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      pending.get(message.id)(message.result);
      pending.delete(message.id);
      return;
    }
    if (message.method === "Network.requestWillBeSent") {
      const request = message.params.request;
      if (request.method === "DELETE") deletes.push(request.url);
    }
    if (message.method === "Runtime.exceptionThrown") {
      const details = message.params.exceptionDetails;
      problems.push(
        `exception: ${(details.exception?.description ?? details.text ?? "").slice(0, 300)}`,
      );
    }
    if (message.method === "Runtime.consoleAPICalled" && message.params.type === "error") {
      problems.push(
        `console: ${message.params.args
          .map((arg) => arg.value ?? arg.description ?? "")
          .join(" ")
          .slice(0, 300)}`,
      );
    }
    if (message.method === "Network.responseReceived") {
      const { response } = message.params;
      if (
        response.status >= 400 &&
        response.url.startsWith(WEB) &&
        !response.url.includes("favicon")
      ) {
        problems.push(`http ${response.status}: ${response.url}`);
      }
    }
  };

  const ready = new Promise((resolve, reject) => {
    ws.onopen = resolve;
    setTimeout(() => reject(new Error("DevTools WebSocket did not open")), 8000);
  });

  function send(method, params = {}, sessionId) {
    return new Promise((resolve) => {
      const messageId = ++id;
      pending.set(messageId, resolve);
      ws.send(JSON.stringify({ id: messageId, method, params, sessionId }));
    });
  }

  return { ws, ready, send, problems, deletes };
}

async function main() {
  await preflight();
  console.log("PASS preflight: web and api reachable");

  let fixture = null;
  let live = null;
  let browser = null;
  let profileDir = null;
  let logFile = null;
  let resumeId = null;

  try {
    fixture = await uploadFixture();
    // Uploads land in the default gallery; detach so the picture renders as a
    // loose card on the Galleries tab (the All tab hides standalone pictures).
    const defaultGallery = (
      await (await fetch(`${API}/api/playlists?kind=gallery`)).json()
    ).playlists.find((entry) => entry.isDefault);
    if (!defaultGallery) fail("default gallery is missing");
    await fetch(`${API}/api/playlists/${defaultGallery.id}/items/${fixture.id}`, {
      method: "DELETE",
    });
    console.log(`PASS fixture uploaded: ${fixture.id}`);

    profileDir = await mkdtemp(path.join(tmpdir(), "mm-e2e-"));
    logFile = await open(path.join(tmpdir(), "mm-e2e-chrome.log"), "a");
    browser = launchBrowser(profileDir, logFile.fd);
    const port = await readPort(profileDir);
    console.log(`PASS chromium started on port ${port}`);

    const version = await (
      await fetch(`http://127.0.0.1:${port}/json/version`, {
        signal: AbortSignal.timeout(5000),
      })
    ).json();
    const client = createClient(version.webSocketDebuggerUrl);
    await client.ready;

    const { targetId } = await client.send("Target.createTarget", {
      url: `${WEB}/`,
    });
    const { sessionId } = await client.send("Target.attachToTarget", {
      targetId,
      flatten: true,
    });
    if (!sessionId) fail("could not attach to the page target");
    const cdp = (method, params = {}) => client.send(method, params, sessionId);
    await client.send("Target.activateTarget", { targetId });
    console.log("PASS DevTools session attached");
    await cdp("Runtime.enable");
    await cdp("Network.enable");
    await cdp("Emulation.setDeviceMetricsOverride", {
      width: 1440,
      height: 900,
      deviceScaleFactor: 1,
      mobile: false,
    });
    console.log("PASS CDP domains enabled");

    const evaluate = async (expression) => {
      const result = await cdp("Runtime.evaluate", {
        expression,
        returnByValue: true,
        awaitPromise: true,
      });
      if (result.exceptionDetails) {
        const message =
          result.exceptionDetails.exception?.description ?? "evaluate failed";
        if (message.includes("Execution context was destroyed")) return undefined;
        fail(message);
      }
      return result.result?.value;
    };

    async function waitFor(expression, timeout = 10000, label = expression) {
      const started = Date.now();
      while (Date.now() - started < timeout) {
        try {
          if (await evaluate(expression)) return true;
        } catch {
          // the page may be mid-reload; retry
        }
        await sleep(150);
      }
      fail(`timed out waiting for ${label}`);
    }

    async function clickCenter(expression) {
      const rect = await evaluate(`(() => {
        const el = ${expression};
        if (!el) return null;
        el.scrollIntoView({ block: "center" });
        const r = el.getBoundingClientRect();
        return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
      })()`);
      if (!rect) return false;
      for (const type of ["mouseMoved", "mousePressed", "mouseReleased"]) {
        await cdp("Input.dispatchMouseEvent", {
          type,
          x: rect.x,
          y: rect.y,
          button: "left",
          clickCount: 1,
        });
      }
      return true;
    }

    const byLabel = (label, startsWith = false) =>
      `[...document.querySelectorAll('button')].find(b => ${
        startsWith
          ? `(b.getAttribute('aria-label')||'').startsWith(${JSON.stringify(label)})`
          : `b.getAttribute('aria-label') === ${JSON.stringify(label)}`
      })`;

    await waitFor(
      "document.body && document.body.innerText.length > 0",
      15000,
      "page body",
    );
    // The All tab is collection-first: standalone pictures are hidden there.
    await sleep(1500);
    const allHasTitle = await evaluate(
      `document.body.innerText.includes(${JSON.stringify(TITLE)})`,
    );
    if (allHasTitle) fail("standalone picture leaked into the All tab");
    await evaluate("location.href = '/?kind=picture'");
    await waitFor(
      `document.body.innerText.includes(${JSON.stringify(TITLE)})`,
      15000,
      "library item on the Galleries tab",
    );
    console.log("PASS All hides pictures; Galleries tab renders the fixture");
    const overflow = await evaluate(
      "document.documentElement.scrollWidth - window.innerWidth",
    );
    if (overflow > 1) fail(`horizontal overflow: ${overflow}px`);
    console.log("PASS no horizontal overflow at 1440px");

    // Resume: store a position, then play that item and confirm the player
    // seeks to it.
    const musicPage = await (
      await fetch(`${API}/api/items?kind=music&limit=1`)
    ).json();
    const resumeItem = musicPage.items[0];
    if (!resumeItem) fail("no playable music item to test resume");
    resumeId = resumeItem.id;
    await fetch(`${API}/api/items/${resumeItem.id}/progress`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ positionMs: 30_000 }),
    });
    // Music is on the All tab; the Galleries tab only shows pictures.
    await evaluate("location.href = '/'");
    await waitFor(
      `document.body.innerText.includes(${JSON.stringify(resumeItem.title)})`,
      15000,
      "music item on the All tab",
    );
    // Reload so the library fetches the item with the stored position.
    await evaluate("location.reload()");
    await waitFor(
      `document.body.innerText.includes(${JSON.stringify(resumeItem.title)})`,
      15000,
      "library after progress reload",
    );

    // Play, queue, and remove a queue row.
    await clickCenter(byLabel(`Play ${resumeItem.title}`));
    await sleep(2500);
    let playback = await evaluate(
      "(() => { const v = document.querySelector('video'); return v ? { paused: v.paused, t: v.currentTime, src: v.currentSrc } : null; })()",
    );
    if (!playback || playback.paused || !playback.src) {
      await sleep(2500);
      playback = await evaluate(
        "(() => { const v = document.querySelector('video'); return v ? { paused: v.paused, t: v.currentTime, src: v.currentSrc } : null; })()",
      );
    }
    if (!playback || playback.paused || !playback.src) {
      fail(`playback did not start (${JSON.stringify(playback)})`);
    }
    if (playback.t < 20) {
      fail(`playback did not resume near 30s (t=${playback.t})`);
    }
    console.log("PASS playback resumes from the stored position");

    await evaluate(`(() => {
      const b = [...document.querySelectorAll('button')].find((x) => {
        const label = x.getAttribute('aria-label') || '';
        return label.startsWith('Queue ') && !label.includes(${JSON.stringify(resumeItem.title)});
      });
      if (b) b.click();
      return !!b;
    })()`);
    await clickCenter(byLabel("Queue"));
    await waitFor(
      "document.querySelectorAll('li[draggable]').length >= 2",
      5000,
      "queue rows",
    );
    const before = await evaluate("document.querySelectorAll('li[draggable]').length");
    await evaluate(
      `[...document.querySelectorAll('button')].find(b => (b.getAttribute('aria-label')||'').startsWith('Remove ') && b.closest('li[draggable]')).click()`,
    );
    await sleep(400);
    const after = await evaluate("document.querySelectorAll('li[draggable]').length");
    if (after !== before - 1) fail(`queue removal failed: ${before} -> ${after}`);
    await evaluate(`${byLabel("Close queue")}.click()`);
    console.log("PASS playback starts and a queue row can be removed");

    // Pause flushes the position; restore the item afterwards.
    await clickCenter(byLabel("Pause"));
    await sleep(700);
    const saved = await (
      await fetch(`${API}/api/items/${resumeItem.id}`)
    ).json();
    if (!(saved.positionMs > 20_000)) {
      fail(`position was not saved on pause (${saved.positionMs})`);
    }
    await fetch(`${API}/api/items/${resumeItem.id}/progress`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ positionMs: 0 }),
    });
    console.log("PASS playback position is saved on pause");

    // Theme switch + persistence.
    await evaluate(`${byLabel("Appearance settings")}.click()`);
    await evaluate(
      `[...document.querySelectorAll('button')].find(b => b.textContent.trim() === 'Ocean').click()`,
    );
    const theme = await evaluate("document.documentElement.dataset.theme");
    if (theme !== "ocean") fail(`theme was ${theme}`);
    await evaluate("location.reload()");
    await sleep(1200);
    await waitFor(
      "document.documentElement.dataset.theme === 'ocean'",
      15000,
      "theme after reload",
    );
    console.log("PASS theme switches and persists");

    // Delete the fixture through the UI, back on the Galleries tab.
    await evaluate("location.href = '/?kind=picture'");
    await waitFor(
      `!!${byLabel(`Delete ${TITLE}`)}`,
      15000,
      "delete button",
    );
    await evaluate(`${byLabel(`Delete ${TITLE}`)}.click()`);
    await waitFor("!!document.querySelector('[role=\"dialog\"]')", 5000, "confirm dialog");
    const confirmClicked = await evaluate(
      `(() => {
        const button = [...document.querySelectorAll('[role="dialog"] button')].find(b => b.textContent.includes('Delete permanently'));
        if (!button) return false;
        button.click();
        return true;
      })()`,
    );
    if (!confirmClicked) fail("confirm button was not found");

    let remaining = { total: -1 };
    for (let attempt = 0; attempt < 20; attempt += 1) {
      remaining = await (
        await fetch(`${API}/api/items?q=${encodeURIComponent(TITLE)}`)
      ).json();
      if (remaining.total === 0) break;
      await sleep(250);
    }
    if (remaining.total !== 0) {
      const toast = await evaluate(
        `[...document.querySelectorAll('[role="status"]')].map(e => e.innerText).join(' | ')`,
      );
      fail(
        `item still exists after delete (total=${remaining.total}, DELETE calls=${JSON.stringify(client.deletes)}, toast=${JSON.stringify(toast)})`,
      );
    }
    fixture = null;
    console.log("PASS delete removes the item and its file");

    // Live updates: a change made outside the tab appears without a refresh.
    live = await uploadFixture("E2E LIVE ITEM");
    await fetch(`${API}/api/playlists/${defaultGallery.id}/items/${live.id}`, {
      method: "DELETE",
    });
    await waitFor(
      `document.body.innerText.includes('E2E LIVE ITEM')`,
      12000,
      "live item appears",
    );
    await fetch(`${API}/api/items/${live.id}?delete_file=true`, { method: "DELETE" });
    await waitFor(
      `!document.body.innerText.includes('E2E LIVE ITEM')`,
      12000,
      "live item disappears",
    );
    live = null;
    console.log("PASS live updates arrive without a refresh");

    if (client.problems.length > 0) {
      fail(`browser problems:\n  ${client.problems.join("\n  ")}`);
    }
    console.log("PASS no console errors, exceptions, or failed resources");
    console.log("\nE2E smoke passed.");
  } finally {
    if (resumeId) {
      await fetch(`${API}/api/items/${resumeId}/progress`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ positionMs: 0 }),
      }).catch(() => {});
    }
    if (fixture) await deleteIfPresent(fixture.id);
    if (live) await deleteIfPresent(live.id);
    if (browser) {
      browser.kill();
      await new Promise((resolve) => {
        browser.once("exit", resolve);
        setTimeout(resolve, 1500);
      });
    }
    if (profileDir) {
      try {
        await rm(profileDir, {
          recursive: true,
          force: true,
          maxRetries: 5,
          retryDelay: 100,
        });
      } catch {
        // the browser may still be flushing its profile; not a test failure
      }
    }
    if (logFile) await logFile.close();
  }
}

main().catch((error) => {
  console.error(`\nE2E FAILED: ${error.message}`);
  process.exit(1);
});
