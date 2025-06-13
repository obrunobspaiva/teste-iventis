package com.iventis.partnercredit.service

import com.iventis.partnercredit.domain.model.Transaction
import com.iventis.partnercredit.domain.model.TransactionStatus
import com.iventis.partnercredit.domain.model.TransactionType
import com.iventis.partnercredit.domain.repository.TransactionRepository
import com.iventis.partnercredit.exception.ResourceNotFoundException
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class TransactionServiceTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var notificationService: NotificationService
    private lateinit var transactionService: TransactionService

    @BeforeEach
    fun setUp() {
        transactionRepository = mockk()
        notificationService = mockk(relaxUnitFun = true)
        transactionService = TransactionService(transactionRepository, notificationService)
    }

    @Test
    fun `getTransactionById should return transaction when found`() {
        // Given
        val transactionId = UUID.randomUUID()
        val transaction = Transaction(
            id = transactionId,
            partnerId = 1L,
            type = TransactionType.CREDIT,
            amount = BigDecimal("50.00"),
            description = "Test transaction",
            status = TransactionStatus.COMPLETED,
            idempotencyKey = "test-key"
        )
        
        every { transactionRepository.findById(transactionId) } returns Optional.of(transaction)
        
        // When
        val result = transactionService.getTransactionById(transactionId)
        
        // Then
        assertEquals(transaction, result)
        verify { transactionRepository.findById(transactionId) }
    }

    @Test
    fun `getTransactionById should throw exception when not found`() {
        // Given
        val transactionId = UUID.randomUUID()
        
        every { transactionRepository.findById(transactionId) } returns Optional.empty()
        
        // When/Then
        assertThrows<ResourceNotFoundException> {
            transactionService.getTransactionById(transactionId)
        }
        
        verify { transactionRepository.findById(transactionId) }
    }

    @Test
    fun `getTransactionsByPartnerId should return transactions for partner`() {
        // Given
        val partnerId = 1L
        val pageable = PageRequest.of(0, 10)
        val transactions = listOf(
            Transaction(
                partnerId = partnerId,
                type = TransactionType.CREDIT,
                amount = BigDecimal("50.00"),
                description = "Test credit",
                status = TransactionStatus.COMPLETED,
                idempotencyKey = "test-key-1"
            ),
            Transaction(
                partnerId = partnerId,
                type = TransactionType.DEBIT,
                amount = BigDecimal("20.00"),
                description = "Test debit",
                status = TransactionStatus.COMPLETED,
                idempotencyKey = "test-key-2"
            )
        )
        val page = PageImpl(transactions, pageable, transactions.size.toLong())
        
        every { transactionRepository.findByPartnerId(partnerId, pageable) } returns page
        
        // When
        val result = transactionService.getTransactionsByPartnerId(partnerId, pageable)
        
        // Then
        assertEquals(page, result)
        verify { transactionRepository.findByPartnerId(partnerId, pageable) }
    }

    @Test
    fun `createTransaction should save new transaction when no duplicate`() {
        // Given
        val partnerId = 1L
        val idempotencyKey = "test-key"
        val transaction = Transaction(
            partnerId = partnerId,
            type = TransactionType.CREDIT,
            amount = BigDecimal("50.00"),
            description = "Test transaction",
            status = TransactionStatus.COMPLETED,
            idempotencyKey = idempotencyKey
        )
        
        every { transactionRepository.findByPartnerIdAndIdempotencyKey(partnerId, idempotencyKey) } returns Optional.empty()
        every { transactionRepository.save(transaction) } returns transaction
        every { notificationService.notifyTransactionProcessed(transaction) } just runs
        
        // When
        val result = transactionService.createTransaction(transaction)
        
        // Then
        assertEquals(transaction, result)
        verify { transactionRepository.findByPartnerIdAndIdempotencyKey(partnerId, idempotencyKey) }
        verify { transactionRepository.save(transaction) }
        verify { notificationService.notifyTransactionProcessed(transaction) }
    }

    @Test
    fun `createTransaction should return existing transaction when duplicate found`() {
        // Given
        val partnerId = 1L
        val idempotencyKey = "test-key"
        val existingTransaction = Transaction(
            partnerId = partnerId,
            type = TransactionType.CREDIT,
            amount = BigDecimal("50.00"),
            description = "Test transaction",
            status = TransactionStatus.COMPLETED,
            idempotencyKey = idempotencyKey
        )
        val newTransaction = Transaction(
            partnerId = partnerId,
            type = TransactionType.CREDIT,
            amount = BigDecimal("50.00"),
            description = "Test transaction",
            status = TransactionStatus.PENDING,
            idempotencyKey = idempotencyKey
        )
        
        every { transactionRepository.findByPartnerIdAndIdempotencyKey(partnerId, idempotencyKey) } returns Optional.of(existingTransaction)
        
        // When
        val result = transactionService.createTransaction(newTransaction)
        
        // Then
        assertEquals(existingTransaction, result)
        verify { transactionRepository.findByPartnerIdAndIdempotencyKey(partnerId, idempotencyKey) }
        verify(exactly = 0) { transactionRepository.save(any()) }
        verify(exactly = 0) { notificationService.notifyTransactionProcessed(any()) }
    }

    @Test
    fun `updateTransactionStatus should update status`() {
        // Given
        val transactionId = UUID.randomUUID()
        val initialStatus = TransactionStatus.PENDING
        val newStatus = TransactionStatus.COMPLETED
        val transaction = Transaction(
            id = transactionId,
            partnerId = 1L,
            type = TransactionType.CREDIT,
            amount = BigDecimal("50.00"),
            description = "Test transaction",
            status = initialStatus,
            idempotencyKey = "test-key"
        )
        val updatedTransaction = transaction.copy(status = newStatus)
        
        every { transactionRepository.findById(transactionId) } returns Optional.of(transaction)
        every { transactionRepository.save(any()) } returns updatedTransaction
        
        // When
        val result = transactionService.updateTransactionStatus(transactionId, newStatus)
        
        // Then
        assertEquals(newStatus, result.status)
        verify { transactionRepository.findById(transactionId) }
        verify { transactionRepository.save(any()) }
    }

    @Test
    fun `getPendingTransactionsForReconciliation should return pending transactions`() {
        // Given
        val cutoffTime = LocalDateTime.now().minusMinutes(5)
        val pageable = PageRequest.of(0, 10)
        val pendingTransactions = listOf(
            Transaction(
                partnerId = 1L,
                type = TransactionType.CREDIT,
                amount = BigDecimal("50.00"),
                description = "Test credit",
                status = TransactionStatus.PENDING,
                idempotencyKey = "test-key-1",
                createdAt = LocalDateTime.now().minusMinutes(10)
            ),
            Transaction(
                partnerId = 2L,
                type = TransactionType.DEBIT,
                amount = BigDecimal("20.00"),
                description = "Test debit",
                status = TransactionStatus.PENDING,
                idempotencyKey = "test-key-2",
                createdAt = LocalDateTime.now().minusMinutes(15)
            )
        )
        val page = PageImpl(pendingTransactions, pageable, pendingTransactions.size.toLong())
        
        every { 
            transactionRepository.findPendingTransactionsForReconciliation(
                TransactionStatus.PENDING, 
                cutoffTime, 
                pageable
            ) 
        } returns page
        
        // When
        val result = transactionService.getPendingTransactionsForReconciliation(cutoffTime, pageable)
        
        // Then
        assertEquals(page, result)
        verify { 
            transactionRepository.findPendingTransactionsForReconciliation(
                TransactionStatus.PENDING, 
                cutoffTime, 
                pageable
            ) 
        }
    }
}
