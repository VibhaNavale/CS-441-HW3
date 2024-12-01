# Stage 1: Build Stage
FROM openjdk:17-slim AS build

# Set working directory
WORKDIR /app

# Copy the project files into the container
COPY . /app

# Install Scala and sbt
RUN apt-get update && apt-get install -y \
    curl \
    && curl -Lo sbt.deb https://github.com/sbt/sbt/releases/download/v1.8.0/sbt-1.8.0.deb \
    && dpkg -i sbt.deb \
    && apt-get install -y -f

# Set the environment variable for SBT
ENV SBT_OPTS="-Xmx2G"

# Build the project
RUN sbt clean compile

# Stage 2: Runtime Stage
FROM openjdk:17-slim AS runtime

# Set working directory
WORKDIR /app

# Copy the compiled project from the build stage
COPY --from=build /app/target/scala-2.12 /app/target/scala-2.12

# Expose the port that the application will run on
EXPOSE 8080

# Command to run the application
CMD ["java", "-cp", "/app/target/scala-2.12/*", "App.AkkaHttpServer"]
