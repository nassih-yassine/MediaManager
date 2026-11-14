"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { api } from "@/lib/api";

export function DeleteButton({ id }: { id: string }) {
  const router = useRouter();
  const [armed, setArmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!armed) return;
    const timer = setTimeout(() => setArmed(false), 5000);
    return () => clearTimeout(timer);
  }, [armed]);

  async function remove() {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/items/${id}?delete_file=true`, { method: "DELETE" });
      router.push("/");
      router.refresh();
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
      setArmed(false);
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      {armed ? (
        <>
          <button
            type="button"
            onClick={remove}
            disabled={busy}
            className="rounded-lg border border-danger/60 bg-danger/10 px-3.5 py-2 text-sm font-medium text-danger transition-colors hover:bg-danger/15 disabled:opacity-50"
          >
            {busy ? "Deleting…" : "Delete file and entry"}
          </button>
          <button
            type="button"
            onClick={() => setArmed(false)}
            className="rounded-lg border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
          >
            Keep
          </button>
        </>
      ) : (
        <button
          type="button"
          onClick={() => setArmed(true)}
          className="rounded-lg border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:border-danger/50 hover:text-danger"
        >
          Delete
        </button>
      )}
      {error ? <span className="text-xs text-danger">{error}</span> : null}
    </div>
  );
}
