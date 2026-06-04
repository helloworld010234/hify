# Chat Experience Design

## Goal

Improve the existing Chat page's sending experience without adding new product features. This round focuses on control, state feedback, and error clarity during the current single-session streaming flow.

## Scope

- Keep the existing `/chat` route, single active session model, and SSE API contract.
- Reuse the current `AbortController` path for stopping generation.
- Improve user-visible states for session creation, ready, failed, sending, stopped, completed, and stream errors.
- Add a light empty state for a ready chat with no messages.
- Keep the input concurrency model conservative: one active send at a time.

Out of scope:

- Chat history list, session switching UI, prompt suggestions, agent selection, backend protocol changes, persistence changes, and new markdown features.

## UX Behavior

### Session State

The header should make the chat state obvious:

- Creating: show that the session is being prepared and keep sending disabled.
- Ready: show the current session id.
- Failed: show a failed state and expose a retry action to create the session again.

### Sending Control

When a message is streaming:

- The primary send action changes into a stop action.
- Clicking stop aborts the current stream request through the existing abort controller.
- A stopped assistant message is marked as stopped instead of error.
- Stopping should not show a red global error toast.

### Message Feedback

Assistant messages should distinguish:

- Loading with no content yet.
- Streaming content.
- Completed content.
- Stopped partial content.
- Error content with a concise error label.

User messages remain plain text. Assistant markdown rendering continues to use the existing marked plus DOMPurify path.

### Empty State

When the session is ready and there are no messages, show a compact empty state in the message area. It should confirm readiness and avoid adding example prompts or extra actions.

### Scrolling

Keep automatic scrolling for the normal send path. If there is a small opportunity while implementing, avoid forcing scroll when the user has clearly moved away from the bottom, but this is secondary to send control and state feedback.

## Code Shape

Prefer a focused edit in `hify-web/src/views/chat/index.vue`:

- Add explicit session status state.
- Add explicit message status metadata instead of relying only on `loading` and `isError`.
- Extract small helpers only when they reduce duplicated state cleanup.
- Keep `hify-web/src/api/chat.ts` unchanged unless a tiny type or abort-handling adjustment is needed.

## Error Handling

- Session creation failure should set a visible failed state and keep send disabled.
- Stream API `error` events should update the active assistant bubble and clear sending state.
- Fetch errors should update the active assistant bubble and clear sending state.
- Abort errors caused by the user's stop action should become a stopped state, not a failure state.

## Verification

- Run `npm run build` in `hify-web`.
- Manually inspect the Chat page if the build passes and a local frontend server is practical.
- Confirm no backend changes are required.
