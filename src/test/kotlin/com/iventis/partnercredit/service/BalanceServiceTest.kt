package com.iventis.partnercredit.service

import com.iventis.partnercredit.domain.model.PartnerBalance
import com.iventis.partnercredit.domain.model.Transaction
import com.iventis.partnercredit.domain.model.TransactionStatus
import com.iventis.partnercredit.domain.model.TransactionType
import com.iventis.partnercredit.domain.repository.PartnerBalanceRepository
import com.iventis.partnercredit.exception.InsufficientBalanceException
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.*

class BalanceServiceTest {

    private lateinit var partnerBalanceRepository: PartnerBalanceRepository
    private lateinit var partnerService: PartnerService
    private lateinit var balanceService: BalanceService

    @BeforeEach
    fun setUp() {
        partnerBalanceRepository = mockk()
        partnerService = mockk()
        balanceService = BalanceService(partnerBalanceRepository, partnerService)
    }

    @Test
    fun `getBalance should return existing balance`() {
        // Given
        val partnerId = 1L
        val expectedBalance = PartnerBalance(partnerId, BigDecimal("100.00"))
        
        every { partnerService.getPartnerById(partnerId) } returns mockk()
        every { partnerBalanceRepository.findByPartnerId(partnerId) } returns Optional.of(expectedBalance)
        
        // When
        val result = balanceService.getBalance(partnerId)
        
        // Then
        assertEquals(expectedBalance, result)
        verify { partnerService.getPartnerById(partnerId) }
        verify { partnerBalanceRepository.findByPartnerId(partnerId) }
    }

    @Test
    fun `getBalance should create new balance when not found`() {
        // Given
        val partnerId = 1L
        
        every { partnerService.getPartnerById(partnerId) } returns mockk()
        every { partnerBalanceRepository.findByPartnerId(partnerId) } returns Optional.empty()
        
        // When
        val result = balanceService.getBalance(partnerId)
        
        // Then
        assertEquals(partnerId, result.partnerId)
        assertEquals(BigDecimal.ZERO, result.balance)
        verify { partnerService.getPartnerById(partnerId) }
        verify { partnerBalanceRepository.findByPartnerId(partnerId) }
    }

    @Test
    fun `addCredits should increase balance`() {
        // Given
        val partnerId = 1L
        val initialBalance = BigDecimal("100.00")
        val creditAmount = BigDecimal("50.00")
        val expectedBalance = BigDecimal("150.00")
        
        val partnerBalance = PartnerBalance(partnerId, initialBalance)
        val expectedUpdatedBalance = PartnerBalance(partnerId, expectedBalance)
        
        every { partnerService.getPartnerById(partnerId) } returns mockk()
        every { partnerBalanceRepository.findByPartnerId(partnerId) } returns Optional.of(partnerBalance)
        every { partnerBalanceRepository.save(any()) } returns expectedUpdatedBalance
        
        // When
        val result = balanceService.addCredits(partnerId, creditAmount)
        
        // Then
        assertEquals(expectedBalance, result.balance)
        verify { partnerBalanceRepository.save(any()) }
    }

    @Test
    fun `deductCredits should decrease balance when sufficient`() {
        // Given
        val partnerId = 1L
        val initialBalance = BigDecimal("100.00")
        val debitAmount = BigDecimal("50.00")
        val expectedBalance = BigDecimal("50.00")
        
        val partnerBalance = PartnerBalance(partnerId, initialBalance)
        val expectedUpdatedBalance = PartnerBalance(partnerId, expectedBalance)
        
        every { partnerService.getPartnerById(partnerId) } returns mockk()
        every { partnerBalanceRepository.findByPartnerId(partnerId) } returns Optional.of(partnerBalance)
        every { partnerBalanceRepository.save(any()) } returns expectedUpdatedBalance
        
        // When
        val result = balanceService.deductCredits(partnerId, debitAmount)
        
        // Then
        assertEquals(expectedBalance, result.balance)
        verify { partnerBalanceRepository.save(any()) }
    }

    @Test
    fun `deductCredits should throw exception when insufficient balance`() {
        // Given
        val partnerId = 1L
        val initialBalance = BigDecimal("30.00")
        val debitAmount = BigDecimal("50.00")
        
        val partnerBalance = PartnerBalance(partnerId, initialBalance)
        
        every { partnerService.getPartnerById(partnerId) } returns mockk()
        every { partnerBalanceRepository.findByPartnerId(partnerId) } returns Optional.of(partnerBalance)
        
        // When/Then
        assertThrows<InsufficientBalanceException> {
            balanceService.deductCredits(partnerId, debitAmount)
        }
        
        verify(exactly = 0) { partnerBalanceRepository.save(any()) }
    }

    @Test
    fun `processTransaction should add credits for CREDIT transaction`() {
        // Given
        val partnerId = 1L
        val amount = BigDecimal("50.00")
        val transaction = Transaction(
            partnerId = partnerId,
            type = TransactionType.CREDIT,
            amount = amount,
            description = "Test credit",
            status = TransactionStatus.PENDING,
            idempotencyKey = "test-key"
        )
        
        val updatedBalance = PartnerBalance(partnerId, amount)
        
        every { partnerService.getPartnerById(partnerId) } returns mockk()
        every { partnerBalanceRepository.findByPartnerId(partnerId) } returns Optional.of(PartnerBalance(partnerId, BigDecimal.ZERO))
        every { partnerBalanceRepository.save(any()) } returns updatedBalance
        
        // When
        val result = balanceService.processTransaction(transaction)
        
        // Then
        assertEquals(TransactionStatus.COMPLETED, result.status)
        verify { partnerBalanceRepository.save(any()) }
    }

    @Test
    fun `processTransaction should deduct credits for DEBIT transaction with sufficient balance`() {
        // Given
        val partnerId = 1L
        val initialBalance = BigDecimal("100.00")
        val debitAmount = BigDecimal("50.00")
        val transaction = Transaction(
            partnerId = partnerId,
            type = TransactionType.DEBIT,
            amount = debitAmount,
            description = "Test debit",
            status = TransactionStatus.PENDING,
            idempotencyKey = "test-key"
        )
        
        val updatedBalance = PartnerBalance(partnerId, BigDecimal("50.00"))
        
        every { partnerService.getPartnerById(partnerId) } returns mockk()
        every { partnerBalanceRepository.findByPartnerId(partnerId) } returns Optional.of(PartnerBalance(partnerId, initialBalance))
        every { partnerBalanceRepository.save(any()) } returns updatedBalance
        
        // When
        val result = balanceService.processTransaction(transaction)
        
        // Then
        assertEquals(TransactionStatus.COMPLETED, result.status)
        verify { partnerBalanceRepository.save(any()) }
    }

    @Test
    fun `processTransaction should mark DEBIT transaction as FAILED when insufficient balance`() {
        // Given
        val partnerId = 1L
        val initialBalance = BigDecimal("30.00")
        val debitAmount = BigDecimal("50.00")
        val transaction = Transaction(
            partnerId = partnerId,
            type = TransactionType.DEBIT,
            amount = debitAmount,
            description = "Test debit",
            status = TransactionStatus.PENDING,
            idempotencyKey = "test-key"
        )
        
        every { partnerService.getPartnerById(partnerId) } returns mockk()
        every { partnerBalanceRepository.findByPartnerId(partnerId) } returns Optional.of(PartnerBalance(partnerId, initialBalance))
        
        // When
        val result = balanceService.processTransaction(transaction)
        
        // Then
        assertEquals(TransactionStatus.FAILED, result.status)
        verify(exactly = 0) { partnerBalanceRepository.save(any()) }
    }
}
