FROM node:22-bookworm-slim

# next.config.ts bakes the rewrite target at build time, so the API URL must be
# present here rather than only at runtime.
ARG MM_API_URL=http://api:8787
ENV MM_API_URL=$MM_API_URL \
    PORT=3001 \
    HOSTNAME=0.0.0.0

WORKDIR /app
COPY package.json package-lock.json ./
# Build needs TypeScript/Tailwind (devDependencies); only the runtime is
# production, so do not set NODE_ENV=production before npm ci.
RUN npm ci
COPY . .
RUN npm run build

ENV NODE_ENV=production
EXPOSE 3001
CMD ["npm", "start"]
