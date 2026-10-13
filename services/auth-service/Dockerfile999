FROM node:20-alpine AS builder
WORKDIR /app
# bcrypt has a native addon; alpine has no compiler toolchain by default.
RUN apk add --no-cache python3 make g++
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json tsconfig.build.json nest-cli.json ./
COPY src ./src
RUN npm run build
# Strip devDependencies out of the already-built node_modules (keeps the
# compiled bcrypt native binary; does not recompile) so the runtime image
# needs no toolchain of its own.
RUN npm prune --omit=dev

FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY package.json ./
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/dist ./dist

RUN addgroup -S nestauth && adduser -S nestauth -G nestauth
USER nestauth

EXPOSE 3000
CMD ["node", "dist/main.js"]
