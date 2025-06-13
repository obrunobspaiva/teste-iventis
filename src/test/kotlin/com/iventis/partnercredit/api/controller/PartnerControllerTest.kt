package com.iventis.partnercredit.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.iventis.partnercredit.api.dto.CreditRequestDto
import com.iventis.partnercredit.api.dto.DebitRequestDto
import com.iventis.partnercredit.domain.model.PartnerBalance
import com.iventis.partnercredit.domain.model.Transaction
import com.iventis.partnercredit.domain.model.TransactionStatus
import com.iventis.partnercredit.domain.model.TransactionType
import com.iventis.partnercredit.service.BalanceService
import com.iventis.partnercredit.service.PartnerService
import com.iventis.partnercredit.service.TransactionService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@WebMvcTest(PartnerController::class)
class PartnerControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var partnerService: PartnerService

    @MockBean
    private lateinit var balanceService: BalanceService

    @MockBean
    private lateinit var transactionService: TransactionService

    @Test
    fun `getBalance should return partner balance`() {
        // Given
        val partnerId = 1L
        val balance = PartnerBalance(partnerId, BigDecimal("100.00"))
        
        `when`(balanceService.getBalance(partnerId)).thenReturn(balance)
        
        // When/Then
        mockMvc.perform(get("/api/v1/partners/$partnerId/balance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.partnerId").value(partnerId))
            .andExpect(jsonPath("$.balance").value("100.00"))
    }

    @Test
    fun `addCredits should add credits to partner`() {
        // Given
        val partnerId = 1L
        val creditRequest = CreditRequestDto(
            amount = BigDecimal("50.00"),
            description = "Test credit",
            idempotencyKey = "test-key"
        )
        
        val transaction = Transaction(
            id = UUID.randomUUID(),
            partnerId = partnerId,
            type = TransactionType.CREDIT,
            amount = creditRequest.amount,
            description = creditRequest.description,
            status = TransactionStatus.PENDING,
            idempotencyKey = creditRequest.idempotencyKey
        )
        
        val processedTransaction = transaction.copy(status = TransactionStatus.COMPLETED)
        
        `when`(transactionService.createTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(transaction)
        `when`(balanceService.processTransaction(transaction)).thenReturn(processedTransaction)
        `when`(transactionService.updateTransactionStatus(processedTransaction.id, processedTransaction.status))
            .thenReturn(processedTransaction)
        
        // When/Then
        mockMvc.perform(
            post("/api/v1/partners/$partnerId/credits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(creditRequest))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.partnerId").value(partnerId))
            .andExpect(jsonPath("$.type").value("CREDIT"))
            .andExpect(jsonPath("$.amount").value("50.00"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
    }

    @Test
    fun `consumeCredits should deduct credits from partner`() {
        // Given
        val partnerId = 1L
        val debitRequest = DebitRequestDto(
            amount = BigDecimal("30.00"),
            description = "Test debit",
            idempotencyKey = "test-key"
        )
        
        val transaction = Transaction(
            id = UUID.randomUUID(),
            partnerId = partnerId,
            type = TransactionType.DEBIT,
            amount = debitRequest.amount,
            description = debitRequest.description,
            status = TransactionStatus.PENDING,
            idempotencyKey = debitRequest.idempotencyKey
        )
        
        val processedTransaction = transaction.copy(status = TransactionStatus.COMPLETED)
        
        `when`(transactionService.createTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(transaction)
        `when`(balanceService.processTransaction(transaction)).thenReturn(processedTransaction)
        `when`(transactionService.updateTransactionStatus(processedTransaction.id, processedTransaction.status))
            .thenReturn(processedTransaction)
        
        // When/Then
        mockMvc.perform(
            post("/api/v1/partners/$partnerId/debits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(debitRequest))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.partnerId").value(partnerId))
            .andExpect(jsonPath("$.type").value("DEBIT"))
            .andExpect(jsonPath("$.amount").value("30.00"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
    }

    @Test
    fun `getTransactionHistory should return partner transactions`() {
        // Given
        val partnerId = 1L
        val pageable = PageRequest.of(0, 20)
        
        val transactions = listOf(
            Transaction(
                id = UUID.randomUUID(),
                partnerId = partnerId,
                type = TransactionType.CREDIT,
                amount = BigDecimal("50.00"),
                description = "Test credit",
                status = TransactionStatus.COMPLETED,
                idempotencyKey = "test-key-1",
                createdAt = LocalDateTime.now().minusDays(1),
                updatedAt = LocalDateTime.now().minusDays(1)
            ),
            Transaction(
                id = UUID.randomUUID(),
                partnerId = partnerId,
                type = TransactionType.DEBIT,
                amount = BigDecimal("20.00"),
                description = "Test debit",
                status = TransactionStatus.COMPLETED,
                idempotencyKey = "test-key-2",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )
        
        val page = PageImpl(transactions, pageable, transactions.size.toLong())
        
        `when`(transactionService.getTransactionsByPartnerId(partnerId, pageable)).thenReturn(page)
        
        // When/Then
        mockMvc.perform(get("/api/v1/partners/$partnerId/transactions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].type").value("CREDIT"))
            .andExpect(jsonPath("$.content[1].type").value("DEBIT"))
    }
}
