package com.hify.refund.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class OrderService {

    private static final Map<String, Order> ORDERS = Map.of(
        "ORD-001", new Order("ORD-001", new BigDecimal("299.00"), LocalDateTime.now().minusDays(2)),
        "ORD-002", new Order("ORD-002", new BigDecimal("599.00"), LocalDateTime.now().minusDays(5)),
        "ORD-003", new Order("ORD-003", new BigDecimal("99.00"), LocalDateTime.now().minusDays(10)),
        "ORD-004", new Order("ORD-004", new BigDecimal("199.00"), null),
        "ORD-005", new Order("ORD-005", new BigDecimal("899.00"), LocalDateTime.now().minusDays(1))
    );

    public Order getOrder(String orderId) {
        return ORDERS.get(orderId);
    }

    public static class Order {
        private final String orderId;
        private final BigDecimal amount;
        private final LocalDateTime signedAt;

        public Order(String orderId, BigDecimal amount, LocalDateTime signedAt) {
            this.orderId = orderId;
            this.amount = amount;
            this.signedAt = signedAt;
        }

        public String getOrderId() { return orderId; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getSignedAt() { return signedAt; }

        public boolean withinRefundWindow() {
            if (signedAt == null) return false;
            return signedAt.plusDays(7).isAfter(LocalDateTime.now());
        }

        public LocalDateTime getRefundDeadline() {
            return signedAt == null ? null : signedAt.plusDays(7);
        }
    }
}
