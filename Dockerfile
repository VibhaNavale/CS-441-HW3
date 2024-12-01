# Use an official OpenJDK runtime as a parent image
FROM openjdk:11-jdk-slim AS builder

# Set working directory
WORKDIR /app

# Install SBT
RUN \
  apt-get update && \
  apt-get install -y curl && \
  curl -L -o sbt-1.5.5.deb https://repo.scala-sbt.org/scalasbt/debian/sbt-1.5.5.deb && \
  dpkg -i sbt-1.5.5.deb && \
  apt-get update && \
  apt-get install -y sbt && \
  rm sbt-1.5.5.deb

# Copy project files
COPY project/build.properties project/build.properties
COPY project/plugins.sbt project/plugins.sbt
COPY build.sbt .
COPY src ./src

# Fetch dependencies and compile
RUN sbt clean compile

# Create fat jar
RUN sbt assembly

# Second stage for a smaller runtime image
FROM openjdk:11-jre-slim

# Set working directory
WORKDIR /app

# Copy the assembled jar from the builder stage
COPY --from=builder /app/target/scala-2.12/CS-441-HW-3-assembly-0.1.0-SNAPSHOT.jar /app/app.jar

# Copy the resources directory to maintain the original structure
COPY --from=builder /app/src/main/resources /app/src/main/resources

# Create output directory
RUN mkdir -p /app/output

# Expose the port your app runs on
EXPOSE 8080

# Run the main class directly with config in its original path
# Use -Dakka.http.server.default-host-connection-pool.max-connections=32 to prevent connection pool issues
CMD ["java", "-Dconfig.file=/app/src/main/resources/application.conf", "-Dakka.http.server.default-host-connection-pool.max-connections=32", "-cp", "/app/app.jar", "App.AkkaHttpServer"]
