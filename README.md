# 💰 Expense Tracker API — Controle Financeiro Pessoal (Quarkus)

API RESTful completa acompanhada de Dashboard Web interativo, desenvolvida em **Java 21** com **Quarkus 3** e **PostgreSQL** para gerenciamento de finanças pessoais, importação automatizada de extratos bancários e faturas de cartão de crédito (**CSV**, **PDF** e **OFX**), detecção automática de instituição financeira, categorização inteligente com aprendizado contínuo e relatórios analíticos.

---

## 🎯 Destaques de Engenharia de Software (Back-End)

- **Arquitetura em Camadas & Design Limpo**: separação clara entre controladores REST (`rest`), objetos de transferência e filtros (`rest/dto`), lógica de negócio e parsers (`service`), entidades de domínio (`model`) e tratamento centralizado de erros (`exception` + `@Provider ExceptionMapper`).
- **Parsing Resiliente de Arquivos Bancários Reais (`CSV`, `PDF`, `OFX`)**:
  - Heurísticas e expressões regulares para extrair lançamentos de múltiplos bancos e fintechs com layouts distintos.
  - Tratamento de particularidades reais do sistema bancário brasileiro: distinção automática entre extrato de conta corrente e fatura de cartão, abatimento de estornos/reembolsos e cálculo da competência real de parcelas quando a fatura exibe a data original da compra (ex: Caixa, Itaú, BB).
- **Idempotência na Importação (`persistUnique`)**: prevenção de duplicidade tanto dentro do mesmo lote quanto contra registros já persistidos no banco, permitindo reimportar extratos com segurança.
- **Categorização Inteligente com Aprendizado Contínuo (`CategorizationService`)**:
  - Ao atualizar a categoria de uma transação (`PUT` ou `PATCH`), o sistema higieniza a descrição (removendo sufixos dinâmicos como `- Parcela X/Y`), aprende o padrão em `CategoryPattern` e **recategoriza retroativamente** outras transações pendentes na categoria `"Outros"`.
- **Padronização e Detecção de Instituições (`InstitutionDetector`)**: identifica automaticamente bancos (*Nubank, Inter, Itaú, Bradesco, Santander, Banco do Brasil, Caixa, C6 Bank, Mercado Pago, PicPay, PagBank, BTG Pactual, XP*) via tags OFX (`BANKID`/`FID`/`ORG`), cabeçalhos CSV, texto do PDF ou nome do arquivo, além de normalizar nomes informados manualmente (`canonicalize`).
- **Suíte de Testes Automatizados (`@QuarkusTest`)**: **31 testes** unitários e de integração com **JUnit 5** e **RestAssured**, executados em banco **H2 em memória** (`%test`), garantindo isolamento e execução rápida sem depender de containers externos.

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem**: Java 21
- **Framework**: Quarkus 3.40.1 (`quarkus-rest-jackson`, `quarkus-hibernate-orm-panache`, `quarkus-hibernate-validator`, `quarkus-smallrye-openapi`)
- **Banco de Dados**: PostgreSQL 16 (via Docker Compose) & H2 Database (ambiente de testes)
- **Processamento de Documentos**: Apache PDFBox 3.0.4
- **Testes**: JUnit 5 + RestAssured
- **Frontend Integrado**: HTML5, CSS3, JavaScript (ES6+ Fetch API) e Chart.js servidos estaticamente pelo Quarkus

---

## 📂 Estrutura do Projeto

```text
src/
├── main/
│   ├── java/org/acme/
│   │   ├── exception/       # Exceções de domínio (ApiException, CategoryBusinessException, etc.)
│   │   ├── model/           # Entidades Panache/JPA (Transaction, Category, CategoryPattern)
│   │   ├── rest/            # Endpoints REST (TransactionResource, CategoryResource, ApiExceptionMapper)
│   │   │   └── dto/         # DTOs de Request, Patch, Filtros, Paginação e Relatórios
│   │   └── service/         # Regras de negócio, Categorização e Parsers (CSV, PDF, OFX, InstitutionDetector)
│   └── resources/
│       ├── META-INF/resources/  # Dashboard Web (index.html, style.css, app.js)
│       ├── application.properties
│       └── import.sql
└── test/
    └── java/org/acme/       # Testes de integração REST e testes unitários de serviços
```

---

## ⚙️ Como Executar o Projeto

### 1. Subir o Banco de Dados PostgreSQL
Certifique-se de estar com o Docker em execução e rode na raiz do projeto:

```bash
docker compose up -d
```

