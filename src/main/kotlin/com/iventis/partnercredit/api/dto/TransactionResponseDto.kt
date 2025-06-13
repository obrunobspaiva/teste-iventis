package com.iventis.partnercredit.api.dto

import com.iventis.partnercredit.domain.model.TransactionStatus
import com.iventis.partnercredit.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class TransactionResponseDto(
    val id: UUID,
    val partnerId: Long,
    val type: TransactionType,
    val amount: BigDecimal,
    val description: String,
    val status: TransactionStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
