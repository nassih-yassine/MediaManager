"use client";

import { PlaylistMenu } from "@/components/PlaylistMenu";
import { PlayIcon, QueueIcon } from "@/components/icons";
import { useToast } from "@/components/ui/Toast";
import { usePlayer } from "@/lib/player";
import { isPlayable } from "@/lib/player-utils";
import type { Item } from "@/lib/types";

export function ItemActions({ item }: { item: Item }) {
  const player = usePlayer();
  const toast = useToast();
  const resume = item.positionMs > 3_000;
  const isCurrent = player.current?.id === item.id;
  const playable = isPlayable(item);

  return (
    <div className="flex flex-wrap items-center gap-2">
      {playable ? (
        <>
          <button
            type="button"
            onClick={() => {
              if (isCurrent) player.toggle();
              else player.playItem(item);
            }}
            className="flex items-center gap-2 rounded-full bg-accent px-4 py-2 text-sm font-semibold text-accent-ink transition-opacity hover:opacity-90"
          >
            <PlayIcon className="h-3.5 w-3.5" />
            {isCurrent && player.playing ? "Pause" : resume ? "Resume" : "Play"}
          </button>
          <button
            type="button"
            onClick={() => {
              player.playNext(item);
              toast("Playing next", "info");
            }}
            className="rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
          >
            Play next
          </button>
          <button
            type="button"
            onClick={() => {
              player.addToQueue([item]);
              toast("Added to queue", "success");
            }}
            className="flex items-center gap-2 rounded-full border border-line px-3.5 py-2 text-sm text-muted transition-colors hover:text-ink"
          >
            <QueueIcon className="h-3.5 w-3.5" />
            Add to queue
          </button>
        </>
      ) : null}
      <PlaylistMenu
        itemIds={[item.id]}
        kind={item.kind === "picture" ? "gallery" : "playlist"}
        variant="pill"
        placement="bottom"
      />
    </div>
  );
}