### 2. Iniciar a Aplicação em Modo de Desenvolvimento
```bash
./mvnw quarkus:dev
```
Acesse no navegador:
- **Dashboard Web Interativo**: `http://localhost:8080`
- **Swagger UI (Documentação OpenAPI)**: `http://localhost:8080/q/swagger-ui`
- **Quarkus Dev UI**: `http://localhost:8080/q/dev/`

### 3. Executar os Testes Automatizados
Os testes utilizam banco H2 em memória (não dependem do Docker estar rodando):

```bash
./mvnw test
```

---

## 📌 Endpoints da API

### Categorias (`/categories`)
| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `GET` | `/categories` | Lista todas as categorias cadastradas |
| `GET` | `/categories/{id}` | Busca uma categoria pelo ID |
| `POST` | `/categories` | Cria uma nova categoria |
| `PUT` | `/categories/{id}` | Atualiza o nome de uma categoria |
| `DELETE` | `/categories/{id}` | Remove uma categoria (retorna `409 Conflict` se houver transações vinculadas) |
| `GET` | `/categories/patterns` | Lista os padrões aprendidos para categorização automática |
| `DELETE` | `/categories/patterns/{id}` | Remove um padrão aprendido pelo ID |

### Transações, Importação e Relatórios (`/transactions`)
| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `GET` | `/transactions` | Lista transações com filtros, ordenação (`sortBy`, `sortDir`) e paginação opcional (`page`, `size`) |
| `GET` | `/transactions/paged` | Lista transações envelopadas com metadados de paginação (`totalElements`, `totalPages`) |
| `POST` | `/transactions` | Cria manualmente uma transação |
| `PUT` | `/transactions/{id}` | Atualiza todos os dados de uma transação e aprende o padrão da categoria |
| `PATCH` | `/transactions/{id}` | Atualiza parcialmente (`categoryId`, `beneficiary`, `transactionType`, `institution`) |
| `DELETE` | `/transactions/{id}` | Remove uma transação pelo ID |
| `DELETE` | `/transactions` | Remove todas as transações |
| `GET` | `/transactions/summary` | Retorna `totalIncome`, `totalExpense` e `balance` do período/filtro |
| `GET` | `/transactions/by-category` | Retorna despesas líquidas (abatendo estornos) e percentual por categoria |
| `GET` | `/transactions/monthly-evolution` | Retorna evolução cronológica mês a mês de receitas, despesas e saldo |
| `GET` | `/transactions/installments/projection` | Projeta parcelas futuras para os próximos `months` meses (padrão: `6`) |
| `POST` | `/transactions/import/csv` | Importa arquivo CSV (`?creditCard=true\|false&institution=...`) |
| `POST` | `/transactions/import/pdf` | Importa arquivo PDF (`?creditCard=true\|false&institution=...`) |
| `POST` | `/transactions/import/ofx` | Importa arquivo OFX (`?creditCard=true\|false&institution=...`) |

#### Parâmetros de Filtro (`GET /transactions`, `/paged`, `/summary`, `/by-category`, `/monthly-evolution`)
- `year` e `month` (ex: `?year=2026&month=10`)
- `startDate` e `endDate` no formato `YYYY-MM-DD` (ex: `?startDate=2026-09-20&endDate=2026-10-19`)
- `categoryId` (ID da categoria)
- `type` (`INCOME` ou `EXPENSE`)
- `description` (busca parcial case-insensitive)
- `isCreditCard` (`true` ou `false`)
- `institution` (ex: `Nubank`, `Inter`, `Itaú`, `Caixa`)
- `page` e `size` (paginação base `0`, ex: `?page=0&size=20`)
- `sortBy` (`date`, `amount`, `description`, `id`) e `sortDir` (`asc`, `desc`)

---

## 💻 Exemplos de Requisições (`curl`)

```bash
# Criar transação manual
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Supermercado Extra",
    "transactionType": "EXPENSE",
    "categoryId": 1,
    "amount": 245.80,
    "date": "2026-10-25",
    "beneficiary": "Extra Hipermercados",
    "isCreditCard": true,
    "institution": "Nubank"
  }'

# Atualizar apenas a categoria e instituição via PATCH (aciona aprendizado automático)
curl -X PATCH http://localhost:8080/transactions/1 \
  -H "Content-Type: application/json" \
  -d '{"categoryId": 1, "institution": "Itaú"}'

# Importar fatura de cartão CSV
curl -X POST "http://localhost:8080/transactions/import/csv?creditCard=true&institution=Nubank" \
  -F "file=@fatura_outubro.csv"

# Consultar projeção de parcelas para os próximos 6 meses
curl "http://localhost:8080/transactions/installments/projection?months=6"
```
