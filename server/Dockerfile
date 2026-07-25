FROM node:24.15.0-alpine AS web-build

WORKDIR /workspace

RUN corepack enable && corepack prepare pnpm@11.13.1 --activate

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml tsconfig.json uno.config.ts ./
COPY packages ./packages
COPY apps/stackchan-console ./apps/stackchan-console
COPY scripts ./scripts

RUN pnpm install --frozen-lockfile
RUN pnpm --filter @stackchan/console run build

FROM maven:3.9.16-eclipse-temurin-21 AS server-build

WORKDIR /workspace/server

COPY server/pom.xml .
RUN mvn dependency:go-offline

COPY server/src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=server-build /workspace/server/target/stackchan-companion-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --from=web-build /workspace/apps/stackchan-console/dist /app/public
COPY server/wakenet-models /app/wakenet-models

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
