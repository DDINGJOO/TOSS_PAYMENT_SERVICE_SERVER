package com.teambind.payment.adapter.out.persistence;

import com.teambind.payment.domain.Money;
import com.teambind.payment.domain.Refund;
import com.teambind.payment.domain.RefundStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(RefundRepositoryAdapter.class)
@DisplayName("RefundRepositoryAdapter 테스트")
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RefundRepositoryAdapterTest {

    @Autowired
    private RefundRepositoryAdapter refundRepository;

    @Test
    @DisplayName("환불 저장 및 조회 - 성공")
    void save_AndFindById_Success() {
        // Given
        Refund refund = Refund.request("PAY-001", Money.of(50000), Money.of(45000), "고객 취소 요청");

        // When
        Refund savedRefund = refundRepository.save(refund);
        Optional<Refund> foundRefund = refundRepository.findById(savedRefund.getRefundId());

        // Then
        assertThat(foundRefund).isPresent();
        assertThat(foundRefund.get().getRefundId()).isEqualTo(savedRefund.getRefundId());
        assertThat(foundRefund.get().getPaymentId()).isEqualTo("PAY-001");
        assertThat(foundRefund.get().getRefundAmount()).isEqualTo(Money.of(45000));
        assertThat(foundRefund.get().getStatus()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    @DisplayName("결제 ID로 환불 목록 조회 - 성공")
    void findByPaymentId_Success() {
        // Given
        Refund refund1 = Refund.request("PAY-002", Money.of(50000), Money.of(45000), "취소");
        Refund refund2 = Refund.request("PAY-002", Money.of(50000), Money.of(40000), "재요청");
        refundRepository.save(refund1);
        refundRepository.save(refund2);

        // When
        List<Refund> refunds = refundRepository.findByPaymentId("PAY-002");

        // Then
        assertThat(refunds).hasSize(2);
        assertThat(refunds).extracting("paymentId").containsOnly("PAY-002");
    }

    @Test
    @DisplayName("존재하지 않는 환불 조회 - Empty 반환")
    void findById_NotFound_ReturnsEmpty() {
        // When
        Optional<Refund> foundRefund = refundRepository.findById("NOT-EXISTS");

        // Then
        assertThat(foundRefund).isEmpty();
    }

    @Test
    @DisplayName("환불 상태 업데이트 - 성공")
    void updateRefundStatus_Success() {
        // Given
        Refund refund = Refund.request("PAY-003", Money.of(50000), Money.of(45000), "취소");
        Refund savedRefund = refundRepository.save(refund);

        // When
        savedRefund.approve();
        Refund updatedRefund = refundRepository.save(savedRefund);

        // Then
        Optional<Refund> foundRefund = refundRepository.findById(updatedRefund.getRefundId());
        assertThat(foundRefund).isPresent();
        assertThat(foundRefund.get().getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(foundRefund.get().getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 ID로 환불 목록 조회 - 빈 목록 반환")
    void findByPaymentId_NotFound_ReturnsEmptyList() {
        // When
        List<Refund> refunds = refundRepository.findByPaymentId("NOT-EXISTS");

        // Then
        assertThat(refunds).isEmpty();
    }
}
