package com.iventis.partnercredit.domain.repository

import com.iventis.partnercredit.domain.model.PartnerBalance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface PartnerBalanceRepository : JpaRepository<PartnerBalance, Long> {
    
    fun findByPartnerId(partnerId: Long): Optional<PartnerBalance>
}
