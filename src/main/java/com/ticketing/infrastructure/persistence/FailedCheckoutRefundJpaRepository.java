package com.ticketing.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ticketing.domain.order.FailedCheckoutRefund;
import com.ticketing.domain.order.RefundStatus;

@Repository
public interface FailedCheckoutRefundJpaRepository extends JpaRepository<FailedCheckoutRefund, UUID> {
    
    @Query("SELECT r FROM FailedCheckoutRefund r WHERE r.status = :status")
    List<FailedCheckoutRefund> findByStatus(@Param("status") RefundStatus status);
}
