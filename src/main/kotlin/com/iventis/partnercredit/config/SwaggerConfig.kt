package com.iventis.partnercredit.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .components(Components())
            .info(
                Info()
                    .title("Partner Credit Management API")
                    .description("API for managing credits for partners in a B2B platform")
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("Iventis")
                            .email("support@iventis.com")
                    )
            )
    }
}
