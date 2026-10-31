// Variáveis globais para armazenar as categorias e instâncias dos gráficos
let categories = [];
let categoryChartInstance = null;
let evolutionChartInstance = null;

// Função utilitária que formata um número para o padrão monetário brasileiro
function formatCurrency(value) {
    return new Intl.NumberFormat('pt-BR', {
        style: 'currency',
        currency: 'BRL'
    }).format(value || 0);
}

// Função utilitária que formata uma data para o padrão brasileiro (dd/mm/yyyy)
function formatDate(isoDate) {
    if(!isoDate) return '-';
    const [year, month, day] = isoDate.split('-');
    return `${day}/${month}/${year}`;
}

// Lê os campos de filtro da tela e monta a query string (ex: ?type=EXPENSE&institution=Nubank")
function buildFilterQuery() {
    const params = new URLSearchParams();
    
    const startDate = document.getElementById('filter-start-date').value;
    const endDate = document.getElementById('filter-end-date').value;
    const categoryId = document.getElementById('filter-category').value;
    const type = document.getElementById('filter-type').value;
    const institution = document.getElementById('filter-institution').value;
    const description = document.getElementById('filter-description').value;

    if(startDate) params.append('startDate', startDate);
    if(endDate) params.append('endDate', endDate);
    if(categoryId) params.append('categoryId', categoryId);
    if(type) params.append('type', type);
    if(institution) params.append('institution', institution);
    if(description) params.append('description', description);

    const queryString = params.toString();
    return queryString ? `?${queryString}` : '';
}

// Busca as categorias do backend e atualiza o array categories
async function fetchCategories() {
    try {
        const response = await fetch('/categories');
        if(response.ok) {
            categories = await response.json();

            const filterSelect = document.getElementById('filter-category');
            filterSelect.innerHTML = '<option value="">Todas</option>' 
            + categories.map(category => `
                <option value="${category.id}">${category.name}</option>
            `).join('');

            const txCategorySelect = document.getElementById('tx-category');
            if(txCategorySelect) {
                txCategorySelect.innerHTML = categories.map(category => `
                    <option value="${category.id}">${category.name}</option>
                `).join('');
            }

        }
    } catch(error) {
        console.error('Falha ao buscar as categorias:', error);
    }
}

// Função assíncrona que busca o balanço no endpoint GET /transactions/summary
async function fetchBalance() {
    try {
        const response = await fetch(`/transactions/summary${buildFilterQuery()}`);
        if(!response.ok) {
            throw new Error('Erro ao buscar o resumo financeiro');
        }

        // Converte o JSON retornado pelo Quarkus ({ totalIncome, totalExpense, balance }) em um objeto JavaScript
        const data = await response.json();

        // Atualiza o conteúdo do elemento com id "balance" com o balanço formatado
        document.getElementById('total-income').textContent = formatCurrency(data.totalIncome);
        document.getElementById('total-expense').textContent = formatCurrency(data.totalExpense);
        document.getElementById('balance').textContent = formatCurrency(data.balance);
    } catch(error) {
        console.error('Falha ao carregar o resumo financeiro:', error);
    }
}

