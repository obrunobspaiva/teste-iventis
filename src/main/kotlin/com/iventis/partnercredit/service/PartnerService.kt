package com.iventis.partnercredit.service

import com.iventis.partnercredit.domain.model.Partner
import com.iventis.partnercredit.domain.repository.PartnerRepository
import com.iventis.partnercredit.exception.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PartnerService(private val partnerRepository: PartnerRepository) {

    @Transactional(readOnly = true)
    fun getPartnerById(partnerId: Long): Partner {
        return partnerRepository.findById(partnerId)
            .orElseThrow { ResourceNotFoundException("Partner not found with id: $partnerId") }
    }

    @Transactional
    fun createPartner(partner: Partner): Partner {
        return partnerRepository.save(partner)
    }
}
