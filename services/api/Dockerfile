# syntax=docker/dockerfile:1

# The build stage uses the Maven wrapper rather than a Maven image. The wrapper pins the Maven
# version in the repo, so the container build and `make api` on a laptop use the same one — which
# is the entire point of committing mvnw. A `maven:` base image would silently use whatever version
# that tag currently carries.
#
# NOTE: the previous version of this file called bare `mvn` on a JDK image, which has no Maven at
# all. It failed with "mvn: not found" the first time anyone actually built it.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Wrapper first, then the pom: dependency resolution only re-runs when the pom changes, not on
# every source edit.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B --no-transfer-progress dependency:go-offline || true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

# curl is not in the JRE image, and the compose healthcheck needs it to reach /actuator/health.
# Installed rather than worked around: a port-open check would report "healthy" for a JVM that
# started and then failed to connect to the database, which is precisely the case worth catching.
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*

# Runs unprivileged. This service holds the database credentials and will hold aggregator tokens;
# root in a container is one escape away from root on the host (docs/SECURITY.md).
RUN useradd --system --uid 10001 --create-home finances
USER finances

COPY --from=build --chown=finances:finances /build/target/*.jar app.jar
EXPOSE 8080

# The healthcheck itself lives in infra/docker-compose.yml, next to the dependency ordering that
# uses it.

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
