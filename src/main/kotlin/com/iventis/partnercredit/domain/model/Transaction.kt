package com.iventis.partnercredit.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "transactions")
data class Transaction(
    @Id
    val id: UUID = UUID.randomUUID(),
    
    @Column(name = "partner_id", nullable = false)
    val partnerId: Long,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: TransactionType,
    
    @Column(nullable = false)
    val amount: BigDecimal,
    
    @Column(nullable = false)
    val description: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TransactionStatus,
    
    @Column(name = "idempotency_key", nullable = false)
    val idempotencyKey: String,
    
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class TransactionType {
    CREDIT, DEBIT
}

enum class TransactionStatus {
    PENDING, COMPLETED, FAILED
}
