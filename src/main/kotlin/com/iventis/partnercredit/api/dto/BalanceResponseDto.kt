package com.iventis.partnercredit.api.dto

import java.math.BigDecimal

data class BalanceResponseDto(
    val partnerId: Long,
    val balance: BigDecimal
)
