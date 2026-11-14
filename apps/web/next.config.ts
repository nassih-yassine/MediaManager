import type { NextConfig } from "next";

const API = process.env.MM_API_URL ?? "http://127.0.0.1:8787";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${API}/api/:path*` },
      { source: "/files/:path*", destination: `${API}/files/:path*` },
      { source: "/thumbs/:path*", destination: `${API}/thumbs/:path*` },
    ];
  },
};

export default nextConfig;