// Busca as transações (GET /transactions) e desenha na tabela
async function fetchTransactions() {
    const tbody = document.getElementById('transactions-body');
    const countBadge = document.getElementById('transactions-count');

    try {
        const response = await fetch(`/transactions${buildFilterQuery()}`);
        if(!response.ok) {
            throw new Error('Erro ao buscar as transações');
        }

        const transactions = await response.json();
        countBadge.textContent = `${transactions.length} itens`;

        if(transactions.length === 0) {
            tbody.innerHTML = `
            <tr>
                <td colspan="7" class="empty-state">Nenhuma transação encontrada.</td>
            </tr>
            `;
            return;
        }

        // Transforma cada transação em uma linha da tabela
        tbody.innerHTML = transactions.map(transaction => {
            const isIncome = transaction.transactionType === 'INCOME';
            const amountClass = isIncome ? 'amount-income' : 'amount-expense';
            const typeTag = isIncome
                ? '<span class="tag tag-income">Receita</span>'
                : '<span class="tag tag-expense">Despesa</span>';
            
            const institutionBadge = transaction.institution
                ? `<span class="tag tag-bank">${transaction.institution}</span>`
                : '-';
            
            // Monta as opções <option> do select com a categoria atual selecionada
            const categoryOptions = categories.map(category => `
                <option value="${category.id}" ${transaction.category.id === category.id ? 'selected' : ''}>
                    ${category.name}
                </option>
            `).join('');

            return `
                <tr>
                    <td>${formatDate(transaction.date)}</td>
                    <td>${transaction.description}</td>
                    <td>${institutionBadge}</td>
                    <td>
                        <select class="category-select" onchange="updateTransactionCategory(${transaction.id}, this.value)">
                            ${categoryOptions}
                        </select>
                    </td>
                    <td>${typeTag}</td>
                    <td class="text-right ${amountClass}">${formatCurrency(transaction.amount)}</td>
                    <td class="text-center">
                        <button class="btn-delete" onclick="deleteTransaction(${transaction.id})">Excluir</button>
                    </td>
                </tr>
            `;
        }).join('');
    } catch(error) {
        console.error('Falha ao carregar as transações:', error);
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="empty-state">Erro ao carregar as transações.</td>
            </tr>
        `;
    }
}

// Busca os dados dos relatórios e desenha os 2 gráficos (Rosca e Barras)
async function fetchCharts() {
    const query = buildFilterQuery();

    try {
        //1. Gráfico de Rosca: Despesas por Categoria (/transactions/by-category)
        const categoryResponse = await fetch(`/transactions/by-category${query}`);
        if(categoryResponse.ok) {
            const categoryData = await categoryResponse.json();
            const ctxCategory = document.getElementById('category-chart').getContext('2d');

            if(categoryChartInstance) {
                categoryChartInstance.destroy();
            }

            categoryChartInstance = new Chart(ctxCategory, {
                type: 'doughnut',
                data: {
                    labels: categoryData.map(item => `${item.categoryName} (${item.percentage}%)`),
                    datasets: [{
                        data: categoryData.map(item => item.totalAmount),
                        backgroundColor: [
                            '#3b82f6', '#ef4444', '#10b981', '#f59e0b',
                            '#8b5cf6', '#ec4899', '#14b8a6', '#64748b'
                        ]
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { position: 'right' }
                    }
                }
            });
        }

        // 2. Gráfico de Barras: Evolução Mensal (/transactions/monthly-evolution)
        const evolutionResponse = await fetch(`/transactions/monthly-evolution${query}`);
        if(evolutionResponse.ok) {
            const evolutionData = await evolutionResponse.json();
            const ctxEvolution = document.getElementById('evolution-chart').getContext('2d');

            if(evolutionChartInstance) {
                evolutionChartInstance.destroy();
            }

            evolutionChartInstance = new Chart(ctxEvolution, {
                type: 'bar',
                data: {
                    labels: evolutionData.map(item => `${String(item.month).padStart(2, '0')}/${item.year}`),
                    datasets: [
                        {
                            label: 'Receitas',
                            data: evolutionData.map(item => item.totalIncome),
                            backgroundColor: '#10b981'
                        },
                        {
                            label: 'Despesas',
                            data: evolutionData.map(item => item.totalExpense),
                            backgroundColor: '#ef4444'
                        }
                    ]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: { beginAtZero: true }
                    }
                }
            });
        }
        
    } catch(error) {
            console.error('Erro ao carregar os gráficos:', error);
    }
}

// Busca a projeção de parcelas dos próximos 6 meses (/transactions/installments-projection)
async function fetchInstallmentsProjection() {
    const container = document.getElementById('installments-projection');
    const institution = document.getElementById('filter-institution').value.trim();

    const params = new URLSearchParams({ months: '6' });
    if(institution) {
        params.append('institution', institution);
    }

    try {
        const response = await fetch(`/transactions/installments/projection?${params.toString()}`);
        if(!response.ok) {
            throw new Error('Erro ao buscar a projeção de parcelas');
        }

        const monthsData = await response.json();
        if(monthsData.length === 0) {
            container.innerHTML = '<p class="empty-state">Nenhuma parcela futura em aberto.</p>';
            return;
        }

        container.innerHTML = monthsData.map(month => {
            const monthLabel = `${String(month.month).padStart(2, '0')}/${month.year}`;
            const itemsHtml = month.items.map(item => `
                <li class="projection-item">
                    <span class="projection-item-description" title="${item.description}">${item.description}</span>
                    <span class="projection-item-amount">${formatCurrency(item.amount)}</span>
                </li>
            `).join('');

            return `
                <div class="projection-card">
                    <div class="projection-header">
                        <span class="projection-month">${monthLabel}</span>
                        <span class="projection-total">${formatCurrency(month.totalCommitted)}</span>
                    </div>
                    <ul class="projection-list">
                        ${itemsHtml}
                    </ul>
                </div>
            `;
        }).join('');
    } catch(error) {
        console.error('Erro ao carregar projeção de parcelas:', error);
        container.innerHTML = '<p class="empty-state">Erro ao carregar projeção de parcelas.</p>';
    }
}

// Atualiza a categoria via PATCH /transactions/{id}
async function updateTransactionCategory(transactionId, newCategoryId) {
    try {
        const response = await fetch(`/transactions/${transactionId}`, {
            method: 'PATCH',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ categoryId: Number(newCategoryId) })
        });

        if(response.ok) {
            await fetchTransactions();
            await fetchCharts();
            await fetchInstallmentsProjection();
        }
    } catch(error) {
        console.error('Falha ao atualizar a categoria:', error);
    }
}

// Exclui uma transação via DELETE /transactions/{id}
async function deleteTransaction(transactionId) {
    if(!confirm('Tem certeza que deseja excluir esta transação?')) return;

    try {
        const response = await fetch(`/transactions/${transactionId}`, {
            method: 'DELETE'
        });

        if(response.ok) {
            await fetchBalance();
            await fetchTransactions();
            await fetchCharts();
            await fetchInstallmentsProjection();
        }
    } catch(error) {
        console.error('Falha ao excluir a transação:', error);
    }
}

// Importa um arquivo usando FormData
async function handleImportFile(event) {
    event.preventDefault();
    const fileInput = document.getElementById('import-file');
    const institution = document.getElementById('import-institution').value.trim();
    const creditCard = document.getElementById('import-credit-card').checked;
    const feedback = document.getElementById('import-feedback');
    const btnImport = document.getElementById('btn-import');

    if(!fileInput.files.length) return;
    const file = fileInput.files[0];
    const fileName = file.name.toLowerCase();

    // Descobre qual endpoint chamar pela extensão do arquivo
    let endpoint = '';
    if(fileName.endsWith('.csv')) {
        endpoint = '/transactions/import/csv';
    } else if(fileName.endsWith('.pdf')) {
        endpoint = '/transactions/import/pdf';
    } else if(fileName.endsWith('.ofx')) {
        endpoint = '/transactions/import/ofx';
    } else {
        feedback.textContent = 'Formato de arquivo não suportado. Use CSV, PDF ou OFX.';
        feedback.className = 'feedback-msg feedback-error';
        return;
    }

    // Monta os queryParams (?creditCard=true&institution=...)
    const params = new URLSearchParams();
    if(fileName.endsWith('.csv') && creditCard) {
        params.append('creditCard', 'true');
    }
    if(institution) {
        params.append('institution', institution);
    }
    const query = params.toString() ? `?${params.toString()}` : '';

    // Monta o multipart/form-data com o campo "file"
    const formData = new FormData();
    formData.append('file', file);

    try {
        btnImport.disabled = true;
        feedback.textContent = 'Importando arquivo...';
        feedback.className = 'feedback-msg';

        const response = await fetch(`${endpoint}${query}`, {
            method: 'POST',
            body: formData
        });
        
        if(!response.ok) {
            throw new Error('Falha ao importar o arquivo');
        }

        const savedTransactions = await response.json();
        feedback.textContent = `Sucesso! ${savedTransactions.length} novas transações importadas.`;
        feedback.className = 'feedback-msg feedback-success';

        document.getElementById('import-form').reset();
        await fetchCategories();
        await fetchBalance();
        await fetchTransactions();
        await fetchCharts();
        await fetchInstallmentsProjection();
    } catch(error) {
        console.error('Erro na importação:', error);
        feedback.textContent = 'Erro ao importar o arquivo. Verifique o formato.';
        feedback.className = 'feedback-msg feedback-error';
    } finally {
        btnImport.disabled = false;
    }
}

// Cria uma nova ransação manual (POST /transactions)
async function handleCreateTransaction(event) {
    event.preventDefault();

    const payload = {
        description: document.getElementById('tx-description').value.trim(),
        amount: Number(document.getElementById('tx-amount').value),
        date: document.getElementById('tx-date').value,
        transactionType: document.getElementById('tx-type').value,
        categoryId: Number(document.getElementById('tx-category').value),
        institution: document.getElementById('tx-institution').value.trim() || null
    };

    try {
        const response = await fetch('/transactions', {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify(payload)
        });
    
        if(response.ok) {
            document.getElementById('create-transaction-form').reset();
            await fetchBalance();
            await fetchTransactions();
            await fetchCharts();
            await fetchInstallmentsProjection();
        }
    } catch(error) {
        console.error('Erro ao criar transação:', error);
    }
}

// Cria uma nova categoria (POST /categories)
async function handleCreateCategory() {
    const name = prompt('Digite o nome da nova categoria:');
    if(!name || !name.trim()) return;

    try {
        const response = await fetch('/categories', {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ name: name.trim() })
        });

        if(response.ok) {
            await fetchCategories();
            await fetchTransactions();
            await fetchCharts();
            await fetchInstallmentsProjection();
        } else {
            const err = await response.json();
            alert(err.message || 'Erro ao criar categoria');
        }
    } catch(error) {
        console.error('Erro ao criar categoria:', error);
    }
}

// Quando a página terminar de carregar o HTML, chama fetchBalance()
document.addEventListener('DOMContentLoaded', async () => {
    await fetchCategories();
    await fetchBalance();
    await fetchTransactions();
    await fetchCharts();
    await fetchInstallmentsProjection();

    // Quando enviar o formulário de filtros, recarrega o resumo e tabela sem recarregar a página
    document.getElementById('filter-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        await fetchBalance();
        await fetchTransactions();
        await fetchCharts();
        await fetchInstallmentsProjection();
    });

    // Botão de limpar filtros
    document.getElementById('btn-clear-filters').addEventListener('click', async () => {
        document.getElementById('filter-form').reset();
        await fetchBalance();
        await fetchTransactions();
        await fetchCharts();
        await fetchInstallmentsProjection();
    });

    document.getElementById('import-form').addEventListener('submit', handleImportFile);
    document.getElementById('create-transaction-form').addEventListener('submit', handleCreateTransaction);
    document.getElementById('btn-new-category').addEventListener('click', handleCreateCategory);
});