# Smart Job Tracker (backend)

Minimal Spring Boot backend scaffold for the Smart Job Application Tracker.

Run locally:

```bash
mvn spring-boot:run
```

Edit database settings in `src/main/resources/application.yml`.

## Running backend tests locally without Maven installed

If you don't have Maven on your PATH, you can run the backend tests using Docker.

Windows:

```
run-backend-tests.bat
```

Unix / WSL / macOS:

```
./run-backend-tests.sh
```

Both scripts use the official `maven` Docker image and mount the project directory into the container.
Ensure Docker is installed and running before invoking them.

## Start backend + database with Docker Compose

If you have Docker installed, you can start Postgres and the backend together with:

```
docker compose up --build
```

This will build the backend image (using the included `Dockerfile`) and start a Postgres container. The backend will be available at `http://localhost:8080`.

To stop and remove containers:

```
docker compose down
```

