# MediCore

MediCore is a college Software Engineering prototype for managing medical test requests, scheduling, laboratory results and reports. The original MTMS project report and approved decisions are documented under `documentation/`.

## Preview the frontend

The quickest way to explore the four role workspaces is the read-only synthetic preview. It does not require MySQL or the backend.

```powershell
py -m http.server 4173 --directory frontend
```

Open `http://localhost:4173` and choose a Patient, Doctor, Lab Technician or Administrator example account. No password is needed for these static previews. The registration page also offers a temporary patient preview using fictional information. Demo actions do not save data. Do not enter real patient information.

For real API-backed registration and login, follow [`backend/medicore/README.md`](backend/medicore/README.md) to configure MySQL and start Spring Boot. Flyway applies the initial schema at startup, so inspect and back up any existing database before connecting it.

## Project layout

- `frontend/` — HTML, CSS and Vanilla JavaScript client.
- `backend/medicore/` — Java 21, Spring Boot, Maven, MySQL and Flyway API.
- `documentation/` — requirements, architecture, database, API and team plans, plus the original project report.

## Vercel

The frontend can be deployed as a static site by setting Vercel's **Root Directory** to `frontend` and using no build command. This publishes the synthetic preview pages. The Spring Boot API and MySQL database are not hosted by that static deployment; live API login requires separately hosted backend/database services and a configured API base URL.
