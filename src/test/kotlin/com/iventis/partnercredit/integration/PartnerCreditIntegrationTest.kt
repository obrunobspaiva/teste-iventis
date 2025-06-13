package com.iventis.partnercredit.integration

import com.iventis.partnercredit.api.dto.BalanceResponseDto
import com.iventis.partnercredit.api.dto.CreditRequestDto
import com.iventis.partnercredit.api.dto.DebitRequestDto
import com.iventis.partnercredit.api.dto.TransactionResponseDto
import com.iventis.partnercredit.domain.model.TransactionStatus
import com.iventis.partnercredit.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.util.*

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PartnerCreditIntegrationTest {

    companion object {
        @Container
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("testdb")
            withUsername("testuser")
            withPassword("testpass")
        }
        
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgresContainer::getUsername)
            registry.add("spring.datasource.password", postgresContainer::getPassword)
            registry.add("spring.kafka.bootstrap-servers", { "localhost:9092" })
        }
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `full credit-debit flow test`() {
        val partnerId = 1L // Using one of the sample partners created by Flyway migration
        val baseUrl = "http://localhost:$port/api/v1/partners"
        
        // Step 1: Check initial balance
        val initialBalanceResponse = restTemplate.getForEntity(
            "$baseUrl/$partnerId/balance", 
            BalanceResponseDto::class.java
        )
        
        assertEquals(HttpStatus.OK, initialBalanceResponse.statusCode)
        assertNotNull(initialBalanceResponse.body)
        assertEquals(BigDecimal.ZERO, initialBalanceResponse.body?.balance)
        
        // Step 2: Add credits
        val creditAmount = BigDecimal("100.00")
        val creditRequest = CreditRequestDto(
            amount = creditAmount,
            description = "Initial credit",
            idempotencyKey = UUID.randomUUID().toString()
        )
        
        val creditResponse = restTemplate.postForEntity(
            "$baseUrl/$partnerId/credits",
            creditRequest,
            TransactionResponseDto::class.java
        )
        
        assertEquals(HttpStatus.CREATED, creditResponse.statusCode)
        assertNotNull(creditResponse.body)
        assertEquals(TransactionType.CREDIT, creditResponse.body?.type)
        assertEquals(creditAmount, creditResponse.body?.amount)
        assertEquals(TransactionStatus.COMPLETED, creditResponse.body?.status)
        
        // Step 3: Check updated balance
        val updatedBalanceResponse = restTemplate.getForEntity(
            "$baseUrl/$partnerId/balance", 
            BalanceResponseDto::class.java
        )
        
        assertEquals(HttpStatus.OK, updatedBalanceResponse.statusCode)
        assertNotNull(updatedBalanceResponse.body)
        assertEquals(creditAmount, updatedBalanceResponse.body?.balance)
        
        // Step 4: Consume credits
        val debitAmount = BigDecimal("30.00")
        val debitRequest = DebitRequestDto(
            amount = debitAmount,
            description = "Purchase",
            idempotencyKey = UUID.randomUUID().toString()
        )
        
        val debitResponse = restTemplate.postForEntity(
            "$baseUrl/$partnerId/debits",
            debitRequest,
            TransactionResponseDto::class.java
        )
        
        assertEquals(HttpStatus.CREATED, debitResponse.statusCode)
        assertNotNull(debitResponse.body)
        assertEquals(TransactionType.DEBIT, debitResponse.body?.type)
        assertEquals(debitAmount, debitResponse.body?.amount)
        assertEquals(TransactionStatus.COMPLETED, debitResponse.body?.status)
        
        // Step 5: Check final balance
        val finalBalanceResponse = restTemplate.getForEntity(
            "$baseUrl/$partnerId/balance", 
            BalanceResponseDto::class.java
        )
        
        assertEquals(HttpStatus.OK, finalBalanceResponse.statusCode)
        assertNotNull(finalBalanceResponse.body)
        assertEquals(creditAmount.subtract(debitAmount), finalBalanceResponse.body?.balance)
        
        // Step 6: Check transaction history
        val transactionsResponse = restTemplate.exchange(
            "$baseUrl/$partnerId/transactions",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            object : ParameterizedTypeReference<Map<String, Any>>() {}
        )
        
        assertEquals(HttpStatus.OK, transactionsResponse.statusCode)
        assertNotNull(transactionsResponse.body)
        
        @Suppress("UNCHECKED_CAST")
        val content = transactionsResponse.body?.get("content") as List<Map<String, Any>>
        assertEquals(2, content.size)
    }
    
    @Test
    fun `insufficient balance test`() {
        val partnerId = 2L // Using one of the sample partners created by Flyway migration
        val baseUrl = "http://localhost:$port/api/v1/partners"
        
        // Step 1: Add small amount of credits
        val creditAmount = BigDecimal("10.00")
        val creditRequest = CreditRequestDto(
            amount = creditAmount,
            description = "Small credit",
            idempotencyKey = UUID.randomUUID().toString()
        )
        
        restTemplate.postForEntity(
            "$baseUrl/$partnerId/credits",
            creditRequest,
            TransactionResponseDto::class.java
        )
        
        // Step 2: Try to consume more credits than available
        val debitAmount = BigDecimal("50.00")
        val debitRequest = DebitRequestDto(
            amount = debitAmount,
            description = "Large purchase",
            idempotencyKey = UUID.randomUUID().toString()
        )
        
        val debitResponse = restTemplate.postForEntity(
            "$baseUrl/$partnerId/debits",
            debitRequest,
            TransactionResponseDto::class.java
        )
        
        assertEquals(HttpStatus.CREATED, debitResponse.statusCode)
        assertNotNull(debitResponse.body)
        assertEquals(TransactionStatus.FAILED, debitResponse.body?.status)
        
        // Step 3: Check that balance is unchanged
        val balanceResponse = restTemplate.getForEntity(
            "$baseUrl/$partnerId/balance", 
            BalanceResponseDto::class.java
        )
        
        assertEquals(HttpStatus.OK, balanceResponse.statusCode)
        assertNotNull(balanceResponse.body)
        assertEquals(creditAmount, balanceResponse.body?.balance)
    }
    
    @Test
    fun `idempotency test`() {
        val partnerId = 3L // Using one of the sample partners created by Flyway migration
        val baseUrl = "http://localhost:$port/api/v1/partners"
        
        // Step 1: Add credits with specific idempotency key
        val idempotencyKey = UUID.randomUUID().toString()
        val creditAmount = BigDecimal("75.00")
        val creditRequest = CreditRequestDto(
            amount = creditAmount,
            description = "Idempotent credit",
            idempotencyKey = idempotencyKey
        )
        
        val firstResponse = restTemplate.postForEntity(
            "$baseUrl/$partnerId/credits",
            creditRequest,
            TransactionResponseDto::class.java
        )
        
        assertEquals(HttpStatus.CREATED, firstResponse.statusCode)
        assertNotNull(firstResponse.body)
        
        val firstTransactionId = firstResponse.body?.id
        
        // Step 2: Send the same request again with the same idempotency key
        val secondResponse = restTemplate.postForEntity(
            "$baseUrl/$partnerId/credits",
            creditRequest,
            TransactionResponseDto::class.java
        )
        
        assertEquals(HttpStatus.CREATED, secondResponse.statusCode)
        assertNotNull(secondResponse.body)
        
        // Verify that the same transaction was returned
        assertEquals(firstTransactionId, secondResponse.body?.id)
        
        // Step 3: Check that balance was only credited once
        val balanceResponse = restTemplate.getForEntity(
            "$baseUrl/$partnerId/balance", 
            BalanceResponseDto::class.java
        )
        
        assertEquals(HttpStatus.OK, balanceResponse.statusCode)
        assertNotNull(balanceResponse.body)
        assertEquals(creditAmount, balanceResponse.body?.balance)
    }
}
