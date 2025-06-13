package com.iventis.partnercredit.service

import com.iventis.partnercredit.domain.model.Transaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    @Value("\${notification.topic}")
    private val notificationTopic: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun notifyTransactionProcessed(transaction: Transaction) {
        try {
            val notificationPayload = mapOf(
                "transactionId" to transaction.id,
                "partnerId" to transaction.partnerId,
                "type" to transaction.type,
                "amount" to transaction.amount,
                "status" to transaction.status,
                "timestamp" to transaction.updatedAt
            )
            
            logger.info("Sending notification for transaction: ${transaction.id}")
            kafkaTemplate.send(notificationTopic, transaction.id.toString(), notificationPayload)
                .whenComplete { result, ex ->
                    if (ex == null) {
                        logger.info("Notification sent successfully for transaction: ${transaction.id}")
                    } else {
                        logger.error("Failed to send notification for transaction: ${transaction.id}", ex)
                    }
                }
        } catch (e: Exception) {
            logger.error("Error sending notification for transaction: ${transaction.id}", e)
        }
    }
}
