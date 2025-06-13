package com.iventis.partnercredit.api.controller

import com.iventis.partnercredit.api.dto.BalanceResponseDto
import com.iventis.partnercredit.api.dto.CreditRequestDto
import com.iventis.partnercredit.api.dto.DebitRequestDto
import com.iventis.partnercredit.api.dto.TransactionResponseDto
import com.iventis.partnercredit.domain.model.Transaction
import com.iventis.partnercredit.domain.model.TransactionStatus
import com.iventis.partnercredit.domain.model.TransactionType
import com.iventis.partnercredit.service.BalanceService
import com.iventis.partnercredit.service.PartnerService
import com.iventis.partnercredit.service.TransactionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/partners")
@Tag(name = "Partner Credit Management", description = "APIs for managing partner credits")
class PartnerController(
    private val partnerService: PartnerService,
    private val balanceService: BalanceService,
    private val transactionService: TransactionService
) {

    @GetMapping("/{partnerId}/balance")
    @Operation(summary = "Get partner's credit balance")
    fun getBalance(@PathVariable partnerId: Long): ResponseEntity<BalanceResponseDto> {
        val balance = balanceService.getBalance(partnerId)
        return ResponseEntity.ok(
            BalanceResponseDto(
                partnerId = balance.partnerId,
                balance = balance.balance
            )
        )
    }

    @PostMapping("/{partnerId}/credits")
    @Operation(summary = "Add credits to partner")
    fun addCredits(
        @PathVariable partnerId: Long,
        @Valid @RequestBody request: CreditRequestDto
    ): ResponseEntity<TransactionResponseDto> {
        // Verify partner exists
        partnerService.getPartnerById(partnerId)
        
        // Create a credit transaction
        val transaction = Transaction(
            partnerId = partnerId,
            type = TransactionType.CREDIT,
            amount = request.amount,
            description = request.description,
            status = TransactionStatus.PENDING,
            idempotencyKey = request.idempotencyKey
        )
        
        // Save the transaction
        val savedTransaction = transactionService.createTransaction(transaction)
        
        // Process the transaction (add credits)
        val processedTransaction = balanceService.processTransaction(savedTransaction)
        
        // Update the transaction status
        val finalTransaction = transactionService.updateTransactionStatus(
            processedTransaction.id,
            processedTransaction.status
        )
        
        return ResponseEntity.status(HttpStatus.CREATED).body(
            TransactionResponseDto(
                id = finalTransaction.id,
                partnerId = finalTransaction.partnerId,
                type = finalTransaction.type,
                amount = finalTransaction.amount,
                description = finalTransaction.description,
                status = finalTransaction.status,
                createdAt = finalTransaction.createdAt,
                updatedAt = finalTransaction.updatedAt
            )
        )
    }

    @PostMapping("/{partnerId}/debits")
    @Operation(summary = "Consume credits from partner")
    fun consumeCredits(
        @PathVariable partnerId: Long,
        @Valid @RequestBody request: DebitRequestDto
    ): ResponseEntity<TransactionResponseDto> {
        // Verify partner exists
        partnerService.getPartnerById(partnerId)
        
        // Create a debit transaction
        val transaction = Transaction(
            partnerId = partnerId,
            type = TransactionType.DEBIT,
            amount = request.amount,
            description = request.description,
            status = TransactionStatus.PENDING,
            idempotencyKey = request.idempotencyKey
        )
        
        // Save the transaction
        val savedTransaction = transactionService.createTransaction(transaction)
        
        // Process the transaction (deduct credits)
        val processedTransaction = balanceService.processTransaction(savedTransaction)
        
        // Update the transaction status
        val finalTransaction = transactionService.updateTransactionStatus(
            processedTransaction.id,
            processedTransaction.status
        )
        
        return ResponseEntity.status(HttpStatus.CREATED).body(
            TransactionResponseDto(
                id = finalTransaction.id,
                partnerId = finalTransaction.partnerId,
                type = finalTransaction.type,
                amount = finalTransaction.amount,
                description = finalTransaction.description,
                status = finalTransaction.status,
                createdAt = finalTransaction.createdAt,
                updatedAt = finalTransaction.updatedAt
            )
        )
    }

    @GetMapping("/{partnerId}/transactions")
    @Operation(summary = "Get partner's transaction history")
    fun getTransactionHistory(
        @PathVariable partnerId: Long,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<TransactionResponseDto>> {
        // Verify partner exists
        partnerService.getPartnerById(partnerId)
        
        val transactions = transactionService.getTransactionsByPartnerId(partnerId, pageable)
        
        return ResponseEntity.ok(
            transactions.map { transaction ->
                TransactionResponseDto(
                    id = transaction.id,
                    partnerId = transaction.partnerId,
                    type = transaction.type,
                    amount = transaction.amount,
                    description = transaction.description,
                    status = transaction.status,
                    createdAt = transaction.createdAt,
                    updatedAt = transaction.updatedAt
                )
            }
        )
    }
}
