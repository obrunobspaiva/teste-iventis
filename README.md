# Sistema de Gestão de Créditos para Parceiros

Este microsserviço gerencia créditos para parceiros em uma plataforma B2B, permitindo que os parceiros reportem vendas e consumam créditos. O sistema foi projetado para suportar alta concorrência, sendo utilizado por milhares de parceiros simultaneamente.

## Funcionalidades Detalhadas

### Gestão de Créditos
- **Consulta de saldo**: Verificação em tempo real do saldo disponível por parceiro
- **Adição de créditos**: Processamento de créditos com validação e registro de transações
- **Consumo de créditos**: Validação de saldo suficiente antes do consumo
- **Histórico de transações**: Registro completo e paginado de todas as operações por parceiro

### Sistema de Transações
- **Identificação única**: Cada transação possui um UUID único
- **Tipificação**: Suporte para transações do tipo CRÉDITO e DÉBITO
- **Rastreabilidade**: Registro de valor, parceiro, descrição, data/hora
- **Gerenciamento de status**: Ciclo de vida completo (PENDENTE, CONCLUÍDA, FALHA)

### Mecanismos de Segurança e Confiabilidade
- **Conciliação automática**: Processo agendado para resolver transações pendentes
- **Idempotência**: Prevenção de duplicidade de transações através de chaves de idempotência
- **Notificações**: Integração com sistema de mensageria (Kafka) para notificar eventos significativos

## Stack Tecnológica

### Backend
- **Linguagem**: Kotlin 1.9.20
- **Framework**: Spring Boot 3.2.0
- **Persistência**: Spring Data JPA
- **Banco de dados**: PostgreSQL 15
- **Migrações**: Flyway

### Infraestrutura
- **Containerização**: Docker e Docker Compose
- **Mensageria**: Apache Kafka 7.3.0
- **Cache**: Caffeine

### Documentação e Testes
- **Documentação API**: Swagger/OpenAPI 3.0
- **Testes unitários**: JUnit 5 e MockK
- **Testes de integração**: TestContainers

## Estrutura do Projeto

```
src
├── main
│   ├── kotlin
│   │   └── com
│   │       └── iventis
│   │           └── partnercredit
│   │               ├── PartnerCreditApplication.kt
│   │               ├── api
│   │               │   ├── controller
│   │               │   │   └── PartnerController.kt
│   │               │   └── dto
│   │               │       ├── BalanceResponseDto.kt
│   │               │       ├── CreditRequestDto.kt
│   │               │       ├── DebitRequestDto.kt
│   │               │       └── TransactionResponseDto.kt
│   │               ├── config
│   │               │   ├── CacheConfig.kt
│   │               │   ├── KafkaConfig.kt
│   │               │   └── SwaggerConfig.kt
│   │               ├── domain
│   │               │   ├── model
│   │               │   │   ├── Partner.kt
│   │               │   │   ├── PartnerBalance.kt
│   │               │   │   └── Transaction.kt
│   │               │   └── repository
│   │               │       ├── PartnerBalanceRepository.kt
│   │               │       ├── PartnerRepository.kt
│   │               │       └── TransactionRepository.kt
│   │               ├── exception
│   │               │   ├── Exceptions.kt
│   │               │   └── GlobalExceptionHandler.kt
│   │               └── service
│   │                   ├── BalanceService.kt
│   │                   ├── NotificationService.kt
│   │                   ├── PartnerService.kt
│   │                   ├── ReconciliationService.kt
│   │                   └── TransactionService.kt
│   └── resources
│       ├── application.yml
│       └── db
│           └── migration
│               └── V1__initial_schema.sql
└── test
    └── kotlin
        └── com
            └── iventis
                └── partnercredit
                    ├── api
                    │   └── controller
                    │       └── PartnerControllerTest.kt
                    ├── integration
                    │   └── PartnerCreditIntegrationTest.kt
                    └── service
                        ├── BalanceServiceTest.kt
                        └── TransactionServiceTest.kt
```

## Detalhes de Implementação

### Modelo de Dados

#### Partner (Parceiro)
- Entidade que representa um parceiro no sistema
- Atributos: id, nome, email, datas de criação e atualização

#### Transaction (Transação)
- Registro de todas as operações de crédito e débito
- Atributos: id (UUID), partnerId, tipo (CREDIT/DEBIT), valor, descrição, status, chave de idempotência, timestamps

#### PartnerBalance (Saldo do Parceiro)
- Armazena o saldo atual de cada parceiro
- Implementa versionamento para controle de concorrência otimista
- Atributos: partnerId, saldo, versão, data da última atualização

### Serviços Principais

#### BalanceService
- Gerencia operações de saldo (consulta, adição, dedução)
- Implementa cache para melhorar performance de consultas frequentes
- Utiliza @Retryable para lidar com falhas de concorrência

#### TransactionService
- Gerencia o ciclo de vida das transações
- Implementa verificação de idempotência
- Integra com o serviço de notificação

#### ReconciliationService
- Executa periodicamente (a cada 5 minutos) para processar transações pendentes
- Utiliza @Scheduled para agendamento de tarefas

#### NotificationService
- Envia notificações para um tópico Kafka quando transações significativas são processadas

### Mecanismos de Segurança

#### Controle de Concorrência
- Bloqueio otimista com versionamento (@Version)
- Retry automático em caso de conflitos de concorrência

#### Idempotência
- Chaves de idempotência únicas por parceiro
- Constraint unique no banco de dados (partner_id + idempotency_key)

## Executando a Aplicação

### Pré-requisitos

- Docker e Docker Compose

### Passos

1. Clone o repositório
2. Navegue até o diretório do projeto
3. Execute o seguinte comando:

```bash
docker-compose up
```

A aplicação estará disponível em http://localhost:8080

A documentação da API (Swagger UI) estará disponível em http://localhost:8080/swagger-ui.html

## Endpoints da API

### Consulta de Saldo
```
GET /api/v1/partners/{partnerId}/balance
```
Retorna o saldo atual do parceiro.

### Adição de Créditos
```
POST /api/v1/partners/{partnerId}/credits
```
Adiciona créditos ao parceiro. Requer payload:
```json
{
  "amount": 100.00,
  "description": "Recarga de créditos",
  "idempotencyKey": "uuid-único"
}
```

### Consumo de Créditos
```
POST /api/v1/partners/{partnerId}/debits
```
Consome créditos do parceiro. Requer payload:
```json
{
  "amount": 50.00,
  "description": "Compra de produto",
  "idempotencyKey": "uuid-único"
}
```

### Histórico de Transações
```
GET /api/v1/partners/{partnerId}/transactions
```
Retorna o histórico de transações do parceiro, com suporte a paginação.

## Testes

O projeto inclui testes abrangentes:

- **Testes unitários**: Validam o comportamento isolado de serviços e componentes
- **Testes de integração**: Validam a integração entre componentes
- **Testes end-to-end**: Validam fluxos completos usando TestContainers para simular o ambiente real

Para executar os testes:

```bash
./gradlew test
```

## Arquitetura

O sistema segue uma arquitetura em camadas:

1. **API Layer**: Controllers e DTOs para interface com o cliente
2. **Service Layer**: Lógica de negócios e orquestração
3. **Repository Layer**: Acesso a dados
4. **Domain Layer**: Entidades e regras de negócio

O sistema é projetado para alta concorrência e evita condições de corrida através de bloqueio otimista. Implementa mecanismos de cache para melhorar o desempenho e inclui tratamento adequado de erros e mecanismos de retry.
