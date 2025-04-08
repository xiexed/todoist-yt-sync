# docker build --platform linux/amd64 -t registry.jetbrains.team/p/td-sync/containers/td-sync:latest .
# docker push registry.jetbrains.team/p/td-sync/containers/td-sync:latest

FROM clojure:openjdk-11-lein AS build
WORKDIR /app
COPY project.clj .
RUN lein deps
COPY . .
RUN lein ring uberjar

FROM openjdk:11-jre-slim
WORKDIR /app

# Add non-root user with explicit numeric ID
RUN groupadd -r -g 1000 appuser && useradd -r -u 1000 -g appuser appuser

# Create directory for external config
RUN mkdir -p /config && chown -R 1000:1000 /config
VOLUME /config

COPY --from=build /app/target/todoist-sync-*-standalone.jar app.jar
RUN chown -R 1000:1000 /app

# Switch to non-root user with numeric ID
USER 1000

EXPOSE 3000
ENV CONFIG_FILE=/config/application.conf

ENTRYPOINT ["sh", "-c", "java -Dconfig.file=${CONFIG_FILE} -jar app.jar"]