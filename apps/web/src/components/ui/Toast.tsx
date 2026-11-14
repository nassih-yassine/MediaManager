"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

type ToastKind = "success" | "error" | "info";

interface ToastItem {
  id: number;
  message: string;
  kind: ToastKind;
}

type PushToast = (message: string, kind?: ToastKind) => void;

const ToastContext = createContext<PushToast | null>(null);

export function useToast(): PushToast {
  const push = useContext(ToastContext);
  if (!push) throw new Error("useToast must be used inside ToastProvider");
  return push;
}

const KIND_STYLES: Record<ToastKind, string> = {
  success: "border-accent/50",
  error: "border-danger/60 text-danger",
  info: "border-line",
};

export function ToastProvider({
  children,
  durationMs = 3800,
}: {
  children: React.ReactNode;
  durationMs?: number;
}) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);

  useEffect(() => {
    const pending = timers.current;
    return () => {
      pending.forEach(clearTimeout);
    };
  }, []);

  const push = useCallback<PushToast>(
    (message, kind = "info") => {
      const id = Date.now() + Math.random();
      setToasts((current) => [...current.slice(-2), { id, message, kind }]);
      timers.current.push(
        setTimeout(() => {
          setToasts((current) => current.filter((toast) => toast.id !== id));
        }, durationMs),
      );
    },
    [durationMs],
  );

  const value = useMemo(() => push, [push]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="pointer-events-none fixed bottom-28 left-4 z-[70] flex w-[min(22rem,calc(100vw-2rem))] flex-col gap-2"
        role="status"
        aria-live="polite"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`animate-toast-in rounded-xl border bg-panel px-4 py-3 text-sm shadow-xl ${KIND_STYLES[toast.kind]}`}
          >
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
