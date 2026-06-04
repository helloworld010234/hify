# Quality Baseline Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore Hify's backend/frontend verification baseline and reduce unsafe hardcoded application defaults without adding features.

**Architecture:** This is a focused quality pass. It changes one backend unit test dependency setup, one frontend API helper import/type issue, and one Spring configuration file. Production API behavior and module structure stay unchanged.

**Tech Stack:** Spring Boot 3.3, JUnit 5, Mockito, Vue 3, TypeScript, Axios, Vite, Maven.

---

## File Structure

- Modify `hify-chat/src/test/java/com/hify/modules/chat/service/impl/ChatServiceImplTest.java`
  - Responsibility: unit-test setup for `ChatServiceImpl`.
  - Add a Mockito mock for the existing `HifyMetrics` dependency so `@InjectMocks` can construct the service fully.

- Modify `hify-web/src/api/knowledge.ts`
  - Responsibility: knowledge base and document API helpers.
  - Import Axios for the existing multipart upload call and type its response callback.

- Modify `hify-app/src/main/resources/application.yml`
  - Responsibility: default application configuration.
  - Replace direct credential/public host values with Spring environment placeholders that keep local defaults.

---

### Task 1: Backend Unit Test Dependency Setup

**Files:**
- Modify: `hify-chat/src/test/java/com/hify/modules/chat/service/impl/ChatServiceImplTest.java`
- Test: `hify-chat/src/test/java/com/hify/modules/chat/service/impl/ChatServiceImplTest.java`

- [ ] **Step 1: Confirm the failing backend test**

Run:

```powershell
mvn test -q -pl hify-chat
```

Expected before the fix: the build fails with `NullPointerException` because `this.metrics` is null in `ChatServiceImpl`.

- [ ] **Step 2: Add the missing metrics mock**

In `hify-chat/src/test/java/com/hify/modules/chat/service/impl/ChatServiceImplTest.java`, add this import near the other `com.hify.common` imports:

```java
import com.hify.common.metrics.HifyMetrics;
```

Add this mock field after the existing `ThreadPoolExecutor llmStreamExecutor` mock:

```java
    @Mock
    private HifyMetrics metrics;
```

- [ ] **Step 3: Run the focused backend test module**

Run:

```powershell
mvn test -q -pl hify-chat
```

Expected after the fix: `hify-chat` tests pass.

---

### Task 2: Frontend Knowledge Upload Build Fix

**Files:**
- Modify: `hify-web/src/api/knowledge.ts`
- Test: frontend TypeScript build through `npm run build`

- [ ] **Step 1: Confirm the frontend build failure**

Run:

```powershell
npm run build
```

from `hify-web`.

Expected before the fix:

```text
src/api/knowledge.ts(83,10): error TS2304: Cannot find name 'axios'.
src/api/knowledge.ts(85,11): error TS7006: Parameter 'res' implicitly has an 'any' type.
```

- [ ] **Step 2: Import Axios and type the upload response**

In `hify-web/src/api/knowledge.ts`, change the first import from:

```ts
import { get, post, del } from '@/utils/request'
```

to:

```ts
import axios, { type AxiosResponse } from 'axios'
import { get, post, del } from '@/utils/request'
```

Change the upload callback from:

```ts
  }).then(res => res.data)
```

to:

```ts
  }).then((res: AxiosResponse) => res.data)
```

- [ ] **Step 3: Run the frontend build**

Run:

```powershell
npm run build
```

from `hify-web`.

Expected after the fix: `vue-tsc -b && vite build` completes successfully.

---

### Task 3: Safer Local-Default Application Configuration

**Files:**
- Modify: `hify-app/src/main/resources/application.yml`
- Test: inspect config values and run backend tests.

- [ ] **Step 1: Replace hardcoded MySQL defaults with placeholders**

In `hify-app/src/main/resources/application.yml`, replace:

```yaml
    username: root
    password: root
```

with:

```yaml
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
```

- [ ] **Step 2: Replace hardcoded Redis password with a placeholder**

Replace:

```yaml
      password: 123456
```

with:

```yaml
      password: ${REDIS_PASSWORD:123456}
```

- [ ] **Step 3: Replace public pgvector connection defaults with local placeholders**

Replace:

```yaml
    url: jdbc:postgresql://8.136.34.168:5432/myhify
    username: myhify
    password: 123456
```

with:

```yaml
    url: ${PGVECTOR_DATASOURCE_URL:jdbc:postgresql://localhost:5432/hify}
    username: ${PGVECTOR_DATASOURCE_USERNAME:hify}
    password: ${PGVECTOR_DATASOURCE_PASSWORD:123456}
```

- [ ] **Step 4: Verify no public pgvector host remains in application config**

Run:

```powershell
Select-String -LiteralPath hify-app\src\main\resources\application.yml -Pattern '8.136.34.168','myhify'
```

Expected after the fix: no output.

---

### Task 4: Full Verification

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run backend tests**

Run:

```powershell
mvn test -q
```

Expected: Maven test phase passes.

- [ ] **Step 2: Run frontend build**

Run:

```powershell
npm run build
```

from `hify-web`.

Expected: TypeScript and Vite build pass.

- [ ] **Step 3: Check working tree**

Run:

```powershell
git status --short
```

Expected: only the planned source/config files are modified, plus this plan file if it has not already been committed.

- [ ] **Step 4: Commit implementation**

Run:

```powershell
git add hify-chat/src/test/java/com/hify/modules/chat/service/impl/ChatServiceImplTest.java hify-web/src/api/knowledge.ts hify-app/src/main/resources/application.yml
git commit -m "fix: restore quality baseline checks"
```

Expected: commit succeeds after local hooks pass.

---

## Self-Review

- Spec coverage: Task 1 covers backend test baseline, Task 2 covers frontend build baseline, Task 3 covers safer local-default configuration, Task 4 covers verification.
- Scope check: no authentication, API behavior changes, module refactoring, database migration tooling, or encryption changes are included.
- Placeholder scan: this plan contains no incomplete implementation steps.
