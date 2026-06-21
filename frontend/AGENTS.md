# AGENTS.md

## Project

React frontend for **Campus Gym Pro** (校园运动场预约系统) — a high-concurrency campus sports venue reservation system. Part of a larger monorepo at `Campus-Gym-Pro/` that includes a Spring Boot backend, MySQL, Redis, and RabbitMQ.

## Stack

- React 18 + Vite 5 + Ant Design 5 (antd)
- React Router 6, Axios, Day.js
- UI language: Chinese (zh-CN)

## Commands

- `npm run dev` — Vite dev server on **port 3000**
- `npm run build` — production build to `dist/`
- `npm run preview` — preview production build

No lint, typecheck, or test scripts are configured. There is no test framework installed.

## Backend dependency

The frontend proxies all `/api/*` requests to `http://localhost:8080` (Vite dev server proxy, see `vite.config.js`). The backend **must be running** for any data to load. Without it, pages show "无法连接到服务器" error.

In production (Docker), nginx proxies `/api/` to `http://backend:8080`.

## Architecture

```
src/
  main.jsx          — entry point
  App.jsx           — layout, routes, antd ConfigProvider (zhCN locale)
  api/index.js      — Axios client, all API calls
  pages/
    HomePage.jsx          — dashboard with stats
    ReservationPage.jsx   — venue reservation UI (date picker, tabs by sport type)
    MyOrdersPage.jsx      — order list with cancel
  assets/           — empty
```

Routes: `/` (home), `/reserve` (reservation), `/orders` (my orders).

## Gotchas

- **No auth**: User ID is simulated via an input field (default 1001). There is no login system.
- **Sport types**: `BADMINTON`, `BASKETBALL`, `TABLE_TENNIS` — these are API enum values, not arbitrary strings.
- **No `.env` or environment config**: All configuration is in `vite.config.js` and hardcoded values.
- **Docker**: Parent repo `docker-compose.yml` orchestrates the full stack. Frontend container uses nginx.

## Full stack (parent repo)

`Campus-Gym-Pro/` contains:
- `docker-compose.yml` — MySQL 8.0, Redis 7, RabbitMQ 3.12, Spring Boot backend, this frontend
- `pom.xml` — Spring Boot 3.2.5, Java 17, MyBatis-Plus, Redisson
- `sql/init.sql` — database schema
