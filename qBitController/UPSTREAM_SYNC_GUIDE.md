# Upstream Sync & Architecture Guide for qBitAct (qBitController + GitHub Actions)

This document provides complete instructions for developers and LLMs to maintain, update, and merge upstream changes from the official `qBitController` repository (`https://github.com/Bartuzen/qBitController`) into **qBitAct**.

---

## 1. Architectural Philosophy & Isolation

**qBitAct** extends the open-source **qBitController** Android/Multiplatform application to support running downloads on **GitHub Actions CI runner workers** while preserving 100% of the native qBittorrent WebUI capabilities.

To ensure frictionless upstream merges:
1. **Isolated Packages**: All GitHub Actions REST APIs, live telemetry clients, and worker models live in:
   - `composeApp/src/commonMain/kotlin/dev/bartuzen/qbitcontroller/model/github/`
   - `composeApp/src/commonMain/kotlin/dev/bartuzen/qbitcontroller/network/github/`
   - `composeApp/src/commonMain/kotlin/dev/bartuzen/qbitcontroller/data/repositories/github/`
2. **Minimal Core Invasions**: Only a few upstream files contain minimal, non-disruptive touchpoints.

---

## 2. Core Upstream Touchpoint Checklist

When merging upstream updates, ensure these specific touchpoints are preserved:

### A. `model/Torrent.kt`
- Keep the 4 optional runner fields with default `null` values at the end of the `Torrent` data class:
  ```kotlin
  @SerialName("worker_run_id") val workerRunId: Long? = null,
  @SerialName("worker_tunnel_url") val workerTunnelUrl: String? = null,
  @SerialName("worker_password") val workerPassword: String? = null,
  @SerialName("worker_stage") val workerStage: String? = null,
  ```
- Keep the implemented `serialize()` methods in `EtaSerializer` and `PieceStateSerializer` (do not throw `UnsupportedOperationException`).

### B. `model/ServerConfig.kt`
- Keep `val gitHubConfig: GitHubConfig? = null` in the `ServerConfig` constructor.
- Keep helper properties:
  - `val isGitHubActionsServer: Boolean get() = gitHubConfig != null && gitHubConfig.isValid`
  - `val visibleUrl` and `displayName` fallback for GitHub profiles.

### C. `network/RequestManager.kt`
- Keep short-circuiting in `request()` and `tryLogin()` to avoid sending direct qBittorrent Web API queries (`/api/v2/app/version` or `/api/v2/auth/login`) against `https://api.github.com`.

### D. Repositories
- **`TorrentListRepository.kt`**: Check `getEffectiveGitHubConfig(serverConfig)` at the top of `getMainData()`, `getPartialMainData()`, `deleteTorrents()`, and `pauseTorrents()`.
- **`AddTorrentRepository.kt`**: Check `ghConfig` at the top of `addTorrent()` to dispatch `workflow_dispatch` on GitHub Actions instead of uploading to local qBittorrent.
- **`TorrentOverviewRepository.kt`**: Delegate `deleteTorrent()` and `pauseTorrent()` to `gitHubWorkerRepository.cancelWorker()`.

### E. Settings UI
- **`ui/settings/addeditserver/AddEditServerScreen.kt`**: Keep the GitHub Actions mode toggle chip and token/owner/repo input fields.
- **`ui/settings/addeditserver/AddEditServerViewModel.kt`**: Keep `gitHubApiClient.validateConnection()` in `testConnection()`.

### F. Dependency Injection (`di/AppModule.kt`)
- Ensure singletons for GitHub components are registered:
  ```kotlin
  singleOf(::GitHubApiClient)
  singleOf(::LiveTelemetryClient)
  singleOf(::QBittorrentTunnelClient)
  singleOf(::GitHubWorkerRepository)
  ```

---

## 3. Step-by-Step Upstream Sync Command Line

Run the following commands inside `qBitController/`:

```bash
# 1. Add upstream remote (if not already added)
git remote add upstream https://github.com/Bartuzen/qBitController.git 2>/dev/null || true

# 2. Fetch latest upstream releases and main branch
git fetch upstream

# 3. Merge upstream changes into your current branch
git merge upstream/main

# 4. Resolve conflicts (if any) following Section 2 checklist above

# 5. Build and install to connected phone
./gradlew :composeApp:installFreeDebug
```

---

## 4. How Version Negotiation Works

- Upstream `qBitController` automatically detects qBittorrent version (`QBittorrentVersion(5, 0, 0)`) via `RequestManager`.
- Each runner running qBittorrent WebUI over an ephemeral tunnel (`dweet.cc` telemetry `tunnel_url`) is queried directly by `QBittorrentTunnelClient`.
- If upstream updates its Web API protocols (e.g. for qBittorrent 5.1+ or 6.x), the runner connections automatically inherit all protocol updates without manual patch work.
