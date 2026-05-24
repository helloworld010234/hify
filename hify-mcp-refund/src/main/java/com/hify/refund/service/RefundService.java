package com.hify.refund.service;

import com.hify.refund.model.RefundApplication;
import com.hify.refund.model.RefundStatus;
import com.hify.refund.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RefundService {

    private final RefundRepository refundRepository;
    private final OrderService orderService;

    public RefundService(RefundRepository refundRepository, OrderService orderService) {
        this.refundRepository = refundRepository;
        this.orderService = orderService;
    }

    public Map<String, Object> checkEligibility(String orderId) {
        OrderService.Order order = orderService.getOrder(orderId);
        if (order == null) return Map.of("success", false, "message", "未找到订单：" + orderId);
        if (order.getSignedAt() == null) return Map.of("success", false, "eligible", false,
                "message", "该订单尚未签收，签收后才可申请退款。", "amount", order.getAmount());
        if (!order.withinRefundWindow()) return Map.of("success", false, "eligible", false,
                "message", "该订单已超过退款期限（签收后 7 天内可申请）。",
                "deadline", fmt(order.getRefundDeadline()), "amount", order.getAmount());
        return Map.of("success", true, "eligible", true,
                "message", "订单符合退款条件，请在 " + fmt(order.getRefundDeadline()) + " 前提交申请。",
                "deadline", fmt(order.getRefundDeadline()), "amount", order.getAmount());
    }

    @Transactional
    public Map<String, Object> submitRefund(String orderId, String userId, BigDecimal amount, String reason) {
        Map<String,Object> eligibility = checkEligibility(orderId);
        if (!Boolean.TRUE.equals(eligibility.get("eligible")))
            return Map.of("success", false, "message", eligibility.get("message"));

        List<RefundApplication> existing = refundRepository.findByOrderIdAndStatusIn(orderId,
                List.of(RefundStatus.PENDING, RefundStatus.APPROVED, RefundStatus.PROCESSING));
        if (!existing.isEmpty()) {
            RefundApplication e = existing.get(0);
            return Map.of("success", false, "message",
                    "该订单已有进行中的退款申请，编号：" + e.getRefundId()
                    + "，当前状态：" + e.getStatus().getDescription()
                    + "。如需修改，请先撤销后再重新提交。");
        }

        RefundApplication app = new RefundApplication(orderId, userId, reason, amount);
        refundRepository.save(app);
        return Map.of("success", true, "message", "退款申请已提交，预计 3 个工作日内完成审核。",
                "refundId", app.getRefundId(), "status", app.getStatus().name(),
                "statusLabel", app.getStatus().getDescription(), "estimatedDays", 3);
    }

    public Map<String, Object> getStatus(String orderId) {
        Optional<RefundApplication> opt = refundRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId);
        if (opt.isEmpty()) return Map.of("success", false,
                "message", "订单 " + orderId + " 暂无退款申请记录。");
        RefundApplication app = opt.get();
        return Map.of("success", true,
                "message", buildStatusMessage(app),
                "refundId", app.getRefundId(), "orderId", app.getOrderId(),
                "amount", app.getAmount(), "status", app.getStatus().name(),
                "statusLabel", app.getStatus().getDescription(),
                "submittedAt", fmt(app.getCreatedAt()), "rejectReason", app.getRejectReason());
    }

    @Transactional
    public Map<String, Object> cancelRefund(String refundId) {
        Optional<RefundApplication> opt = refundRepository.findById(refundId);
        if (opt.isEmpty()) return Map.of("success", false,
                "message", "未找到退款单：" + refundId + "，请确认单号是否正确。");
        RefundApplication app = opt.get();
        if (app.getStatus() != RefundStatus.PENDING)
            return Map.of("success", false, "message",
                    "退款单 " + refundId + " 当前状态为「" + app.getStatus().getDescription()
                    + "」，退款已在处理中，无法撤销。");
        app.setStatus(RefundStatus.CANCELLED);
        refundRepository.save(app);
        return Map.of("success", true,
                "message", "退款单 " + refundId + " 已撤销成功。如需退款，可重新提交申请。",
                "refundId", app.getRefundId(), "status", app.getStatus().name(),
                "statusLabel", app.getStatus().getDescription());
    }

    private static String fmt(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private static String buildStatusMessage(RefundApplication app) {
        return "退款单 " + app.getRefundId() + " 当前状态为「" + app.getStatus().getDescription() + "」。";
    }
}
