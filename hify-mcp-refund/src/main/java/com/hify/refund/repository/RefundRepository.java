package com.hify.refund.repository;

import com.hify.refund.model.RefundApplication;
import com.hify.refund.model.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<RefundApplication, String> {
    Optional<RefundApplication> findTopByOrderIdOrderByCreatedAtDesc(String orderId);
    List<RefundApplication> findByOrderIdAndStatusIn(String orderId, List<RefundStatus> statuses);
}
