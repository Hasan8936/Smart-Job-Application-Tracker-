#!/usr/bin/env bash
# Run backend Maven tests using the official Maven Docker image
# Requires Docker to be installed and running.
set -e
echo "Running backend tests inside Maven Docker container..."
docker run --rm -v "$(pwd)":/workspace -w /workspace maven:3.9.5-eclipse-temurin-17 mvn -B test
echo "Tests finished."
