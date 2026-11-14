import type { Metadata } from "next";

import { Sidebar } from "@/components/Sidebar";

import "./globals.css";

export const metadata: Metadata = {
  title: "MediaManager",
  description: "Local library for downloaded music, video, pictures and books.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="bg-canvas text-ink antialiased">
        <div className="mx-auto flex min-h-dvh w-full max-w-[1560px] flex-col md:flex-row">
          <Sidebar />
          <main className="min-w-0 flex-1 px-4 pt-5 pb-24 sm:px-6 md:px-10 md:pt-9">
            {children}
          </main>
        </div>
      </body>
    </html>
  );
}
