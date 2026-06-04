# Quality Baseline Fixes Design

## Goal

Improve Hify code quality without adding features. The first pass focuses on high-impact, low-effort fixes found during the project evaluation: broken verification commands and unsafe development defaults.

## Scope

This change will only address three areas:

1. Restore the backend test baseline by fixing the `ChatServiceImplTest` setup so `HifyMetrics` is provided as a mock dependency.
2. Restore the frontend build baseline by fixing `hify-web/src/api/knowledge.ts` so its direct Axios upload call is typed and imported correctly.
3. Reduce hardcoded credential exposure in `hify-app/src/main/resources/application.yml` while keeping local startup defaults.

This change will not add authentication, change API behavior, refactor module boundaries, introduce database migration tooling, or replace the encryption algorithm.

## Backend Test Fix

`ChatServiceImpl` now depends on `HifyMetrics`. The unit test uses Mockito `@InjectMocks` but does not provide a `HifyMetrics` mock, so error and completion paths can throw `NullPointerException` during `mvn test -q`.

The fix is to add a `@Mock HifyMetrics metrics` field to the test. No production behavior changes.

## Frontend Build Fix

`hify-web/src/api/knowledge.ts` calls `axios.post` directly for multipart upload, but the file does not import Axios and leaves the response callback implicitly typed.

The fix is to import Axios types explicitly and type the callback response. No endpoint paths or UI behavior change.

## Configuration Safety Fix

The main application config currently contains local credentials and a public pgvector host. These will be changed to Spring placeholder expressions with local defaults:

- MySQL user defaults to `root`.
- MySQL password defaults to `root`.
- Redis password defaults to `123456`.
- pgvector URL defaults to `jdbc:postgresql://localhost:5432/hify`.
- pgvector username defaults to `hify`.
- pgvector password defaults to `123456`.

This keeps local development convenient while allowing deployed environments to inject real values through environment variables.

## Verification

After implementation, run:

```powershell
mvn test -q
npm run build
```

The security audit shell script is not part of this pass because the current Windows environment cannot execute WSL `/bin/bash`. That remains a separate tooling portability issue.

## Risks

The backend and frontend fixes are low risk because they only restore intended test/build behavior. The config change may require users who relied on the old public pgvector default to set `PGVECTOR_DATASOURCE_URL`, but the new default is safer and still locally runnable.
