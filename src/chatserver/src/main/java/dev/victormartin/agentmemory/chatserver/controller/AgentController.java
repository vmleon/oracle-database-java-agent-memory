package dev.victormartin.agentmemory.chatserver.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.victormartin.agentmemory.chatserver.retriever.OracleHybridDocumentRetriever;
import dev.victormartin.agentmemory.chatserver.tools.AgentTools;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final int MAX_MESSAGE_LENGTH = 10_000;
    private static final int MAX_KNOWLEDGE_LENGTH = 50_000;

    private final ChatClient chatClient;
    private final JdbcTemplate jdbcTemplate;

    public AgentController(ChatClient.Builder builder,
                           JdbcChatMemoryRepository chatMemoryRepository,
                           JdbcTemplate jdbcTemplate,
                           AgentTools agentTools) {
        this.jdbcTemplate = jdbcTemplate;

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(100)
                .build();

        var hybridRetriever = new OracleHybridDocumentRetriever(
                jdbcTemplate, 5, "POLICY_HYBRID_IDX", "rrf");

        QueryTransformer queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder.build().mutate())
                .targetSearchSystem("Oracle hybrid vector search over policy documents")
                .build();

        this.chatClient = builder
                .defaultSystem("""
                        You are a helpful AI assistant with access to a knowledge base \
                        and a set of tools for performing tasks. \
                        When answering questions, use any relevant context provided to you. \
                        When a user asks you to perform an action (like looking up an order, \
                        initiating a return, or escalating to support), use the appropriate tool. \
                        If you don't know the answer, say so honestly. \
                        Be concise and direct in your responses.""")
                .defaultTools(agentTools)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        RetrievalAugmentationAdvisor.builder()
                                .documentRetriever(hybridRetriever)
                                .queryTransformers(queryTransformer)
                                .build()
                )
                .build();
    }

    @PostMapping("/chat")
    public ResponseEntity<String> chat(
            @RequestBody String message,
            @RequestHeader("X-Conversation-Id") String conversationId) {

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("Message cannot be empty.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return ResponseEntity.badRequest()
                    .body("Message exceeds maximum length of " + MAX_MESSAGE_LENGTH + " characters.");
        }

        log.info("[conv:{}] Incoming: \"{}\"", conversationId,
                message.length() > 200 ? message.substring(0, 200) + "..." : message);

        try {
            String response = chatClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            log.info("[conv:{}] Response: 200 OK ({} chars)", conversationId,
                    response != null ? response.length() : 0);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[conv:{}] Error processing chat request", conversationId, e);
            return ResponseEntity.internalServerError()
                    .body("Unable to process your request. Please try again later.");
        }
    }

    @PostMapping("/knowledge")
    public ResponseEntity<String> addKnowledge(@RequestBody String content) {
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body("Content cannot be empty.");
        }
        if (content.length() > MAX_KNOWLEDGE_LENGTH) {
            return ResponseEntity.badRequest().body("Content exceeds maximum length of " + MAX_KNOWLEDGE_LENGTH + " characters.");
        }

        try {
            jdbcTemplate.update(
                    "INSERT INTO POLICY_DOCS (id, content) VALUES (sys_guid(), ?)",
                    content);
            return ResponseEntity.ok("Knowledge added.");
        } catch (Exception e) {
            log.error("Error adding knowledge to POLICY_DOCS", e);
            return ResponseEntity.internalServerError()
                    .body("Unable to store knowledge. Please try again later.");
        }
    }
}
