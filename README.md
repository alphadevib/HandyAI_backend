# HandyAI

The marketplace for AI tools. Compare 39 platforms by price across monthly, quarterly and annual
plans, get picks for your profession, ask the AI Chat (typed or spoken), and track every AI
subscription you pay for in one place.

This repository is the backend: `backend/handy-ai`, a Spring Boot 4.1 REST API (Java 17, JPA, H2
locally, MySQL in production). The React 19 + Vite frontend lives in its own repository,
[HandyAI_frontend](https://github.com/alphadevib/HandyAI_frontend).

## Running it

Two terminals. The backend first (from this repo).

```bash
cd backend/handy-ai
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

It starts on <http://localhost:8080> with an in-memory H2 database and seeds 13 categories and
39 tools on first run. Nothing to install or configure.

Then the frontend, from a clone of HandyAI_frontend:

```bash
cd HandyAI_frontend
npm install
npm run dev
```

The app is on <http://localhost:5173>. The dev server proxies `/api` to port 8080, and it listens
on the network too, so a phone on the same Wi-Fi can open `http://<your-ip>:5173` and see the
mobile layout.

Demo account: `demo@handyai.app` / `demo12345` (the sign-in page can fill it in for you). It is
only created locally; the production profile turns it off.

### The admin account

There is exactly one admin, set by `handyai.admin.email` (`HANDYAI_ADMIN_EMAIL`; locally it
defaults to `admin@handyai.local`). On every start the backend creates that account if it is
missing, gives it the configured password, and removes the admin role from anyone else. The
admin's **Admin dashboard** (account menu → `/admin`) shows live users, purchase redirects,
registrations and tracked subscriptions, refreshing every 5 seconds, plus organisation
verification.

The password is never committed. Locally, create `backend/handy-ai/config/application.properties`
(git-ignored) containing:

```properties
handyai.admin.password=your-password
```

In production set the `HANDYAI_ADMIN_PASSWORD` environment variable instead.

### Using MySQL instead

```bash
mysql -u root -p -e "CREATE DATABASE handyai CHARACTER SET utf8mb4;"
cd backend/handy-ai
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

Connection details come from `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_USER` and
`MYSQL_PASSWORD`, all with sensible localhost defaults — see
`src/main/resources/application-mysql.properties`. The seeder is idempotent, so restarting against
a persistent database inserts only what is missing.

### Docker Compose (API + MySQL container)

```bash
cd backend/handy-ai
docker compose up --build
```

Builds the API image from the `Dockerfile` and starts it with its own MySQL 8.4 container. The
API waits until MySQL reports healthy. The API is on <http://localhost:8080> and MySQL on
`localhost:3307`; data is kept in the `mysql-data` volume (`docker compose down -v` removes it).
Set `HANDYAI_ADMIN_PASSWORD` or `HANDYAI_ANTHROPIC_API_KEY` in your shell or a `.env` file next to
`docker-compose.yml` to pass them in.

### Database changes

The schema is managed by Flyway: every change is a new numbered file in
`backend/handy-ai/src/main/resources/db/migration` (`V3__what_changed.sql`, ...), applied once on
startup. Hibernate only validates that the entities match, and refuses to start if they do not.
An existing database without migration history is baselined at `V1` (the schema as it was when
migrations were introduced).

### Tests

```bash
cd backend/handy-ai && ./mvnw test
cd HandyAI_frontend && npm run lint && npm run build
```

## Deploying

Vercel hosts the frontend only; it cannot run the Java backend. Put the backend on a host that
runs Docker (Render, Railway, Fly.io) with a MySQL database, then point the frontend at it.

**1. Backend** — deploy `backend/handy-ai` with its `Dockerfile` (it runs the `prod` profile) and
set these environment variables. The app refuses to start if a required one is missing.

| Variable | Required | Value |
| --- | --- | --- |
| `HANDYAI_JWT_SECRET` | yes | 32+ random characters, e.g. `openssl rand -base64 48` |
| `HANDYAI_ADMIN_PASSWORD` | yes | The admin's password |
| `HANDYAI_CORS_ALLOWED_ORIGINS` | yes | Your Vercel URL(s), comma separated; `*` patterns allowed for preview deployments, e.g. `https://ib11.vercel.app,https://ib11-*-yourteam.vercel.app` |
| `MYSQL_HOST`, `MYSQL_USER`, `MYSQL_PASSWORD` | yes | From your MySQL provider |
| `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_SSL_MODE` | no | Default `3306`, `handyai`, `REQUIRED` |
| `HANDYAI_ADMIN_EMAIL` | yes | The one admin account's email |
| `HANDYAI_USD_TO_INR` | no | Rupee conversion rate, default `88` |
| `BREVO_API_KEY` | no | Turns on email confirmation for new sign-ups (Brevo → SMTP & API → API keys) |
| `MAIL_FROM` | with the key | A sender address verified in Brevo, e.g. `hello@yourdomain.com` |
| `FRONTEND_URL` | no | Where confirmation links point; defaults to `https://ib11.vercel.app` |

Check it with `https://<backend>/api/health`.

**2. Frontend** — import the HandyAI_frontend repo in Vercel (the included
`vercel.json` sets the build and makes page refreshes work on every route) and add the
environment variable `VITE_API_BASE_URL=https://<backend>/api`. Redeploy after changing it: Vite
bakes it in at build time.

**3. Your own domain (e.g. `handyai.tech`)** — in Vercel open Project → Settings → Domains, add
the domain, and create the DNS records Vercel shows at your registrar (usually an `A` record
`76.76.21.21` for the bare domain and a `CNAME` `cname.vercel-dns.com` for `www`). HTTPS is issued
automatically. Then add the new address to `HANDYAI_CORS_ALLOWED_ORIGINS` on the backend, e.g.
`https://handyai.tech,https://www.handyai.tech,https://handyai.vercel.app`. Optionally give the
backend `api.handyai.tech` through your backend host's custom-domain setting and point
`VITE_API_BASE_URL` at it.

### Releasing a new version

1. Bump `"version"` in `package.json` of HandyAI_frontend (e.g. `2.1.0`).
2. Add an entry at the top of `src/releases.js` in HandyAI_frontend with the same version, a title and the
   highlights.
3. Push; Vercel deploys.

Every open tab checks `/version.json` every two minutes and when it is brought back to the front.
When the deployment changes it shows a "HandyAI just got better" bar and refreshes itself after 15
seconds (it waits while someone is typing), or at the next page change. Each visitor sees the
release notes once, during the 48 hours after the deployment. First-time visitors get a welcome
note instead.

### Suggestions

Anyone can send an idea, tool request or bug report from the footer, the welcome note or "What's
new". Only the admin can read them, under Admin dashboard → Suggestions, and move each one to
Planned, Done or Dismissed. One sender may send five every ten minutes.

**What "live" means here.** Live users are browser tabs that sent a heartbeat in the last 75
seconds, kept in the backend's memory, so run a single backend instance (or move presence to
Redis before scaling out). A purchase redirect is a click on Buy / Try it, recorded as HandyAI
hands the visitor to the vendor; the payment itself happens on the vendor's site and is not
reported back without an affiliate programme.

