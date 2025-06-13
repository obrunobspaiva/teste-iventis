package com.iventis.partnercredit.service

import com.iventis.partnercredit.domain.model.Transaction
import com.iventis.partnercredit.domain.model.TransactionStatus
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ReconciliationService(
    private val transactionService: TransactionService,
    private val balanceService: BalanceService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    // Run reconciliation every 5 minutes
    @Scheduled(fixedRate = 300000)
    @Transactional
    fun reconcilePendingTransactions() {
        logger.info("Starting reconciliation of pending transactions")
        
        // Get transactions that have been pending for more than 5 minutes
        val cutoffTime = LocalDateTime.now().minusMinutes(5)
        val pageSize = 100
        var currentPage = 0
        var hasMore = true
        
        while (hasMore) {
            val pendingTransactions = transactionService.getPendingTransactionsForReconciliation(
                cutoffTime,
                PageRequest.of(currentPage, pageSize)
            )
            
            if (pendingTransactions.isEmpty) {
                hasMore = false
                continue
            }
            
            pendingTransactions.forEach { transaction ->
                try {
                    logger.info("Reconciling transaction: ${transaction.id}")
                    processTransaction(transaction)
                } catch (e: Exception) {
                    logger.error("Error reconciling transaction: ${transaction.id}", e)
                    // Mark as failed if we can't process it after retry
                    transactionService.updateTransactionStatus(transaction.id, TransactionStatus.FAILED)
                }
            }
            
            if (pendingTransactions.isLast) {
                hasMore = false
            } else {
                currentPage++
            }
        }
        
        logger.info("Completed reconciliation of pending transactions")
    }
    
    private fun processTransaction(transaction: Transaction) {
        when (transaction.status) {
            TransactionStatus.PENDING -> {
                balanceService.processTransaction(transaction)
                transactionService.updateTransactionStatus(transaction.id, transaction.status)
            }
            else -> {
                logger.info("Transaction ${transaction.id} is not in PENDING state, skipping reconciliation")
            }
        }
    }
}
