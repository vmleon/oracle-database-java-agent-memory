package dev.victormartin.agentmemory.chatserver.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class AgentTools {

    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);

    @Tool(description = "Look up the status of a customer order by its order ID. " +
            "Returns the current status including shipping information.")
    public String lookupOrderStatus(
            @ToolParam(description = "The order ID to look up, e.g. ORD-12345") String orderId) {
        log.info("Procedural memory: looking up order status for {}", orderId);
        // Simulated order lookup — in a real system this would query a database
        return "Order %s: Status SHIPPED. Shipped on 2026-03-04 via Express. Expected delivery: 2026-03-07."
                .formatted(orderId);
    }

    @Tool(description = "Initiate a product return for a given order. " +
            "Validates the order, checks the return window, and creates a return label. " +
            "Use this when a customer wants to return a product.")
    public String initiateReturn(
            @ToolParam(description = "The order ID to return") String orderId,
            @ToolParam(description = "The reason for the return") String reason) {
        log.info("Procedural memory: initiating return for order {} reason: {}", orderId, reason);
        // Simulated multi-step return procedure
        return """
                Return initiated for order %s.
                Step 1: Order validated — found in system.
                Step 2: Return window checked — within 30-day policy.
                Step 3: Return label created — RET-%s-001.
                Please ship the item back using the return label."""
                .formatted(orderId, orderId);
    }

    @Tool(description = "Escalate an issue to a human support agent. " +
            "Use this when the question is beyond your capabilities or the customer explicitly asks for a human.")
    public String escalateToSupport(
            @ToolParam(description = "Brief description of the issue") String issue,
            @ToolParam(description = "Priority level: low, medium, or high") String priority) {
        log.info("Procedural memory: escalating to support — issue: {}, priority: {}", issue, priority);
        return "Ticket created: SUP-2026-0042. Priority: %s. A human agent will follow up within %s."
                .formatted(priority.toUpperCase(),
                        switch (priority.toLowerCase()) {
                            case "high" -> "1 hour";
                            case "medium" -> "4 hours";
                            default -> "24 hours";
                        });
    }
}