## API

Everything lives under `/api`. Browsing is public; anything tied to a person needs
`Authorization: Bearer <token>` from register or login.

| Method | Path | Auth | What it does |
| --- | --- | --- | --- |
| POST | `/auth/register` | — | Create an account, returns a token |
| POST | `/auth/login` | — | Sign in, returns a token |
| GET | `/auth/me` | yes | The signed-in profile |
| PUT | `/auth/me` | yes | Update name / profession |
| GET | `/tools` | optional | Search with `q`, `category`, `pricing`, `sort`, `page`, `size` |
| GET | `/tools/featured`, `/tools/trending` | optional | Home page rails |
| GET | `/tools/{slug}` | optional | One tool |
| GET | `/tools/{slug}/reviews` | — | Reviews for a tool |
| PUT | `/tools/{slug}/reviews` | yes | Add or replace your review (1–5 plus a comment) |
| DELETE | `/tools/{slug}/reviews/{id}` | yes | Remove your own review |
| POST | `/tools/{slug}/favorite` | yes | Toggle saved, returns the new state |
| GET | `/me/favorites` | yes | Your saved tools |
| GET | `/categories`, `/categories/{slug}` | — | Categories with tool counts |
| POST | `/recommendations` | optional | The matcher: `goal`, `profession`, `categorySlugs`, `pricing`, `limit` |
| GET | `/stats`, `/health` | — | Counters and a liveness check |
| GET | `/tools/price-ranges` | — | Marketplace price bands with counts, per `currency` |
| GET | `/professions` | — | The 60 professions and 25 industries for sign-up |
| POST | `/chat` | optional | One AI Chat turn: `message` plus the `context` from the last reply |
| GET/POST/PUT/DELETE | `/me/subscriptions` | yes | Track, edit and remove your subscriptions |
| POST | `/presence` | optional | Heartbeat behind the live-users count |
| GET | `/go/{slug}` | — | Records a purchase redirect, then 302s to the vendor |
| GET | `/admin/stats` | admin | Live dashboard numbers |
| GET/POST | `/admin/organisations…` | admin | List, approve and reject organisations |
| POST | `/suggestions` | optional | Send a suggestion (5 per sender per 10 minutes) |
| GET/PUT | `/admin/suggestions…` | admin | Read suggestions and set their status |

