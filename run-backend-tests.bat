@echo off
rem Run backend Maven tests using the official Maven Docker image
rem Requires Docker to be installed and running on Windows.
setlocal
echo Running backend tests inside Maven Docker container...
docker run --rm -v "%cd%":/workspace -w /workspace maven:3.9.5-eclipse-temurin-17 mvn -B test
if %ERRORLEVEL% NEQ 0 (
  echo Docker run failed or tests failed. Ensure Docker is installed and running.
  exit /b %ERRORLEVEL%
)
echo Tests finished.
endlocal
