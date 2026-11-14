import type { NextConfig } from "next";

const API = process.env.MM_API_URL ?? "http://127.0.0.1:8787";

const nextConfig: NextConfig = {
  // The floating Next.js badge is dev-only noise; the desktop window should
  // look like the app, not the framework.
  devIndicators: false,
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${API}/api/:path*` },
      { source: "/files/:path*", destination: `${API}/files/:path*` },
      { source: "/thumbs/:path*", destination: `${API}/thumbs/:path*` },
    ];
  },
};

export default nextConfig;
