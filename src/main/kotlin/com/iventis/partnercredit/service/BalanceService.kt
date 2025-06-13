package com.iventis.partnercredit.service

import com.iventis.partnercredit.domain.model.PartnerBalance
import com.iventis.partnercredit.domain.model.Transaction
import com.iventis.partnercredit.domain.model.TransactionStatus
import com.iventis.partnercredit.domain.model.TransactionType
import com.iventis.partnercredit.domain.repository.PartnerBalanceRepository
import com.iventis.partnercredit.exception.InsufficientBalanceException
import com.iventis.partnercredit.exception.ResourceNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class BalanceService(
    private val partnerBalanceRepository: PartnerBalanceRepository,
    private val partnerService: PartnerService
) {

    @Cacheable(value = ["partnerBalances"], key = "#partnerId")
    @Transactional(readOnly = true)
    fun getBalance(partnerId: Long): PartnerBalance {
        // Verify partner exists
        partnerService.getPartnerById(partnerId)
        
        return partnerBalanceRepository.findByPartnerId(partnerId)
            .orElseGet {
                // If no balance record exists, create one with zero balance
                PartnerBalance(partnerId = partnerId, balance = BigDecimal.ZERO)
            }
    }

    @CacheEvict(value = ["partnerBalances"], key = "#partnerId")
    @Retryable(value = [OptimisticLockingFailureException::class], maxAttempts = 3)
    @Transactional
    fun addCredits(partnerId: Long, amount: BigDecimal): PartnerBalance {
        val balance = getBalance(partnerId)
        balance.balance = balance.balance.add(amount)
        balance.lastUpdatedAt = LocalDateTime.now()
        return partnerBalanceRepository.save(balance)
    }

    @CacheEvict(value = ["partnerBalances"], key = "#partnerId")
    @Retryable(value = [OptimisticLockingFailureException::class], maxAttempts = 3)
    @Transactional
    fun deductCredits(partnerId: Long, amount: BigDecimal): PartnerBalance {
        val balance = getBalance(partnerId)
        
        if (balance.balance.compareTo(amount) < 0) {
            throw InsufficientBalanceException(
                "Insufficient balance for partner $partnerId. Required: $amount, Available: ${balance.balance}"
            )
        }
        
        balance.balance = balance.balance.subtract(amount)
        balance.lastUpdatedAt = LocalDateTime.now()
        return partnerBalanceRepository.save(balance)
    }

    @CacheEvict(value = ["partnerBalances"], key = "#transaction.partnerId")
    @Transactional
    fun processTransaction(transaction: Transaction): Transaction {
        return when (transaction.type) {
            TransactionType.CREDIT -> {
                addCredits(transaction.partnerId, transaction.amount)
                transaction.apply { status = TransactionStatus.COMPLETED }
            }
            TransactionType.DEBIT -> {
                try {
                    deductCredits(transaction.partnerId, transaction.amount)
                    transaction.apply { status = TransactionStatus.COMPLETED }
                } catch (e: InsufficientBalanceException) {
                    transaction.apply { status = TransactionStatus.FAILED }
                }
            }
        }
    }
}
