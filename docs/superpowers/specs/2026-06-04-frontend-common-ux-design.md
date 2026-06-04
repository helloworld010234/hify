# Frontend Common UX Design

## Goal

Improve Hify frontend interaction quality without changing business APIs, adding features, or redesigning the product. The work focuses on common components used across management pages so small changes improve multiple workflows.

## Product Context

Hify is an internal AI Agent platform for a small team. The frontend is a Vue 3 + Element Plus management console. Users need a quiet, efficient, scannable interface for repeated operations such as managing providers, agents, knowledge bases, MCP servers, and workflows.

## Scope

This pass will only modify common interaction behavior:

1. `HifyTable` list feedback and refresh interactions.
2. `HifyFormDialog` submit feedback and duplicate-submit prevention.
3. Minimal page wiring for Provider and Agent search inputs.

This pass will not redesign the visual system, add authentication, change backend APIs, change route structure, or introduce new dependencies.

## HifyTable Interaction Design

`HifyTable` should make list state obvious:

- Show table rows while data exists.
- Show a single empty state when the loaded list is empty.
- Show an error state with a retry button when the list request fails.
- Keep the existing exposed `refresh()` method so current pages continue to work.
- Add a lightweight toolbar interaction hook so parent pages can refresh when users press Enter or clear search input.

The table should continue to use existing design tokens, Element Plus components, and current pagination behavior.

## HifyFormDialog Interaction Design

`HifyFormDialog` should make submission state explicit:

- When submit starts, the primary button enters loading state.
- While submitting, cancel and close actions are disabled.
- Duplicate submit clicks are ignored.
- On submit failure, loading state clears and the dialog remains open.
- On submit success, parent pages keep responsibility for closing the dialog.

This preserves existing page ownership of create/update logic while making the common dialog safer.

## Provider and Agent Page Wiring

Provider and Agent pages already use `HifyTable` and search inputs. This pass will wire search inputs so:

- Pressing Enter refreshes the table.
- Clearing the input refreshes the table.

No search debounce or auto-search is added in this pass.

## Testing

Run:

```powershell
npm run build
```

Manual checks after build:

- Provider and Agent search inputs refresh on Enter.
- Clearing those search inputs refreshes the list.
- Form submit buttons show loading and prevent duplicate submit while awaiting the parent handler.
- Empty and error states do not render as duplicate table plus empty placeholders.

## Risks

The main risk is accidentally changing existing slot behavior in `HifyTable` or submit ownership in `HifyFormDialog`. To reduce this, changes should preserve existing props, emits, exposed methods, and slot names.
