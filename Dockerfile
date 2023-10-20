# ------------------------
# Setup Builder container

FROM clojure:temurin-17-alpine AS builder

# Set Clojure CLI version (defaults to latest release)
# ENV CLOJURE_VERSION=1.11.1.1155

# Create directory for project code (working directory)
RUN mkdir -p /build

# Set Docker working directory
WORKDIR /build

# Cache and install Clojure dependencies
# Add before copying code to cache the layer even if code changes
COPY deps.edn Makefile /build/
RUN make deps

# Copy project to working directory
# .dockerignore file excludes all but essential files
COPY ./ /build


# ------------------------
# Test and Package application via Makefile
# `make all` calls `deps`, `test-ci`, `dist` and `clean` tasks
# using shared library cache mounted by pipeline process


# `dist` task packages Clojure service as an uberjar
# - creates: /build/practicalli-gameboard-api-service.jar
# - uses command `clojure -T:build uberjar`
RUN make dist

# End of Docker builder image
# ------------------------------------------

# ------------------------
# Setup Run-time Container

# Official OpenJDK Image
FROM eclipse-temurin:17-alpine

# Add operating system packages
# - dumb-init to ensure SIGTERM sent to java process running Clojure service
# - Curl and jq binaries for manual running of system integration scripts
# check for newer package versions: https://pkgs.alpinelinux.org/
RUN apk add --no-cache \
    dumb-init~=1.2.5 \
    curl~=8.0.1 \
    jq~=1.6

# Create Non-root group and user to run service securely
RUN addgroup -S clojure && adduser -S clojure -G clojure

# Create directory to contain service archive, owned by non-root user
RUN mkdir -p /service && chown -R clojure. /service

# Tell docker that all future commands should run as the appuser user
USER clojure

# Copy service archive file from Builder image
WORKDIR /service
COPY --from=builder /build/target/jcpsantiago-thearqivist-standalone.jar /service/

# ------------------------
# Set Service Environment variables

# ENV HTTP_SERVER_PORT=
# ENV MYSQL_DATABASE=
ENV SERVICE_PROFILE=prod

# Expose port of HTTP Server
EXPOSE 8080

# ------------------------
# Run service

# Docker Service heathcheck
# docker inspect --format='{{json .State.Health}}' container-name
# - local heathcheck defined in `compose.yaml` service definition
# TODO: Set URL in Dockerfile to point to deployed service
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 CMD [ "curl --fail http://localhost:8080 || exit 1" ]

# JDK_JAVA_OPTIONS environment variable for setting JVM options
# Use JVM options that optomise running in a container
# For very low latency, use the Z Garbage collector "-XX:+UseZGC"
ENV JDK_JAVA_OPTIONS "-XshowSettings:system -XX:+UseContainerSupport -XX:MaxRAMPercentage=90"

# Start service using dumb-init and java run-time
# (overrides `jshell` entrypoint - default in eclipse-temuring image)
ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["java", "-jar", "/service/jcpsantiago-thearqivist-standalone.jar"]
