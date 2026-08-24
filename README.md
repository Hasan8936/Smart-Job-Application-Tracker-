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

## Password reset and Google sign-in

Password reset uses SMTP and sends links to `${FRONTEND_URL}/reset-password`. Set
`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, and `FRONTEND_URL` in
the backend environment. The reset-token table is created by Flyway migration V4.

Google sign-in is enabled when `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are
set. In Google Cloud Console, add this redirect URI:

```
https://<your-backend-host>/login/oauth2/code/google
```

For local development use `http://localhost:8080/login/oauth2/code/google` and
set `VITE_API_BASE=http://localhost:8080/api` for the frontend.

