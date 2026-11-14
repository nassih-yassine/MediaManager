"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { useToast } from "@/components/ui/Toast";
import { TrashIcon } from "@/components/icons";
import { api } from "@/lib/api";
import { usePlayer } from "@/lib/player";

export function DeleteButton({ id }: { id: string }) {
  const router = useRouter();
  const player = usePlayer();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  async function remove() {
    setBusy(true);
    try {
      await api(`/api/items/${id}?delete_file=true`, { method: "DELETE" });
      player.removeItem(id);
      toast("Item deleted", "success");
      router.push("/");
    } catch (err) {
      toast((err as Error).message, "error");
      setBusy(false);
      setOpen(false);
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex items-center gap-2 rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:border-danger/50 hover:text-danger"
      >
        <TrashIcon className="h-3.5 w-3.5" />
        Delete
      </button>
      <ConfirmDialog
        open={open}
        busy={busy}
        title="Delete this item?"
        description="The entry and the file on disk will be removed. This cannot be undone."
        confirmLabel="Delete permanently"
        onCancel={() => setOpen(false)}
        onConfirm={remove}
      />
    </>
  );
}