Signing in only sharpens the results: a token adds your saved flag to every card and nudges
recommendations towards the categories you favourite and rate highly.

Errors always come back in one shape, so the frontend has a single path to handle:

```json
{
  "timestamp": "2026-09-22T09:10:41Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Enter a valid email address",
  "path": "/api/auth/register",
  "fieldErrors": { "email": "Enter a valid email address" }
}
```

## How the backend is put together

```
com.handyai.build
├── config        WebConfig (CORS), CatalogueSeeder
├── controller    Auth, Tool, Category, Favorite, Recommendation, Meta
├── domain        User, Category, AiTool, Review, Favorite, Role, PricingModel
├── dto           Request/response records — entities never leave the service layer
├── exception     Typed failures + GlobalExceptionHandler
├── repository    Spring Data JPA interfaces
├── security      PasswordHasher (PBKDF2), JwtService (HS256), filter, CurrentUserProvider
└── service       AuthService, ToolService, CategoryService, ReviewService,
                  FavoriteService, RecommendationService
```

Deliberate choices that keep concurrent requests out of each other's way:

- **No bidirectional relations.** Every `@ManyToOne` is lazy and nothing owns a collection, so a
  read is one predictable query and JSON can never recurse.
- **`spring.jpa.open-in-view=false`.** The persistence session closes when the service call
  returns, so no request holds a pooled connection while the response is being written.
- **Fixed lock order on writes.** A review is written before the tool row it belongs to, always in
  that order, so two reviews on the same tool queue instead of dead-locking.
- **Ratings are re-derived, not incremented.** After any review write the sum and count come from
  an aggregate query, so interleaved transactions cannot lose an update.
- **Stateless auth.** JWTs mean no server-side session map to synchronise on.
- **Unique constraints backed by a handler.** A duplicate email or double-tapped favourite comes
  back as a clean `409` instead of a `500`.

Passwords are hashed with PBKDF2-HMAC-SHA256 (210k iterations, per-user salt) using the JDK only.
Set `HANDYAI_JWT_SECRET` (32+ characters) in any environment that is not your laptop.
