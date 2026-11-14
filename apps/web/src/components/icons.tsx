type Props = { className?: string };

const base = "h-4 w-4";

export function PlayIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden>
      <path d="M8 5.2v13.6L19 12z" />
    </svg>
  );
}

export function PauseIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden>
      <rect x="7" y="5" width="3.6" height="14" rx="1" />
      <rect x="13.4" y="5" width="3.6" height="14" rx="1" />
    </svg>
  );
}

export function PrevIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden>
      <path d="M7 5h2.2v14H7zM19 5.6v12.8L9.6 12z" />
    </svg>
  );
}

export function NextIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden>
      <path d="M14.8 5H17v14h-2.2zM5 5.6v12.8L14.4 12z" />
    </svg>
  );
}

export function ShuffleIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M3 7h3.5l10 10H20" />
      <path d="M3 17h3.5l10-10H20" />
      <path d="M17 4l3 3-3 3M17 14l3 3-3 3" />
    </svg>
  );
}

export function RepeatIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M17 3l3 3-3 3" />
      <path d="M20 6H8a4 4 0 0 0-4 4v1" />
      <path d="M7 21l-3-3 3-3" />
      <path d="M4 18h12a4 4 0 0 0 4-4v-1" />
    </svg>
  );
}

export function RepeatOneIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M17 3l3 3-3 3" />
      <path d="M20 6H8a4 4 0 0 0-4 4v1" />
      <path d="M7 21l-3-3 3-3" />
      <path d="M4 18h12a4 4 0 0 0 4-4v-1" />
      <path d="M11.4 14.5h1.2V10l-1.3.8" />
    </svg>
  );
}

export function VolumeIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M4 9.5v5h3l4.5 3.5v-12L7 9.5z" />
      <path d="M15.5 8.8a4.5 4.5 0 0 1 0 6.4M18.2 6.2a8.2 8.2 0 0 1 0 11.6" />
    </svg>
  );
}

export function MuteIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M4 9.5v5h3l4.5 3.5v-12L7 9.5z" />
      <path d="M16 9.5l5 5M21 9.5l-5 5" />
    </svg>
  );
}

export function QueueIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" className={className} aria-hidden>
      <path d="M4 7h11M4 12h11M4 17h7" />
      <path d="M19 11v7.2a2 2 0 1 1-2-2h2" />
    </svg>
  );
}

export function CloseIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" className={className} aria-hidden>
      <path d="M6 6l12 12M18 6L6 18" />
    </svg>
  );
}

export function TrashIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M4 7h16M9 7V5h6v2M7 7l1 13h8l1-13M10 11v6M14 11v6" />
    </svg>
  );
}

export function PlusIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9"
      strokeLinecap="round" className={className} aria-hidden>
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

export function CheckIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M5 12.5l4.5 4.5L19 7" />
    </svg>
  );
}

export function OpenIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M14 4h6v6M20 4l-8 8" />
      <path d="M19 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h5" />
    </svg>
  );
}

export function SearchIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" className={className} aria-hidden>
      <circle cx="11" cy="11" r="6.5" />
      <path d="M16 16l4.5 4.5" />
    </svg>
  );
}

export function LibraryIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M4 5.5v13M9 5.5v13" />
      <path d="M13.5 5.8l5 12.4" />
    </svg>
  );
}

export function AddMediaIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <rect x="3.5" y="4.5" width="12" height="15" rx="2.5" />
      <path d="M18.5 9v9M22 11.5h-7M8 8.5h3M8 12h3" />
    </svg>
  );
}

export function DiscIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" className={className} aria-hidden>
      <circle cx="12" cy="12" r="8.5" />
      <circle cx="12" cy="12" r="2.2" />
    </svg>
  );
}

export function SelectIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <rect x="4" y="4" width="16" height="16" rx="4" strokeDasharray="3 3" />
      <path d="M8.5 12.5l2.5 2.5 4.5-5" />
    </svg>
  );
}

export function ChevronDownIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M6 9l6 6 6-6" />
    </svg>
  );
}

export function ArrowUpIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M12 19V5M6 11l6-6 6 6" />
    </svg>
  );
}

export function ArrowDownIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M12 5v14M18 13l-6 6-6-6" />
    </svg>
  );
}

export function MoreIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden>
      <circle cx="5" cy="12" r="1.6" />
      <circle cx="12" cy="12" r="1.6" />
      <circle cx="19" cy="12" r="1.6" />
    </svg>
  );
}

export function MovieIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <rect x="3" y="5" width="18" height="14" rx="2.5" />
      <path d="M7 5v14M17 5v14M3 12h18" />
    </svg>
  );
}

export function ImageIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <rect x="3" y="4" width="18" height="16" rx="2.5" />
      <circle cx="8.5" cy="9.5" r="1.4" />
      <path d="M21 16l-4.5-4.5L7 21" />
    </svg>
  );
}

export function BookIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5z" />
      <path d="M4 20.5A2.5 2.5 0 0 1 6.5 18H20v3H6.5A2.5 2.5 0 0 1 4 20.5z" />
    </svg>
  );
}

export function LinkIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M10 13.5a4 4 0 0 0 5.7 0l2.8-2.8a4 4 0 1 0-5.7-5.7L11.5 6.3" />
      <path d="M14 10.5a4 4 0 0 0-5.7 0L5.5 13.3a4 4 0 1 0 5.7 5.7l1.3-1.3" />
    </svg>
  );
}

export function PlaylistAddIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M4 6.5h10M4 11h10M4 15.5h6" />
      <path d="M17.5 13v6M14.5 16h6" />
    </svg>
  );
}

export function ChevronLeftIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M15 5l-7 7 7 7" />
    </svg>
  );
}

export function ChevronRightIcon({ className = base }: Props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      <path d="M9 5l7 7-7 7" />
    </svg>
  );
}
