# Core Path UX Design

## Goal

Make the first internal MVP path easy for colleagues to complete: configure a Provider, create an Agent, and start a Chat. This round focuses on clarity, feedback, and stable SSE display, without expanding backend features or touching Knowledge/RAG, Workflow, or MCP deep flows.

## Scope

- Fix visible mojibake copy in global navigation, Provider, Agent, and Chat core screens.
- Improve Provider configuration feedback, especially connection testing and next-step guidance.
- Improve Agent creation feedback, especially when no model is available.
- Improve Chat entry and streaming display stability for SSE generation.
- Keep all existing API contracts unchanged unless a tiny frontend type adjustment is required.

Out of scope:

- Authentication, RBAC, Knowledge/RAG upload flow, Workflow builder, MCP debug depth, backend protocol changes, database migrations, and new onboarding wizard pages.

## User Path

The intended colleague path is:

1. Open Hify and go to Model Providers.
2. Add or edit a Provider.
3. Test connection and understand the result.
4. Create an Agent using an available model.
5. Open Chat and send a message.
6. Observe stable streaming output, with clear stop/error/done states.

## UX Requirements

### Global Navigation

- Navigation labels must be readable Chinese.
- The sidebar should continue to use the existing layout and icons.
- The default entry can remain `/providers`, but the page should make the next step obvious after a Provider is ready.

### Provider Page

- Page title, button text, table columns, placeholders, dialog labels, validation messages, and action labels must be readable Chinese.
- Connection testing should show a loading state per row or action, not only a global message.
- Test success should confirm latency/model count when available and suggest creating an Agent.
- Test failure should show a clear message that helps the colleague check API Key, Base URL, or network.
- Provider creation/update should keep the existing dialog and submit flow.

### Agent Page

- Page title, button text, table columns, placeholders, dialog labels, validation messages, and action labels must be readable Chinese.
- If model metadata cannot be loaded, show a visible warning instead of silently failing.
- If no model is available when creating an Agent, guide the colleague to configure/test a Provider first.
- Agent creation success should suggest going to Chat.
- Inline quick edits for temperature, context turns, and tools should keep their existing behavior.

### Chat Page And SSE Display

- Existing Chat improvements should be preserved: session status, stop generation, stopped/error/done states, and empty state.
- SSE display must stay visually stable:
  - assistant bubble width and input area should not jump during streaming,
  - loading dots should transition cleanly into text,
  - stop should leave partial content visible when content exists,
  - failure should render inside the assistant bubble and clear sending state.
- Chat copy and placeholder text must be readable Chinese.
- Do not add prompt templates or chat history in this round.

## Code Shape

Prefer focused frontend edits:

- `hify-web/src/App.vue`
- `hify-web/src/views/provider/index.vue`
- `hify-web/src/views/agent/index.vue`
- `hify-web/src/views/chat/index.vue`

Optional tiny edits:

- `hify-web/src/api/provider.ts` or `hify-web/src/api/agent.ts` only if type information is needed for better feedback.
- Shared components only if a bug blocks the core path.

## Error Handling

- Reuse the existing request interceptor for normal API failures.
- Add local page-level warnings only when the current code currently swallows errors.
- Avoid duplicate error toasts for the same failure.
- Keep destructive actions protected by existing confirmation helpers.

## Verification

- Run `npm run build` in `hify-web`.
- Manually inspect the Provider, Agent, and Chat routes if a local frontend server is practical.
- Confirm no backend changes are required.
- Confirm the worktree contains only intended frontend implementation files before committing.
