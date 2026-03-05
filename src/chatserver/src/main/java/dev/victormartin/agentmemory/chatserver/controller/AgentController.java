package dev.victormartin.agentmemory.chatserver.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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
    private final VectorStore vectorStore;

    public AgentController(ChatClient.Builder builder,
                           JdbcChatMemoryRepository chatMemoryRepository,
                           VectorStore vectorStore) {
        this.vectorStore = vectorStore;

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(100)
                .build();

        this.chatClient = builder
                .defaultSystem("""
                        You are a helpful AI assistant with access to a knowledge base. \
                        When answering questions, use any relevant context provided to you. \
                        If you don't know the answer, say so honestly. \
                        Be concise and direct in your responses.""")
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .topK(5)
                                        .similarityThreshold(0.7)
                                        .build())
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

        try {
            String response = chatClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing chat request for conversation {}", conversationId, e);
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
            vectorStore.add(List.of(new Document(content)));
            return ResponseEntity.ok("Knowledge added.");
        } catch (Exception e) {
            log.error("Error adding knowledge to vector store", e);
            return ResponseEntity.internalServerError()
                    .body("Unable to store knowledge. Please try again later.");
        }
    }
}
