# Running the app against Cloud SQL — live demo runbook

> ⚠️ **Local file, not committed** (git-excluded — it lists the DB credential). Keep it with `run-cloud.ps1`.
> Use this to launch the ticketing-system against the **remote Cloud SQL** database on any machine (e.g. the TA's PC).

---

## TL;DR (machine already set up)
```powershell
cd <repo>\ticketing-system
git checkout develop ; git pull          # the venue-designer "Validate" fix lives on develop
.\run-cloud.ps1                          # sets JDK 21 + DB_* env vars, runs on :8081
```
Then open **http://localhost:8081**, log in as `owner` / `owner123`, create an event → Venue designer → **Validate** → restart to show data persisted.

---

## Connection parameters (what the app needs)
These are set by `run-cloud.ps1`; edit them there if anything changes.

| Param (env var) | Value | Notes |
|---|---|---|
| `TICKETING_PERSISTENCE` | `jpa` | **the switch** — without it, the app uses in-memory H2, *not* Cloud SQL |
| `DB_URL` | `jdbc:postgresql://34.66.18.105:5432/ticketing` | **IP can change** if the instance is recreated — see below |
| `DB_USERNAME` | `ticketing` | |
| `DB_PASSWORD` | `DorOrHannah25` | secret — only in this file / `run-cloud.ps1`, never in git |
| `DB_DRIVER` | `org.postgresql.Driver` | |
| `DB_DIALECT` | `org.hibernate.dialect.PostgreSQLDialect` | |
| `DB_DDL_AUTO` | `update` | reconciles schema additively; safe across `develop` changes |
| `JAVA_HOME` | a `jdk-21*` path | project targets **Java 21** (system default 17 won't compile) |

**GCP coordinates** (for the `gcloud` steps): project `ticketing-system-499511`, instance `ticketing-db`, region `us-central1`, billing budget set (50/90/100% alerts on the $50 credit).

If the public IP ever changes, re-read it and update `$DB_HOST` in `run-cloud.ps1`:
```bash
gcloud sql instances describe ticketing-db --format='value(ipAddresses[0].ipAddress)'
```

---

## First-time setup on a NEW machine
1. **Install prerequisites** (PowerShell, accept UAC prompts):
   ```powershell
   winget install --id EclipseAdoptium.Temurin.21.JDK -e   # Java 21 (required)
   winget install --id Apache.Maven -e                      # if mvn missing
   winget install --id Git.Git -e                           # if git missing
   ```
2. **Get the repo** and switch to the fixed branch:
   ```powershell
   git clone https://github.com/Sadna-2026/ticketing-system.git
   cd ticketing-system ; git checkout develop
   ```
3. **Copy `run-cloud.ps1`** into the repo root (it is git-excluded, so it won't be in the clone).
4. Do the **two Cloud-side steps below**, then `.\run-cloud.ps1`.

---

## ⚠️ The two things that break on a different machine
### 1. Authorize THIS machine's public IP
Cloud SQL only accepts connections from allow-listed IPs. A new machine = new IP → connection will **time out** until you add it.
```powershell
irm https://ifconfig.me/ip          # this machine's public IP
```
Then (in **Cloud Shell** at <https://console.cloud.google.com>, or local `gcloud`):
```bash
gcloud sql instances patch ticketing-db --authorized-networks=<THAT_IP>/32
```
> `--authorized-networks` **replaces** the whole list. To keep several machines, pass them together: `ip1/32,ip2/32`.
> Live-demo shortcut if you can't get the IP ahead of time: add it on the spot, or temporarily widen the range and tighten it afterwards.

### 2. Make sure the instance is running
If it was stopped to save credit:
```bash
gcloud sql instances patch ticketing-db --activation-policy=ALWAYS   # start
# (after the demo)  --activation-policy=NEVER                         # stop
```

---

## Troubleshooting (symptom → cause → fix)
| Symptom in console | Cause | Fix |
|---|---|---|
| `invalid target release: 21` | Building with JDK 17 | Use JDK 21 — `run-cloud.ps1` sets `JAVA_HOME`; verify `java -version` says 21 |
| `Connection timed out` / `HikariPool ... Connection is not available` | IP not authorized, **or** instance stopped | Authorize this machine's IP; start the instance (both above) |
| `FATAL: password authentication failed for user "ticketing"` | Wrong `DB_PASSWORD` | Fix it in `run-cloud.ps1`; or reset: `gcloud sql users set-password ticketing --instance=ticketing-db --password=...` |
| `Web server failed to start. Port 8080 was already in use` | Port taken (e.g. Docker) | `.\run-cloud.ps1 -Port 8082` |
| `LazyInitializationException ... venueLayout.cells` on **Validate** | Running an old branch without the fix | `git checkout develop && git pull` |
| `Schema-validation: missing table/column` | Schema drift vs entities | Keep `DB_DDL_AUTO=update` (not `validate`) |
| Push/`gh` "permission denied" (only if pushing code) | Wrong GitHub account | Not needed to run; pushing requires the `harelna` account |

Useful while debugging: temporarily set `$env:DB_SHOW_SQL="true"` in `run-cloud.ps1` to see every SQL statement, and watch for `Started TicketingApplication in N seconds` = healthy boot.

---

## Live-demo flow tips
- **Warm up before the TA arrives:** start the instance, authorize the IP, run `.\run-cloud.ps1` once and load `http://localhost:8081` — the *first* boot is slow (Vaadin compiles its frontend). Leave it running.
- **Prove it's the cloud, not memory:** show a `SELECT * FROM members;` / `SELECT * FROM events;` in **Cloud SQL Studio** (Console → Cloud SQL → `ticketing-db` → Studio), then create something in the UI and re-run the query — rows appear remotely.
- **Prove persistence:** create an event, `Ctrl+C` the app, relaunch, show the event is still there (came back from Cloud SQL).
- **The headline fix:** owner login → event → Venue designer → add zone + Build grid → **Validate** succeeds (this used to crash).
- **Fallback if the network/instance misbehaves:** plain `mvn spring-boot:run` (no env vars) boots instantly on in-memory H2 — you can still demo features, just not the remote-DB requirement.
- **After the demo:** stop the instance (`--activation-policy=NEVER`) to preserve the $50 credit.
