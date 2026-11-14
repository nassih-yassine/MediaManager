FROM node:22-bookworm-slim

# next.config.ts bakes the rewrite target at build time, so the API URL must be
# present here rather than only at runtime.
ARG MM_API_URL=http://api:8787
ENV MM_API_URL=$MM_API_URL \
    NODE_ENV=production \
    PORT=3001 \
    HOSTNAME=0.0.0.0

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

EXPOSE 3001
CMD ["npm", "start"]
