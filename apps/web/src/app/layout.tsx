import type { Metadata } from "next";

import { PlayerBar } from "@/components/PlayerBar";
import { Sidebar } from "@/components/Sidebar";
import { PlayerProvider } from "@/lib/player";

import "./globals.css";

export const metadata: Metadata = {
  title: "MediaManager",
  description: "Local library for downloaded music, video, pictures and books.",
};

const APPEARANCE_SCRIPT = `(function(){try{var raw=localStorage.getItem('mm.appearance');var s=raw?JSON.parse(raw):null;var d=document.documentElement;var m=(s&&s.mode)?s.mode:(window.matchMedia('(prefers-color-scheme: light)').matches?'light':'dark');d.setAttribute('data-theme',(s&&s.theme)||'slate');d.setAttribute('data-mode',m);}catch(e){}})();`;

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="bg-canvas text-ink antialiased">
        <script dangerouslySetInnerHTML={{ __html: APPEARANCE_SCRIPT }} />
        <PlayerProvider>
          <div className="mx-auto flex min-h-dvh w-full max-w-[1560px] flex-col md:flex-row">
            <Sidebar />
            <main className="min-w-0 flex-1 px-4 pt-5 pb-40 sm:px-6 md:px-10 md:pt-9">
              {children}
            </main>
          </div>
          <PlayerBar />
        </PlayerProvider>
      </body>
    </html>
  );
}
