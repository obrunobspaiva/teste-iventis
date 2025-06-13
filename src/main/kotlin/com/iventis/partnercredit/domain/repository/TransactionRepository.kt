package com.iventis.partnercredit.domain.repository

import com.iventis.partnercredit.domain.model.Transaction
import com.iventis.partnercredit.domain.model.TransactionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@Repository
interface TransactionRepository : JpaRepository<Transaction, UUID> {
    
    fun findByPartnerId(partnerId: Long, pageable: Pageable): Page<Transaction>
    
    fun findByPartnerIdAndIdempotencyKey(partnerId: Long, idempotencyKey: String): Optional<Transaction>
    
    fun findByStatus(status: TransactionStatus, pageable: Pageable): Page<Transaction>
    
    @Query("SELECT t FROM Transaction t WHERE t.status = :status AND t.createdAt < :cutoffTime")
    fun findPendingTransactionsForReconciliation(
        status: TransactionStatus = TransactionStatus.PENDING,
        cutoffTime: LocalDateTime,
        pageable: Pageable
    ): Page<Transaction>
}
