package dev.victormartin.agentmemory.chatserver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import dev.victormartin.agentmemory.chatserver.model.CustomerOrder;
import dev.victormartin.agentmemory.chatserver.model.OrderStatus;
import dev.victormartin.agentmemory.chatserver.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final OrderRepository orderRepository;
    private final VectorStore vectorStore;

    public DataSeeder(OrderRepository orderRepository, VectorStore vectorStore) {
        this.orderRepository = orderRepository;
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) {
        seedOrders();
        seedPolicies();
    }

    private void seedOrders() {
        if (orderRepository.count() > 0) {
            log.info("Orders already seeded, skipping.");
            return;
        }

        LocalDate today = LocalDate.now();

        List<CustomerOrder> orders = List.of(
                new CustomerOrder("ORD-1001", "Wireless Headphones", 1, new BigDecimal("79.99"),
                        OrderStatus.DELIVERED, today.minusDays(10), "123 Main St, Springfield"),
                new CustomerOrder("ORD-1002", "USB-C Hub", 1, new BigDecimal("45.00"),
                        OrderStatus.SHIPPED, today.minusDays(3), "123 Main St, Springfield"),
                new CustomerOrder("ORD-1003", "Mechanical Keyboard", 1, new BigDecimal("129.99"),
                        OrderStatus.DELIVERED, today.minusDays(45), "123 Main St, Springfield"),
                new CustomerOrder("ORD-1004", "Monitor Stand", 2, new BigDecimal("34.50"),
                        OrderStatus.PLACED, today, "456 Oak Ave, Shelbyville"),
                new CustomerOrder("ORD-1005", "Laptop Sleeve", 1, new BigDecimal("25.99"),
                        OrderStatus.DELIVERED, today.minusDays(20), "456 Oak Ave, Shelbyville"),
                new CustomerOrder("ORD-1006", "Webcam HD", 1, new BigDecimal("59.99"),
                        OrderStatus.CONFIRMED, today.minusDays(1), "123 Main St, Springfield"),
                new CustomerOrder("ORD-1007", "Bluetooth Speaker", 1, new BigDecimal("89.99"),
                        OrderStatus.CANCELLED, today.minusDays(15), "789 Elm Dr, Capital City"),
                new CustomerOrder("ORD-1008", "Ergonomic Mouse", 1, new BigDecimal("39.99"),
                        OrderStatus.DELIVERED, today.minusDays(5), "789 Elm Dr, Capital City")
        );

        orderRepository.saveAll(orders);
        log.info("Seeded {} demo orders.", orders.size());
    }

    private void seedPolicies() {
        log.info("Seeding policy documents into vector store...");

        List<Document> policies = List.of(
                new Document("""
                        Return Policy: Customers may return any product within 30 days of the purchase date \
                        for a full refund. The order must be in DELIVERED status to be eligible for return. \
                        Orders that are still being shipped, have been cancelled, or are already being returned \
                        are not eligible. Once a return is initiated, the order status changes to PREPARING_RETURN. \
                        Refunds are processed within 5-7 business days after the returned item is received at \
                        our warehouse. The item must be in its original packaging and in unused condition."""),
                new Document("""
                        Shipping Policy: All orders go through the following status transitions: \
                        PLACED (order received) -> CONFIRMED (payment verified) -> SHIPPED (in transit) -> \
                        DELIVERED (received by customer). Standard shipping takes 5-7 business days. \
                        Express shipping takes 2-3 business days and costs an additional $9.99. \
                        Customers receive tracking information via email once the order status changes to SHIPPED. \
                        Delivery confirmation is sent automatically when the carrier marks the package as delivered."""),
                new Document("""
                        Support Policy: Our support team handles issues at three priority levels. \
                        HIGH priority: response within 1 hour — for urgent issues like payment problems, \
                        wrong items received, or damaged goods. MEDIUM priority: response within 4 hours — \
                        for order modifications, shipping delays, or product questions. LOW priority: response \
                        within 24 hours — for general inquiries, feedback, or feature requests. \
                        All support tickets are tracked with a unique ticket ID and customers can check \
                        their ticket status at any time.""")
        );

        vectorStore.add(policies);
        log.info("Seeded {} policy documents.", policies.size());
    }
}
