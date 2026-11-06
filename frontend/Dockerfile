# The frontend server, which renders the pages. Built by compose.prod.yaml; run the tests before building.

FROM node:24-slim AS build
WORKDIR /build
# Dependencies first, so they are installed again only when the lock file changes
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM node:24-slim
WORKDIR /app
ENV NODE_ENV=production \
    PORT=4000
# The server build carries what it needs, so no node_modules
COPY --from=build /build/dist/frontend ./dist/frontend
USER node
EXPOSE 4000
CMD ["node", "dist/frontend/server/server.mjs"]
