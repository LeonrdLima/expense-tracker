# Expense Tracker API — Controle Financeiro Pessoal (Quarkus)

API RESTful desenvolvida em **Java 21** com **Quarkus 3** e **PostgreSQL** para gerenciamento de finanças pessoais, importação automatizada de extratos bancários e faturas de cartão de crédito (**CSV**, **PDF** e **OFX**), detecção automática de instituição financeira, categorização inteligente com aprendizado contínuo e relatórios analíticos.

---

## 🚀 Principais Funcionalidades

- **Gestão Completa de Transações (`CRUD` + `PATCH`)**: criação, listagem com filtros avançados, ordenação e paginação, atualização completa (`PUT`), atualização parcial (`PATCH`) e exclusão.
- **Gestão de Categorias e Padrões Aprendidos**: cadastro, edição e exclusão segura de categorias (impede exclusão quando há transações vinculadas) e gerenciamento dos padrões de categorização (`CategoryPattern`).
- **Importação Inteligente Multi-Formato**:
  - **CSV (`/transactions/import/csv`)**: detecta delimitadores (`,`, `;`, `\t`), mapeia colunas dinamicamente, distingue conta corrente de fatura de cartão de crédito e ignora pagamentos de fatura.
  - **PDF (`/transactions/import/pdf`)**: extrai lançamentos via Apache PDFBox, identifica datas flexíveis, compras parceladas (`Parcela X/Y`) e estornos.
  - **OFX (`/transactions/import/ofx`)**: lê blocos `<STMTTRN>`, extrai datas, valores, descrições e parcelas.
  - **Prevenção de Duplicidade**: evita reimportar transações idênticas já salvas no banco de dados.
- **Detecção Automática de Instituição Financeira**: reconhece bancos e fintechs (*Nubank, Inter, Itaú, Bradesco, Santander, Banco do Brasil, Caixa, C6 Bank, Mercado Pago, PicPay, PagBank, BTG Pactual, XP*) pelo código bancário, cabeçalhos, texto ou nome do arquivo, permitindo também sobrescrita manual via `?institution=...`.
- **Categorização Automática com Aprendizado (`CategorizationService`)**: ao alterar a categoria de uma transação via `PUT` ou `PATCH`, o sistema aprende o padrão da descrição e recategoriza automaticamente outras transações que estavam em `"Outros"`.
- **Relatórios Financeiros**:
  - **Resumo do Período (`/transactions/summary`)**: total de receitas, despesas e saldo líquido.
  - **Gastos por Categoria (`/transactions/by-category`)**: valores líquidos (abatendo estornos/reembolsos) e percentual sobre o total.
  - **Evolução Mensal (`/transactions/monthly-evolution`)**: histórico mês a mês de receitas, despesas, saldo e volume de transações.
  - **Projeção de Parcelas Futuras (`/transactions/installments/projection`)**: projeta o comprometimento mensal com compras parceladas para os próximos $N$ meses sem duplicar compras.

---

## 🛠️ Tecnologias Utilizadas

- **Java 21**
- **Quarkus 3.40.1** (`quarkus-rest-jackson`, `quarkus-hibernate-orm-panache`, `quarkus-hibernate-validator`, `quarkus-smallrye-openapi`)
- **PostgreSQL 16** (Produção / Dev via Docker Compose) & **H2 Database** (Testes em memória)
- **Apache PDFBox 3.0.4** (Extração de texto de faturas e extratos em PDF)
- **JUnit 5 + RestAssured** (Testes unitários e de integração)

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
- **API Base URL**: `http://localhost:8080`
- **Swagger UI (OpenAPI)**: `http://localhost:8080/q/swagger-ui`
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

### Transações e Relatórios (`/transactions`)
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
| `GET` | `/transactions/by-category` | Retorna despesas líquidas e percentual por categoria |
| `GET` | `/transactions/monthly-evolution` | Retorna evolução cronológica mês a mês de receitas, despesas e saldo |
| `GET` | `/transactions/installments/projection` | Projeta parcelas futuras para os próximos `months` meses (padrão: `6`) |
| `POST` | `/transactions/import/csv` | Importa arquivo CSV (`?creditCard=true\|false&institution=...`) |
| `POST` | `/transactions/import/pdf` | Importa arquivo PDF (`?institution=...`) |
| `POST` | `/transactions/import/ofx` | Importa arquivo OFX (`?institution=...`) |

#### Parâmetros de Filtro (`GET /transactions`, `/paged`, `/summary`, `/by-category`, `/monthly-evolution`)
- `year` e `month` (ex: `?year=2026&month=10`)
- `startDate` e `endDate` no formato `YYYY-MM-DD` (ex: `?startDate=2026-09-20&endDate=2026-10-19`)
- `categoryId` (ID da categoria)
- `type` (`INCOME` ou `EXPENSE`)
- `description` (busca parcial case-insensitive)
- `isCreditCard` (`true` ou `false`)
- `institution` (ex: `Nubank`, `Inter`, `Itaú`)
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

# Atualizar apenas a categoria e instituição via PATCH
curl -X PATCH http://localhost:8080/transactions/1 \
  -H "Content-Type: application/json" \
  -d '{"categoryId": 1, "institution": "Itaú"}'

# Importar fatura de cartão CSV
curl -X POST "http://localhost:8080/transactions/import/csv?creditCard=true&institution=Nubank" \
  -F "file=@fatura_outubro.csv"

# Consultar projeção de parcelas para os próximos 6 meses
curl "http://localhost:8080/transactions/installments/projection?months=6"
```
