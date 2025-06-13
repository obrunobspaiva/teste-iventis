package com.iventis.partnercredit.domain.repository

import com.iventis.partnercredit.domain.model.Partner
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PartnerRepository : JpaRepository<Partner, Long>
