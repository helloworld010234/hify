# RAG Core Path UX Design

## Goal

Make the internal MVP RAG path usable from end to end:

1. Create or select a knowledge base.
2. Upload a document and understand its processing status.
3. Bind that knowledge base to an Agent.
4. Use the Agent in Chat with the bound knowledge base.

This phase optimizes the existing core path. It does not add multi-knowledge retrieval, retrieval tuning, document reprocessing, or advanced RAG settings.

## Current Findings

- Chat currently reads `agent.knowledgeBaseId` when building the RAG prompt.
- Agent create and update requests already support `knowledgeBaseId`.
- The frontend Agent API detail type exposes `knowledgeIds`, but the current Chat path does not consume relation-table multi-bindings.
- The Agent page currently exposes model and tool binding, but not the knowledge base binding that Chat actually uses.
- The knowledge document page already supports upload, polling, status display, chunks, and delete, but the next step after a successful upload is not clear enough for an internal user.

## Recommended Approach

Use single knowledge base binding for this phase.

This aligns the frontend with the backend field that is already effective in Chat, keeps the implementation small, and avoids giving users a multi-select configuration that may not affect RAG answers.

## Scope

### Agent Configuration

- Add a knowledge base selector to Agent create and edit.
- Save and update the effective `knowledgeBaseId` field.
- Load available knowledge bases using the existing knowledge base list API.
- Show an empty or disabled state when no knowledge base is available.
- Keep knowledge binding optional so Agents without RAG remain valid.

### Agent List

- Add clear knowledge binding visibility in the Agent list.
- Prefer a compact status such as "已绑定" or "未绑定" for the current MVP.
- Avoid implying that multiple knowledge bases are active in Chat.

### Knowledge Documents

- Improve upload and parsing feedback so users can see the current document state.
- After a successful upload, make it clear that the document is parsing before it can be used.
- When parsing succeeds, guide the user toward binding the knowledge base to an Agent.
- Preserve failure visibility with the backend error message where available.

## Data Flow

1. Agent form loads model groups, tools, and knowledge bases.
2. User selects one optional knowledge base.
3. Agent create or update sends `knowledgeBaseId`.
4. Chat loads the Agent detail.
5. Chat RAG uses `agent.knowledgeBaseId` to retrieve chunks and append them to the system prompt.

## Error Handling

- If knowledge base metadata fails to load, show a warning without blocking model-only Agent creation.
- If no knowledge base exists, show a concise empty state and keep the selector optional.
- If upload succeeds but parsing is still pending or processing, keep the row visible and polling.
- If parsing fails, keep the row visible and surface the failure reason.

## Verification

- Frontend build succeeds.
- Agent create and edit can save `knowledgeBaseId`.
- Editing an Agent correctly restores the selected knowledge base.
- Agent list clearly distinguishes bound and unbound Agents.
- Knowledge document upload still validates type and size, refreshes the list, and polls status.
- Routes for providers, agents, knowledge bases, knowledge documents, and chat still render.
