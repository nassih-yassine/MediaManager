"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { PlayerBar } from "@/components/PlayerBar";
import { Sidebar } from "@/components/Sidebar";
import { useAuth } from "@/lib/auth";
import { PlayerProvider } from "@/lib/player";

function AuthGate({ children }: { children: React.ReactNode }) {
  const { user, loading, needsSetup } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (loading) return;
    if (needsSetup) router.replace("/setup");
    else if (!user) router.replace("/login");
  }, [loading, needsSetup, user, router]);

  if (loading || needsSetup || !user) {
    return (
      <div className="flex min-h-dvh items-center justify-center text-sm text-muted">
        Loading…
      </div>
    );
  }
  return <>{children}</>;
}

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGate>
      <PlayerProvider>
        <div className="mx-auto flex min-h-dvh w-full max-w-[1600px] flex-col md:flex-row">
          <Sidebar />
          <main className="min-w-0 flex-1 px-4 pt-5 pb-40 sm:px-6 md:px-10 md:pt-9">
            {children}
          </main>
        </div>
        <PlayerBar />
      </PlayerProvider>
    </AuthGate>
  );
}
