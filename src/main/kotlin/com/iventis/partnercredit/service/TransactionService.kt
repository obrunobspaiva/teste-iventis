package com.iventis.partnercredit.service

import com.iventis.partnercredit.domain.model.Transaction
import com.iventis.partnercredit.domain.model.TransactionStatus
import com.iventis.partnercredit.domain.model.TransactionType
import com.iventis.partnercredit.domain.repository.TransactionRepository
import com.iventis.partnercredit.exception.DuplicateTransactionException
import com.iventis.partnercredit.exception.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val notificationService: NotificationService
) {

    @Transactional(readOnly = true)
    fun getTransactionById(transactionId: UUID): Transaction {
        return transactionRepository.findById(transactionId)
            .orElseThrow { ResourceNotFoundException("Transaction not found with id: $transactionId") }
    }

    @Transactional(readOnly = true)
    fun getTransactionsByPartnerId(partnerId: Long, pageable: Pageable): Page<Transaction> {
        return transactionRepository.findByPartnerId(partnerId, pageable)
    }

    @Transactional
    fun createTransaction(transaction: Transaction): Transaction {
        // Check for duplicate transaction using idempotency key
        val existingTransaction = transactionRepository
            .findByPartnerIdAndIdempotencyKey(transaction.partnerId, transaction.idempotencyKey)
        
        if (existingTransaction.isPresent) {
            return existingTransaction.get()
        }
        
        val savedTransaction = transactionRepository.save(transaction)
        
        // Notify external service for significant transactions (e.g., large amounts)
        if (shouldNotify(savedTransaction)) {
            notificationService.notifyTransactionProcessed(savedTransaction)
        }
        
        return savedTransaction
    }

    @Transactional
    fun updateTransactionStatus(transactionId: UUID, status: TransactionStatus): Transaction {
        val transaction = getTransactionById(transactionId)
        transaction.status = status
        transaction.updatedAt = LocalDateTime.now()
        return transactionRepository.save(transaction)
    }

    @Transactional(readOnly = true)
    fun getPendingTransactionsForReconciliation(cutoffTime: LocalDateTime, pageable: Pageable): Page<Transaction> {
        return transactionRepository.findPendingTransactionsForReconciliation(
            TransactionStatus.PENDING,
            cutoffTime,
            pageable
        )
    }

    private fun shouldNotify(transaction: Transaction): Boolean {
        // Example logic: notify for transactions with status COMPLETED
        return transaction.status == TransactionStatus.COMPLETED
    }
}
