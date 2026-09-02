# Local Setup & Troubleshooting

Notes from getting the backend running locally from IntelliJ on 2026-09-01.
Three separate problems were stacked on top of each other; each is written up
below with the symptom, the real cause, and the fix.

---

## 1. Build fails in IntelliJ but `mvn` works

**Symptom** — Clicking the green play button on `JewelleryApplication.main()`
fails during *Build*, before the app starts:

```
java: java.lang.ExceptionInInitializerError
  com.sun.tools.javac.code.TypeTag :: UNKNOWN
```

**Cause** — A JDK mismatch between the IDE and Maven, not a code problem.

- The project SDK was named `homebrew-23`, but that entry points at
  `/opt/homebrew/opt/java/libexec/openjdk.jdk` which is **JDK 26.0.1**.
  The name is misleading; both `homebrew-23` and `homebrew-26` resolve to the
  same JDK 26 install.
- The project resolves **Lombok 1.18.34** (managed by the Spring Boot 3.3.5
  parent — confirm with `mvn dependency:tree -Dincludes=org.projectlombok`).
  That version predates JDK 23+ support, so its annotation processor dies in a
  static initializer reading a `TypeTag` enum constant that no longer exists.
- Maven worked because it runs on a *different* JDK — check `mvn -v`; it was
  JDK 21 (`/Library/Java/JavaVirtualMachines/jdk-21.jdk`), which Lombok 1.18.34
  handles fine. Same code, two different compilers.

**Fix** — Point the IDE at JDK 17, matching `<java.version>17</java.version>`
in `pom.xml`:

- File → Project Structure → Project → SDK: **17**, language level 17.
- Settings → Build, Execution, Deployment → Build Tools → Maven → Importing →
  JDK for importer: **17**.

This is stored in `.idea/misc.xml` as
`project-jdk-name="17" languageLevel="JDK_17"`. The run configuration in
`.idea/runConfigurations/JewelleryApplication.xml` also pins
`ALTERNATIVE_JRE_PATH=17` so the launch can't drift back to a Homebrew JDK.

**Rule of thumb** — when the IDE build fails but the Maven CLI build succeeds,
compare the SDK's actual *homePath* against `mvn -v`. Never trust the SDK name.
Registered JDKs live in
`~/Library/Application Support/JetBrains/<version>/options/jdk.table.xml`.

---

## 2. `Port 8081 was already in use`

**Symptom** — App starts, migrations run, then:

```
Web server failed to start. Port 8081 was already in use.
```

Everything before the web server (Postgres, Flyway, JPA, security) succeeds —
that's the tell that it's a leftover process, not a config error.

**Fix** — look first, then kill:

```bash
lsof -nP -iTCP:8081 -sTCP:LISTEN
lsof -ti tcp:8081 -sTCP:LISTEN | xargs kill
```

Add `-9` only if it refuses to die. The port comes from
`server.port` in `application-local.yml` (`${SERVER_PORT:8081}`).

---

## 3. No admin user / cannot log in to the admin portal

**Symptom** — Backend starts cleanly, but the portal rejects `admin` with
"The username or password is incorrect", and the log says:

```
No admin user exists and jewellery.bootstrap.admin-password is not set.
Set it once to create the initial super administrator.
```

**Cause** — `AdminBootstrapper` deliberately refuses to create the first user
without a password (credentials are never committed to migrations). It:

- returns silently if the username already exists,
- logs that warning and does nothing if the password is blank,
- otherwise creates a `SUPER_ADMIN` with `mustChangePassword = true`.

**The trap that cost the most time here:** the green gutter arrow next to
`main()` creates its **own temporary run configuration**
(`SpringBootApplicationConfigurationType`, `temporary="true"`, stored in
`.idea/workspace.xml`). It is *not* the saved config in
`.idea/runConfigurations/`, so VM parameters added there are silently ignored.

Verify what the running process actually received:

```bash
ps -o args= -p $(lsof -ti tcp:8081 -sTCP:LISTEN) | tr ' ' '\n' | grep '^-D'
```

If the only entries are `-Dspring.output.ansi.enabled` and `-Dfile.encoding`,
the IDE is using the temporary config and your settings never applied.

**Fix** — put bootstrap settings in `application-local.yml` rather than in a
run configuration, so they apply no matter how the app is launched. The `local`
profile is the default (`spring.profiles.active: local` in `application.yml`):

```yaml
jewellery:
  bootstrap:
    admin-username: ${ADMIN_BOOTSTRAP_USERNAME:admin}
    admin-password: ${ADMIN_BOOTSTRAP_PASSWORD:Admin@2026}
```

Restart and look for `Created initial super administrator 'admin'`.

> **Before prod:** `application-local.yml` is tracked in git and holds two dev
> defaults — this bootstrap password and
> `${JWT_SECRET:local-development-only-...}`. Clear both together. Either can
> be overridden by environment variable without editing the file.

---

## Changing the admin password

The account is created with `mustChangePassword = true`. There is an Angular
admin portal at `../erp-admin-portal` — Settings → Profile
(`src/app/features/settings/profile.component.ts`) hits the change-password
endpoint. Admins reset *other* users from the Users screen.

The underlying API (browsable at http://localhost:8081/swagger-ui.html, which
is public via `jewellery.security.public-paths`):

| Endpoint | Purpose | Auth |
| --- | --- | --- |
| `POST /api/v1/auth/login` | obtain access + refresh tokens | public |
| `POST /api/v1/auth/change-password` | change **your own** password | logged-in user |
| `POST /api/v1/users/{id}/reset-password` | reset **someone else's** password | `USER_MANAGE` |

`changeOwnPassword` verifies the current password, rejects a new password
identical to the old one, clears `mustChangePassword`, stamps
`passwordChangedAt`, **revokes all refresh tokens** (so every session is logged
out), and writes a `PASSWORD_CHANGED` audit record.

`newPassword` is constrained to `@Size(min = 10, max = 100)` — anything shorter
is rejected as a validation error.

---

## Useful checks

Docker services (Postgres on **5433**, not 5432; Redis 6379; MinIO 9000/9001):

```bash
docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}'
```

Tables live in per-module schemas (`identity`, `sales`, `inventory`, …), not
`public` — so `select … from users` fails; the users table is
`identity.app_user`:

```bash
docker exec erp-backend-postgres-1 psql -U jewellery -d jewellery \
  -c "select username, status, must_change_password from identity.app_user;"
```

Health check:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/actuator/health
```

---

## Open item

Startup logs `Using generated security password: …` and
`Global AuthenticationManager configured with … inMemoryUserDetailsManager`.
That means Spring Security's default auto-config is still active alongside the
JWT setup — the custom `SecurityFilterChain` may not be fully taking over.
Login works, so it isn't blocking, but it's worth investigating.
