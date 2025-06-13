package com.iventis.partnercredit.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "partner_balances")
data class PartnerBalance(
    @Id
    @Column(name = "partner_id")
    val partnerId: Long,
    
    @Column(nullable = false)
    var balance: BigDecimal,
    
    @Version
    @Column(nullable = false)
    var version: Long = 0,
    
    @Column(name = "last_updated_at", nullable = false)
    var lastUpdatedAt: LocalDateTime = LocalDateTime.now()
)
