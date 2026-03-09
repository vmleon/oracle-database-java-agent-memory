# TODO: Add Memory Observability Logs

**Date:** 2026-03-09
**Status:** Open
**Priority:** Medium — Improves developer experience and demo-ability

## Goal

Add structured logging to `src/chatserver/` so that when interacting via the Streamlit frontend (`src/web/`), the server logs clearly show **which memory mechanism** was activated for each request: episodic, semantic, procedural, or a combination.

## Current State

| Memory Type     | Mechanism                                             | Current Logging                                                                                |
| --------------- | ----------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Episodic        | `MessageChatMemoryAdvisor` (loads/saves chat history) | None from our code. Spring AI DEBUG logs exist but are not labeled.                            |
| Semantic        | `QuestionAnswerAdvisor` (vector similarity search)    | `OracleVectorStore` logs the SQL query at DEBUG, but no log of matched documents or relevance. |
| Procedural      | `AgentTools` (`@Tool` methods)                        | Already logs `"Procedural memory: ..."` on each tool call.                                     |
| Request context | `AgentController.chat()`                              | Only logs errors. No log of incoming message, conversation ID, or response.                    |

## What to Add

### 1. Request/response logging in `AgentController.chat()`

**File:** `src/chatserver/src/main/java/dev/victormartin/agentmemory/chatserver/controller/AgentController.java`

Log the incoming message and conversation ID at the start of each request, and a summary when the response is ready. This creates a "bracket" in the logs that frames which memory mechanisms fired in between.

```
INFO  AgentController : [conv:abc-123] Incoming: "What are my orders?"
...memory mechanism logs appear here...
INFO  AgentController : [conv:abc-123] Response: 200 OK (342 chars)
```

### 2. Episodic memory logging

**File:** `AgentController.java` (at construction or via a custom advisor wrapper)

The `MessageChatMemoryAdvisor` loads previous messages from `SPRING_AI_CHAT_MEMORY` before each call and saves the new exchange after. Currently invisible in our logs.

Options (simplest first):

- **Option A:** Add targeted Spring AI log levels in `application-local.yaml` for the advisor and JDBC repository packages so their internal DEBUG logs surface.
- **Option B:** Create a thin logging wrapper advisor that delegates to `MessageChatMemoryAdvisor` and logs message count before/after.

### 3. Semantic memory logging

**File:** `AgentController.java` (via advisor wrapper or logging config)

The `QuestionAnswerAdvisor` runs a vector similarity search and injects matching documents into the prompt. Currently, only the raw SQL is logged.

Options:

- **Option A:** Add targeted log levels for the `QuestionAnswerAdvisor` package to surface its internal document-match logs.
- **Option B:** Create a thin wrapper advisor that logs the number of matched documents and their similarity scores.

### 4. Log format consistency

Use a consistent prefix pattern across all memory types so logs can be filtered easily:

```
INFO  AgentController : [conv:abc-123] Incoming: "What is my return policy?"
DEBUG MemoryLog       : [conv:abc-123] [EPISODIC] Loaded 12 previous messages
DEBUG MemoryLog       : [conv:abc-123] [SEMANTIC] Vector search returned 3 documents (best similarity: 0.92)
INFO  AgentController : [conv:abc-123] Response: 200 OK (587 chars)
```

When procedural memory is involved (once the tools blocker is resolved):

```
INFO  AgentController : [conv:abc-123] Incoming: "What are my orders?"
DEBUG MemoryLog       : [conv:abc-123] [EPISODIC] Loaded 4 previous messages
DEBUG MemoryLog       : [conv:abc-123] [SEMANTIC] Vector search returned 0 documents
INFO  AgentTools      : [conv:abc-123] [PROCEDURAL] listOrders called
INFO  AgentController : [conv:abc-123] Response: 200 OK (892 chars)
```

## Files to Modify

- `src/chatserver/src/main/java/dev/victormartin/agentmemory/chatserver/controller/AgentController.java` — request/response logs + advisor wrappers
- `src/chatserver/src/main/resources/application-local.yaml` — add targeted log levels for Spring AI advisor packages
- `src/chatserver/src/main/java/dev/victormartin/agentmemory/chatserver/tools/AgentTools.java` — minor: add conversation ID to existing procedural memory logs (if possible via context)

## Verification

1. Start the server with `./gradlew bootRun --args='--spring.profiles.active=local'`
2. Open Streamlit UI at `http://localhost:8501`
3. Send a message like "Hello, I am Victor" — logs should show `[EPISODIC]` (new conversation, 0 messages loaded) and `[SEMANTIC]` (vector search, likely low relevance)
4. Send "What is the return policy?" — logs should show `[EPISODIC]` (1 previous message) and `[SEMANTIC]` (vector search, high similarity match on return policy doc)
5. Send a follow-up like "What is my name?" — logs should show `[EPISODIC]` (3 previous messages, name recalled from history) and `[SEMANTIC]` (low relevance)
6. Verify each log line correlates to the conversation visible in the Streamlit UI

## Dependencies

- None for episodic + semantic logging (works now)
- Procedural memory logging depends on resolving [spring-ai-oci-genai-tools-blocker](../issues/spring-ai-oci-genai-tools-blocker.md)
