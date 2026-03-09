# TODO: Replace OCI GenAI with Ollama

**Date:** 2026-03-09
**Status:** Open
**Priority:** High — Unblocks tool calling and improves portability

## Goal

Remove the OCI Generative AI dependency and replace it with Ollama for both LLM chat and embeddings. This resolves the [tools blocker](../issues/spring-ai-oci-genai-tools-blocker.md) and makes the project provider-agnostic — switching from Ollama to OCI GenAI, GCP Vertex AI, Azure OpenAI, or any other provider becomes a dependency + config change.

## Why

1. **Unblocks procedural memory**: Spring AI's OCI GenAI Cohere adapter does not support tool/function calling. Ollama does.
2. **No API keys or cloud credentials needed**: Ollama runs locally, simplifying dev setup.
3. **Provider portability**: Spring AI's `ChatModel` and `EmbeddingModel` abstractions mean the application code (`AgentController`, `AgentTools`, advisors) stays identical across providers. Only the starter dependency and config change.

## What to Change

### 1. `build.gradle` — Swap dependency

Remove:
```gradle
implementation 'org.springframework.ai:spring-ai-starter-model-oci-genai'
```

Add:
```gradle
implementation 'org.springframework.ai:spring-ai-starter-model-ollama'
```

Keep unchanged:
```gradle
implementation 'org.springframework.ai:spring-ai-advisors-vector-store'
implementation 'org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc'
implementation 'org.springframework.ai:spring-ai-starter-vector-store-oracle'
```

### 2. `application.yaml` — Replace OCI config with Ollama config

Remove the entire `spring.ai.oci` section and replace with:
```yaml
spring:
  ai:
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        model: ${OLLAMA_CHAT_MODEL:qwen2.5}
      embedding:
        model: ${OLLAMA_EMBEDDING_MODEL:nomic-embed-text}
```

Keep unchanged: `spring.ai.vectorstore.oracle`, `spring.ai.chat.memory.repository.jdbc`, datasource config.

### 3. `application-local.yaml` — Simplify

Remove all `spring.ai.oci` config (model OCIDs, compartment, auth). Replace with Ollama overrides if needed (or leave empty since defaults point to localhost).

### 4. `application-local.yaml.example` — Update template

Update to reflect Ollama config instead of OCI credentials.

### 5. No Java code changes

`AgentController.java`, `AgentTools.java`, `DataSeeder.java` — zero changes needed. Spring AI auto-configures the `ChatModel` and `EmbeddingModel` beans from the Ollama starter, and the existing `ChatClient.Builder`, `.defaultTools()`, advisors, and `VectorStore` work as-is.

## Ollama Setup

```bash
# Install (macOS)
brew install ollama

# Pull models
ollama pull qwen2.5          # chat model with tool calling support
ollama pull nomic-embed-text  # embedding model

# Ollama serves on http://localhost:11434 by default
```

### Recommended models (tool calling support)

| Model | Size | Tool Calling | Notes |
|---|---|---|---|
| `qwen2.5` | 4.7 GB | Yes | Good balance of quality and speed |
| `qwen2.5:14b` | 9 GB | Yes | Better quality, needs more RAM |
| `llama3.1` | 4.7 GB | Yes | Meta's model, solid tool support |
| `mistral` | 4.1 GB | Yes | Fast, good for POC |

### Embedding models

| Model | Dimensions | Notes |
|---|---|---|
| `nomic-embed-text` | 768 | Good default, small |
| `mxbai-embed-large` | 1024 | Matches current OCI embedding dimensions |

Note: Changing embedding dimensions requires updating `spring.ai.vectorstore.oracle.dimensions` and re-seeding the vector store (drop and recreate `SPRING_AI_VECTORS` table or truncate it).

## Vector Store Dimension Consideration

Current OCI embedding model produces 1024-dimension vectors. The Ollama embedding model dimensions will likely differ. When switching:

1. Update `spring.ai.vectorstore.oracle.dimensions` to match the new model
2. Clear existing vectors: the `SPRING_AI_VECTORS` table will need to be recreated (easiest: drop the table, Spring AI will recreate it on startup with `initialize-schema: true`)
3. `DataSeeder` will re-seed policy documents on next startup

## Future: Switching to Other Providers

After this migration, switching to another provider is a config-level change:

| Provider | Dependency | Config prefix |
|---|---|---|
| Ollama (local) | `spring-ai-starter-model-ollama` | `spring.ai.ollama` |
| OpenAI | `spring-ai-starter-model-openai` | `spring.ai.openai` |
| Anthropic | `spring-ai-starter-model-anthropic` | `spring.ai.anthropic` |
| GCP Vertex AI | `spring-ai-starter-model-vertex-ai` | `spring.ai.vertex.ai` |
| Azure OpenAI | `spring-ai-starter-model-azure-openai` | `spring.ai.azure.openai` |
| OCI GenAI | `spring-ai-starter-model-oci-genai` | `spring.ai.oci.genai` |

No Java code changes needed for any of these — only `build.gradle` dependency and `application.yaml` config.

## Files to Modify

- `src/chatserver/build.gradle` — swap OCI GenAI dependency for Ollama
- `src/chatserver/src/main/resources/application.yaml` — replace `spring.ai.oci` with `spring.ai.ollama`
- `src/chatserver/src/main/resources/application-local.yaml` — remove OCI credentials, add Ollama overrides if needed
- `src/chatserver/src/main/resources/application-local.yaml.example` — update template

## Verification

1. Install Ollama and pull models (`qwen2.5` + `nomic-embed-text`)
2. Start Oracle DB container
3. Start chatserver: `./gradlew bootRun --args='--spring.profiles.active=local'`
4. Verify startup: DataSeeder logs should show orders + policies seeded
5. Test episodic memory: send "Hello, I am Victor", then "What is my name?" — should recall the name
6. Test semantic memory: send "What is the return policy?" — should retrieve policy from vector store
7. Test procedural memory (tools): send "What are my orders?" — should call `listOrders` tool and return order data (this is the key test that was broken with OCI GenAI)
8. Test tool with parameters: send "What is the status of order ORD-1001?" — should call `lookupOrderStatus`

## Dependencies

- Resolves [spring-ai-oci-genai-tools-blocker](../issues/spring-ai-oci-genai-tools-blocker.md)
- Should be done before [memory-observability-logs](./memory-observability-logs.md) to verify all three memory types are working
