// Menu structure based on the application - loaded from localStorage or use default
const defaultMenuStructure = {
    atencao_primaria: {
        name: "Atenção Primária",
        menus: {
            acolhimento: "Acolhimento",
            conduta_esus: "Conduta e-SUS",
            configuracoes_laudos: "Configurações de Laudos",
            consulta_medica: "Consulta Médica",
            consulta_nivel_superior: "Consulta Nível Superior",
            consulta_odontologica: "Consulta Odontológica",
            escuta_inicial: "Escuta Inicial",
            fichas_esus: "Fichas e-SUS",
            locais_atendimento: "Locais de Atendimento",
            prontuario_paciente: "Prontuário do Paciente",
            registro_producao: "Registro de Produção Ambulatorial"
        }
    },
    internacao: {
        name: "Internação",
        menus: {
            admissao: "Admissão",
            evolucao: "Evolução",
            alta: "Alta",
            transferencia: "Transferência",
            prescricao_enfermagem: "Prescrição de Enfermagem"
        }
    },
    prescricao: {
        name: "Prescrição Médica",
        menus: {
            nova_prescricao: "Nova Prescrição",
            prescricoes_ativas: "Prescrições Ativas",
            historico: "Histórico de Prescrições"
        }
    },
    faturamento: {
        name: "Faturamento / TISS",
        menus: {
            guias_tiss: "Guias TISS",
            lote_faturamento: "Lote de Faturamento",
            glosas: "Glosas",
            recursos: "Recursos"
        }
    },
    farmacia: {
        name: "Farmácia Hospitalar",
        menus: {
            dispensacao: "Dispensação",
            estoque: "Estoque",
            solicitacao_compra: "Solicitação de Compra",
            controle_validade: "Controle de Validade"
        }
    },
    laboratorio: {
        name: "Laboratório",
        menus: {
            solicitacao_exames: "Solicitação de Exames",
            resultados: "Resultados",
            laudos: "Laudos"
        }
    },
    cadastro_paciente: {
        name: "Cadastro de Paciente",
        menus: {
            novo_paciente: "Novo Paciente",
            buscar_paciente: "Buscar Paciente",
            historico: "Histórico"
        }
    }
};

// Load menu structure from localStorage or use default
let menuStructure = JSON.parse(localStorage.getItem('qaMenuStructure')) || defaultMenuStructure;

// Saved tests storage
let savedTests = JSON.parse(localStorage.getItem('qaTests') || '[]');
let editingTestId = null; // Track if we're editing a test

// Current view state persistence
const VIEW_STATE_KEY = 'qaCurrentView';
let currentViewState = {
    view: 'dashboard', // dashboard, testList, newTest, menuEditor, config
    moduleKey: null,
    menuKey: null
};

// View state persistence functions
function saveViewState(view, moduleKey = null, menuKey = null) {
    currentViewState = {
        view,
        moduleKey,
        menuKey,
        timestamp: new Date().toISOString()
    };
    localStorage.setItem(VIEW_STATE_KEY, JSON.stringify(currentViewState));
    console.log('View state saved:', currentViewState);
}

// Data persistence to disk functions
async function saveToDisk(key, value) {
    try {
        const response = await fetch('http://localhost:8080/api/save-data', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ key, value })
        });
        const result = await response.json();
        console.log('Data saved to disk:', key, result);
    } catch (error) {
        console.error('Error saving to disk:', error);
    }
}

async function loadFromDisk(key) {
    try {
        const response = await fetch(`http://localhost:8080/api/load-data/${key}`);
        if (response.ok) {
            const value = await response.text();
            console.log('Data loaded from disk:', key);
            return value;
        }
        return null;
    } catch (error) {
        console.error('Error loading from disk:', error);
        return null;
    }
}

// Sync localStorage to disk
async function syncToDisk() {
    await saveToDisk('qaTests', JSON.stringify(savedTests));
    await saveToDisk('qaMenuStructure', JSON.stringify(menuStructure));
    await saveToDisk('qaAgentConfig', localStorage.getItem('qaAgentConfig'));
    console.log('All data synced to disk');
}

// Load data from disk on startup
async function loadFromDiskOnStartup() {
    try {
        const testsData = await loadFromDisk('qaTests');
        if (testsData) {
            savedTests = JSON.parse(testsData);
            localStorage.setItem('qaTests', testsData);
            console.log('Tests loaded from disk');
        }

        const menuData = await loadFromDisk('qaMenuStructure');
        if (menuData) {
            menuStructure = JSON.parse(menuData);
            localStorage.setItem('qaMenuStructure', menuData);
            console.log('Menu structure loaded from disk');
        }

        const configData = await loadFromDisk('qaAgentConfig');
        if (configData) {
            localStorage.setItem('qaAgentConfig', configData);
            console.log('Config loaded from disk');
        }
    } catch (error) {
        console.error('Error loading from disk on startup:', error);
    }
}

// Override localStorage.setItem to also save to disk
const originalSetItem = localStorage.setItem;
localStorage.setItem = function(key, value) {
    originalSetItem.call(this, key, value);
    // Auto-sync important keys to disk
    if (key === 'qaTests' || key === 'qaMenuStructure' || key === 'qaAgentConfig') {
        saveToDisk(key, value);
    }
};

function restoreViewState() {
    try {
        const saved = localStorage.getItem(VIEW_STATE_KEY);
        if (saved) {
            const state = JSON.parse(saved);
            console.log('Restoring view state:', state);
            
            // Only restore if it's from the last 30 minutes to avoid stale state
            const savedTime = new Date(state.timestamp);
            const now = new Date();
            const diffMinutes = (now - savedTime) / (1000 * 60);
            
            if (diffMinutes < 30) {
                currentViewState = state;
                return state;
            }
        }
    } catch (error) {
        console.error('Error restoring view state:', error);
    }
    
    // Default to dashboard
    currentViewState = {
        view: 'dashboard',
        moduleKey: null,
        menuKey: null
    };
    return currentViewState;
}

function clearViewState() {
    localStorage.removeItem(VIEW_STATE_KEY);
    currentViewState = {
        view: 'dashboard',
        moduleKey: null,
        menuKey: null
    };
}

// Custom Modal Functions
function showModal(title, message, type = 'success', confirmCallback = null, showCancel = false) {
    const modal = document.getElementById('customModal');
    const modalIcon = document.getElementById('modalIcon');
    const modalTitle = document.getElementById('modalTitle');
    const modalMessage = document.getElementById('modalMessage');
    const modalConfirm = document.getElementById('modalConfirm');
    const modalCancel = document.getElementById('modalCancel');
    
    // Set content
    modalTitle.textContent = title;
    modalMessage.textContent = message;
    
    // Set icon and colors based on type
    let iconHtml, iconClass;
    switch(type) {
        case 'success':
            iconHtml = '<i class="fas fa-check" style="font-size: 20px;"></i>';
            iconClass = 'success';
            modalConfirm.className = 'btn-primary';
            break;
        case 'error':
            iconHtml = '<i class="fas fa-times" style="font-size: 20px;"></i>';
            iconClass = 'error';
            modalConfirm.className = 'btn-primary';
            break;
        case 'warning':
            iconHtml = '<i class="fas fa-exclamation" style="font-size: 20px;"></i>';
            iconClass = 'warning';
            modalConfirm.className = 'btn-primary';
            break;
        case 'info':
            iconHtml = '<i class="fas fa-info" style="font-size: 20px;"></i>';
            iconClass = 'info';
            modalConfirm.className = 'btn-primary';
            break;
        default:
            iconHtml = '<i class="fas fa-check" style="font-size: 20px;"></i>';
            iconClass = 'success';
            modalConfirm.className = 'btn-primary';
    }

    modalIcon.innerHTML = iconHtml;
    modalIcon.className = `modal-icon ${iconClass}`;
    
    // Show/hide cancel button
    if (showCancel) {
        modalCancel.classList.remove('hidden');
    } else {
        modalCancel.classList.add('hidden');
    }
    
    // Set up confirm button callback
    modalConfirm.onclick = () => {
        modal.classList.add('hidden');
        if (confirmCallback) {
            confirmCallback();
        }
    };
    
    // Set up cancel button callback
    modalCancel.onclick = () => {
        modal.classList.add('hidden');
    };
    
    // Show modal
    modal.classList.remove('hidden');
}

// Replace alert with custom modal
function showAlert(message, type = 'info', callback = null) {
    const titles = {
        'success': 'Sucesso!',
        'error': 'Erro!',
        'warning': 'Atenção!',
        'info': 'Informação'
    };
    
    showModal(titles[type] || 'Informação', message, type, callback);
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    // Load data from disk on startup
    loadFromDiskOnStartup().then(() => {
        loadModulesList();
        
        // Restore previous view state
        const state = restoreViewState();
        console.log('Initializing with state:', state);
    
        switch (state.view) {
            case 'testList':
                if (state.moduleKey && state.menuKey) {
                    showModuleTests(state.moduleKey, state.menuKey);
                } else {
                    showAllTests();
                }
                break;
            case 'newTest':
                showNewTestForm();
                break;
            case 'menuEditor':
                showMenuEditor();
                break;
            case 'config':
                showConfigForm();
                break;
            default:
                showDashboard();
        }
    });
});

// Show dashboard
function showDashboard() {
    hideAllViews();
    document.getElementById('dashboardView').classList.remove('hidden');
    
    updatePageTitle('Dashboard', 'Visão geral dos testes automatizados');
    updateActiveNav('nav-dashboard');
    
    saveViewState('dashboard');
    
    // Update dashboard data
    updateDashboardStats();
    loadRecentTests();
    loadTestDistribution();
}

// Update dashboard statistics
function updateDashboardStats() {
    const totalTests = savedTests.length;
    const generatedTests = savedTests.filter(test => test.status === 'generated' || test.status === 'generated_draft').length;
    const draftTests = savedTests.filter(test => test.status === 'draft').length;
    const totalModules = Object.keys(menuStructure).length;
    
    // Safely update elements with null checks
    const totalTestsEl = document.getElementById('totalTests');
    if (totalTestsEl) totalTestsEl.textContent = totalTests;
    
    const generatedTestsEl = document.getElementById('generatedTests');
    if (generatedTestsEl) generatedTestsEl.textContent = generatedTests;
    
    const draftTestsEl = document.getElementById('draftTests');
    if (draftTestsEl) draftTestsEl.textContent = draftTests;
    
    const totalModulesEl = document.getElementById('totalModules');
    if (totalModulesEl) totalModulesEl.textContent = totalModules;
}

// Load recent tests
function loadRecentTests() {
    const recentTestsList = document.getElementById('recentTestsList');
    if (!recentTestsList) return;
    
    const recentTests = savedTests
        .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
        .slice(0, 5);
    
    if (recentTests.length === 0) {
        recentTestsList.innerHTML = '<div class="empty-state" style="padding: 24px;"><div class="empty-state-sub">Nenhum teste encontrado</div></div>';
        return;
    }

    recentTestsList.innerHTML = `
        <div class="test-list">
            <div class="test-list-header">
                <span class="test-list-title">Testes Recentes</span>
                <span class="test-list-action" onclick="showTestList()">Ver todos</span>
            </div>
            ${recentTests.map(test => `
                <div class="test-row">
                    <div class="test-row-icon">
                        <i class="fas fa-vial"></i>
                    </div>
                    <div class="test-row-body">
                        <div class="test-row-title">${test.name}</div>
                        <div class="test-row-preview">${test.module || 'Sem módulo'} › ${test.menu || 'Sem menu'}</div>
                        <div class="test-row-badges">
                            ${getStatusBadge(test.status)}
                            ${getPriorityBadge(test.priority)}
                            ${getTypeBadge(test.testType)}
                        </div>
                    </div>
                    <div class="test-row-meta">${menuStructure[test.module]?.name || test.module}</div>
                    <div class="test-row-actions">
                        <button onclick="editTest('${test.id}')" class="btn-icon" title="Editar">
                            <i class="fas fa-edit"></i>
                        </button>
                        <button onclick="runSingleTest('${test.id}')" class="btn-icon run" title="Executar">
                            <i class="fas fa-play"></i>
                        </button>
                    </div>
                </div>
            `).join('')}
        </div>
    `;
}

// Load test distribution
function loadTestDistribution() {
    // Type distribution
    const typeDistribution = document.getElementById('typeDistribution');
    if (!typeDistribution) return;
    
    const typeCounts = {};
    savedTests.forEach(test => {
        typeCounts[test.testType] = (typeCounts[test.testType] || 0) + 1;
    });
    
    const typeColors = {
        'smoke': 'var(--green-text)',
        'regression': 'var(--blue-400)',
        'critico': 'var(--red-text)'
    };

    typeDistribution.innerHTML = Object.entries(typeCounts).map(([type, count]) => {
        const percentage = Math.round((count / savedTests.length) * 100);
        return `
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                <div style="display: flex; align-items: center; gap: 12px;">
                    <div style="width: 16px; height: 16px; background: ${typeColors[type] || 'var(--text-muted)'}; border-radius: var(--radius-sm);"></div>
                    <span style="font-size: 13px; font-weight: 500; color: var(--text-primary); text-transform: capitalize;">${type}</span>
                </div>
                <div style="display: flex; align-items: center; gap: 8px;">
                    <span style="font-size: 13px; color: var(--text-secondary);">${count}</span>
                    <span style="font-size: 11px; color: var(--text-muted);">(${percentage}%)</span>
                </div>
            </div>
        `;
    }).join('') || '<div class="empty-state" style="padding: 24px;"><div class="empty-state-sub">Nenhum teste encontrado</div></div>';
    
    // Priority distribution
    const priorityDistribution = document.getElementById('priorityDistribution');
    const priorityCounts = {};
    savedTests.forEach(test => {
        priorityCounts[test.priority] = (priorityCounts[test.priority] || 0) + 1;
    });
    
    const priorityColors = {
        'Crítica': 'var(--red-text)',
        'Alta': 'var(--amber-text)',
        'Média': 'var(--amber-text)',
        'Baixa': 'var(--green-text)'
    };

    priorityDistribution.innerHTML = Object.entries(priorityCounts).map(([priority, count]) => {
        const percentage = Math.round((count / savedTests.length) * 100);
        return `
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                <div style="display: flex; align-items: center; gap: 12px;">
                    <div style="width: 16px; height: 16px; background: ${priorityColors[priority] || 'var(--text-muted)'}; border-radius: var(--radius-sm);"></div>
                    <span style="font-size: 13px; font-weight: 500; color: var(--text-primary);">${priority}</span>
                </div>
                <div style="display: flex; align-items: center; gap: 8px;">
                    <span style="font-size: 13px; color: var(--text-secondary);">${count}</span>
                    <span style="font-size: 11px; color: var(--text-muted);">(${percentage}%)</span>
                </div>
            </div>
        `;
    }).join('') || '<div class="empty-state" style="padding: 24px;"><div class="empty-state-sub">Nenhum teste encontrado</div></div>';
}

// Get status color
function getStatusColor(status) {
    switch(status) {
        case 'generated': return 'var(--green-text)';
        case 'draft': return 'var(--amber-text)';
        case 'generated_draft': return 'var(--blue-500)';
        default: return 'var(--text-muted)';
    }
}

// Get status text
function getStatusText(status) {
    switch(status) {
        case 'generated': return 'Gerado';
        case 'draft': return 'Rascunho';
        case 'generated_draft': return 'Rascunho Gerado';
        default: return 'Desconhecido';
    }
}

// Show all tests
function showAllTests() {
    // This would show a comprehensive test list view
    // For now, we'll just show the first module's tests
    const firstModule = Object.keys(menuStructure)[0];
    if (firstModule) {
        const firstMenu = Object.keys(menuStructure[firstModule].menus)[0];
        if (firstMenu) {
            showModuleTests(firstModule, firstMenu);
        }
    }
}

// Show drafts
function showDrafts() {
    // Filter and show only draft tests
    const draftTests = savedTests.filter(t => t.status === 'draft');
    if (draftTests.length === 0) {
        showAlert('Nenhum rascunho encontrado', 'info');
        return;
    }
    
    // Show first draft's module
    const firstDraft = draftTests[0];
    if (firstDraft.module && firstDraft.menu) {
        showModuleTests(firstDraft.module, firstDraft.menu);
    }
}

// Show test list view
function showTestList() {
    hideAllViews();
    document.getElementById('testListView').classList.remove('hidden');
    updatePageTitle('Testes', 'Lista completa de testes automatizados');
    updateActiveNav('nav-tests');
    
    // Load all tests into the list
    const testList = document.getElementById('testList');
    if (testList) {
        renderTestList(testList, savedTests);
    }
    
    saveViewState('testList');
}

// Show executions view
function showExecutions() {
    hideAllViews();
    document.getElementById('executionsView').classList.remove('hidden');
    updatePageTitle('Execuções', 'Histórico de execuções de testes');
    updateActiveNav('nav-executions');
    saveViewState('executions');
    loadExecutionsHistory();
}

// Save execution to history
function saveExecution(execution) {
    const executions = JSON.parse(localStorage.getItem('qaAgentExecutions') || '[]');
    executions.push(execution);
    // Keep only last 50 executions
    if (executions.length > 50) {
        executions.shift();
    }
    localStorage.setItem('qaAgentExecutions', JSON.stringify(executions));
}

// Load executions history
function loadExecutionsHistory() {
    const container = document.getElementById('executionsList');
    if (!container) return;

    // Mock execution history for now - in production this would come from backend
    const executions = JSON.parse(localStorage.getItem('qaAgentExecutions') || '[]');

    if (executions.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">
                    <i class="fas fa-play-circle"></i>
                </div>
                <div class="empty-state-title">Nenhuma execução registrada ainda</div>
                <div class="empty-state-sub">Execute testes para ver o histórico aqui</div>
            </div>
        `;
        return;
    }

    // Create executions list
    const list = document.createElement('div');
    list.className = 'test-list';
    list.innerHTML = `
        <div class="test-list-header">
            <span class="test-list-title">Histórico de Execuções</span>
        </div>
    `;

    executions.slice().reverse().forEach(exec => {
        const row = document.createElement('div');
        row.className = 'test-row';
        row.innerHTML = `
            <div class="test-row-icon">
                <i class="fas fa-play"></i>
            </div>
            <div class="test-row-body">
                <div class="test-row-title">${exec.testName || 'Teste sem nome'}</div>
                <div class="test-row-preview">${exec.module || 'Sem módulo'} › ${exec.menu || 'Sem menu'}</div>
                <div class="test-row-badges">
                    <span class="badge ${exec.status === 'success' ? 'badge-passou' : exec.status === 'failed' ? 'badge-falhou' : 'badge-executando'}">
                        ${exec.status === 'success' ? 'Sucesso' : exec.status === 'failed' ? 'Falhou' : 'Executando'}
                    </span>
                </div>
            </div>
            <div class="test-row-meta">${new Date(exec.timestamp).toLocaleString('pt-BR')}</div>
        `;
        list.appendChild(row);
    });

    container.innerHTML = '';
    container.appendChild(list);
}

// Show generated tests
function showGeneratedTests() {
    const generated = savedTests.filter(t => t.status === 'generated' || t.status === 'completed');
    if (generated.length === 0) {
        showAlert('Nenhum teste gerado encontrado', 'info');
        return;
    }
    showTestList();
}

// Toggle collapsible section
function toggleSection(sectionId, header) {
    const section = document.getElementById(sectionId);
    const chevron = header.querySelector('.chevron');
    
    if (section.classList.contains('open')) {
        section.classList.remove('open');
        chevron.classList.remove('rotate');
    } else {
        section.classList.add('open');
        chevron.classList.add('rotate');
    }
}

// Update active navigation item
function updateActiveNav(navId) {
    document.querySelectorAll('.nav-item').forEach(item => {
        item.classList.remove('active');
    });
    const activeItem = document.getElementById(navId);
    if (activeItem) {
        activeItem.classList.add('active');
    }
}

// Update page title
function updatePageTitle(title, subtitle) {
    const pageTitle = document.getElementById('pageTitle');
    const pageSubtitle = document.getElementById('pageSubtitle');
    if (pageTitle) pageTitle.textContent = title;
    if (pageSubtitle) pageSubtitle.textContent = subtitle;
}

// Hide all views helper
function hideAllViews() {
    const views = ['dashboardView', 'testListView', 'executionsView', 'newTestForm', 'menuEditor', 'configForm'];
    views.forEach(viewId => {
        const view = document.getElementById(viewId);
        if (view) view.classList.add('hidden');
    });
}

// Render test list helper
function renderTestList(container, tests) {
    container.innerHTML = '';

    if (tests.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon"><i class="fas fa-inbox"></i></div>
                <div class="empty-state-title">Nenhum teste encontrado</div>
            </div>
        `;
        return;
    }

    const testList = document.createElement('div');
    testList.className = 'test-list';
    testList.innerHTML = `
        <div class="test-list-header">
            <span class="test-list-title">${tests.length} teste${tests.length !== 1 ? 's' : ''}</span>
        </div>
    `;

    tests.forEach(test => {
        const testRow = document.createElement('div');
        testRow.className = 'test-row';
        testRow.innerHTML = `
            <div class="test-row-icon">
                <i class="fas fa-vial"></i>
            </div>
            <div class="test-row-body">
                <div class="test-row-title">${test.name || 'Teste sem nome'}</div>
                <div class="test-row-preview">${test.module || 'Sem módulo'} › ${test.menu || 'Sem menu'}</div>
                <div class="test-row-badges">
                    ${getStatusBadge(test.status)}
                    ${getPriorityBadge(test.priority)}
                    ${getTypeBadge(test.testType)}
                </div>
            </div>
            <div class="test-row-meta"></div>
            <div class="test-row-actions">
                <button onclick="runSingleTest('${test.id}')" class="btn-icon run" title="Executar">
                    <i class="fas fa-play"></i>
                </button>
                <button onclick="editTest('${test.id}')" class="btn-icon" title="Editar">
                    <i class="fas fa-edit"></i>
                </button>
                <button onclick="deleteTest('${test.id}')" class="btn-icon danger" title="Excluir">
                    <i class="fas fa-trash"></i>
                </button>
            </div>
        `;
        testList.appendChild(testRow);
    });

    container.appendChild(testList);
}

// Load modules in sidebar
function loadModulesList() {
    // Reload menu structure from localStorage to get latest changes
    menuStructure = JSON.parse(localStorage.getItem('qaMenuStructure')) || defaultMenuStructure;
    
    console.log('Loading modules:', Object.keys(menuStructure));
    console.log('Menu structure:', menuStructure);
    
    const modulesList = document.getElementById('modulesList');
    if (!modulesList) {
        console.error('modulesList element not found!');
        return;
    }
    
    modulesList.innerHTML = '';
    
    Object.keys(menuStructure).forEach(moduleKey => {
        const module = menuStructure[moduleKey];
        console.log('Creating module:', moduleKey, module.name);
        
        const moduleDiv = document.createElement('div');
        moduleDiv.className = 'sidebar-module';
        moduleDiv.innerHTML = `
            <div class="sidebar-module-header" onclick="toggleModule('${moduleKey}')">
                <span>${module.name}</span>
                <i class="fas fa-chevron-down chevron" id="chevron-${moduleKey}"></i>
            </div>
            <div id="menus-${moduleKey}" class="hidden">
                ${Object.keys(module.menus).map(menuKey => `
                    <button class="sidebar-module-item" onclick="showModuleTests('${moduleKey}', '${menuKey}')">
                        ${module.menus[menuKey]}
                    </button>
                `).join('')}
            </div>
        `;
        modulesList.appendChild(moduleDiv);
    });
    
    console.log('Modules loaded successfully');
}

// Toggle module expansion
function toggleModule(moduleKey) {
    console.log('Toggling module:', moduleKey);
    const menusDiv = document.getElementById(`menus-${moduleKey}`);
    const chevron = document.getElementById(`chevron-${moduleKey}`);
    
    console.log('Found elements:', menusDiv, chevron);
    
    if (menusDiv.classList.contains('hidden')) {
        menusDiv.classList.remove('hidden');
        chevron.classList.remove('fa-chevron-down');
        chevron.classList.add('fa-chevron-up');
        console.log('Module expanded');
    } else {
        menusDiv.classList.add('hidden');
        chevron.classList.remove('fa-chevron-up');
        chevron.classList.add('fa-chevron-down');
        console.log('Module collapsed');
    }
}

// Load menus based on selected module
function loadMenus() {
    console.log('Loading menus...');
    
    const moduleSelect = document.getElementById('moduleSelect');
    const menuSelect = document.getElementById('menuSelect');
    const submenuSelect = document.getElementById('submenuSelect');
    
    console.log('Found elements:', moduleSelect, menuSelect, submenuSelect);
    
    if (!moduleSelect || !menuSelect) {
        console.error('Required elements not found!');
        return;
    }
    
    const selectedModule = moduleSelect.value;
    console.log('Selected module:', selectedModule);
    
    menuSelect.innerHTML = '<option value="">Selecione um menu</option>';
    
    // Only handle submenuSelect if it exists
    if (submenuSelect) {
        submenuSelect.innerHTML = '<option value="">Selecione um submenu</option>';
        submenuSelect.disabled = true;
    }
    
    if (selectedModule && menuStructure[selectedModule]) {
        console.log('Loading menus for module:', selectedModule);
        console.log('Available menus:', menuStructure[selectedModule].menus);
        
        menuSelect.disabled = false;
        Object.keys(menuStructure[selectedModule].menus).forEach(menuKey => {
            const option = document.createElement('option');
            option.value = menuKey;
            option.textContent = menuStructure[selectedModule].menus[menuKey];
            menuSelect.appendChild(option);
            console.log('Added menu option:', menuKey, menuStructure[selectedModule].menus[menuKey]);
        });
        console.log('Menus loaded successfully');
    } else {
        console.log('No module selected or module not found');
        menuSelect.disabled = true;
    }
}

// Load submenus (placeholder for future use)
function loadSubmenus() {
    const menuSelect = document.getElementById('menuSelect');
    const submenuSelect = document.getElementById('submenuSelect');
    
    const selectedMenu = menuSelect.value;
    
    submenuSelect.innerHTML = '<option value="">Nenhum submenu</option>';
    submenuSelect.disabled = false;
}

// Show new test form
function showNewTestForm() {
    console.log('Showing new test form...');
    
    hideAllViews();
    document.getElementById('newTestForm').classList.remove('hidden');
    
    updatePageTitle('Novo Teste', editingTestId ? 'Editando teste existente' : 'Criar novo teste automatizado');
    updateActiveNav('');
    
    // Reset form if not in editing mode
    if (!editingTestId) {
        resetForm();
    }
    
    // Hide generated output if visible
    document.getElementById('generatedOutput').classList.add('hidden');
    window.currentGenerated = null;
    
    // Save view state
    saveViewState('newTest');
    
    // Update module select - ensure it's called after reset
    setTimeout(() => {
        updateModuleSelect();
        console.log('Module select updated in showNewTestForm');
    }, 100);
    
    console.log('New test form displayed');
}

// Get status badge HTML
function getStatusBadge(status) {
    const badges = {
        'draft': '<span class="badge badge-rascunho"><i class="fas fa-edit"></i>Rascunho</span>',
        'generated': '<span class="badge badge-pronto"><i class="fas fa-check"></i>Gerado</span>',
        'generated_draft': '<span class="badge badge-rascunho"><i class="fas fa-code"></i>Rascunho Gerado</span>',
        'running': '<span class="badge badge-executando"><i class="fas fa-play"></i>Executando</span>',
        'passed': '<span class="badge badge-passou"><i class="fas fa-check-circle"></i>Aprovado</span>',
        'failed': '<span class="badge badge-falhou"><i class="fas fa-times-circle"></i>Falhou</span>'
    };
    return badges[status] || '<span class="badge badge-media"><i class="fas fa-question"></i>Desconhecido</span>';
}

// Get priority badge HTML
function getPriorityBadge(priority) {
    const badges = {
        'Crítica': '<span class="badge badge-critico"><i class="fas fa-exclamation-triangle"></i>Crítica</span>',
        'Alta': '<span class="badge badge-alta"><i class="fas fa-arrow-up"></i>Alta</span>',
        'Média': '<span class="badge badge-media"><i class="fas fa-minus"></i>Média</span>',
        'Baixa': '<span class="badge badge-baixa"><i class="fas fa-arrow-down"></i>Baixa</span>'
    };
    return badges[priority] || '<span class="badge badge-media"><i class="fas fa-minus"></i>Média</span>';
}

// Get type badge HTML
function getTypeBadge(type) {
    const badges = {
        'smoke': '<span class="badge badge-smoke"><i class="fas fa-bolt"></i>Smoke</span>',
        'regression': '<span class="badge badge-regression"><i class="fas fa-undo"></i>Regressão</span>',
        'functional': '<span class="badge badge-funcional"><i class="fas fa-cogs"></i>Funcional</span>',
        'integration': '<span class="badge badge-api"><i class="fas fa-link"></i>Integração</span>'
    };
    return badges[type] || '<span class="badge badge-funcional"><i class="fas fa-cog"></i>Outro</span>';
}

// Delete test
function deleteTest(testId) {
    if (!confirm('Tem certeza que deseja excluir este teste?')) {
        return;
    }
    
    savedTests = savedTests.filter(test => test.id !== testId);
    localStorage.setItem('qaTests', JSON.stringify(savedTests));
    
    // Reload the current view
    const currentModule = document.querySelector('[onclick*="showModuleTests"]').getAttribute('onclick').match(/'([^']+)'/)[1];
    const currentMenu = document.querySelector('[onclick*="showModuleTests"]').getAttribute('onclick').match(/'([^']+)'/)[3];
    showModuleTests(currentModule, currentMenu);
}

// Show module tests
function showModuleTests(moduleKey, menuKey) {
    // Hide all views first
    document.getElementById('dashboardView').classList.add('hidden');
    document.getElementById('newTestForm').classList.add('hidden');
    document.getElementById('menuEditorView').classList.add('hidden');
    document.getElementById('configView').classList.add('hidden');
    document.getElementById('testListView').classList.remove('hidden');
    
    // Update page title
    document.getElementById('pageTitle').textContent = menuStructure[moduleKey].menus[menuKey];
    document.getElementById('pageSubtitle').textContent = `Testes do menu ${menuStructure[moduleKey].menus[menuKey]}`;
    
    // Save view state
    saveViewState('testList', moduleKey, menuKey);
    
    // Load tests for this module and menu
    const testList = document.getElementById('testList');
    if (!testList) {
        console.error('testList element not found');
        return;
    }
    
    // Clear existing content
    testList.innerHTML = '';
    
    // Reload tests from localStorage to get fresh data
    savedTests = JSON.parse(localStorage.getItem('qaTests') || '[]');
    
    const filteredTests = savedTests.filter(test => test.module === moduleKey && test.menu === menuKey);
    console.log('Filtered tests for', moduleKey, menuKey, ':', filteredTests);
    console.log('All saved tests:', savedTests);
    
    if (filteredTests.length === 0) {
        testList.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">
                    <i class="fas fa-clipboard-list"></i>
                </div>
                <div class="empty-state-title">Nenhum teste encontrado</div>
                <div class="empty-state-sub">Não há testes cadastrados para este menu.</div>
                <button onclick="showNewTestForm()" class="btn-primary" style="margin-top: 16px;">
                    <i class="fas fa-plus"></i>
                    <span>Criar Primeiro Teste</span>
                </button>
            </div>
        `;
        return;
    }
    
    // Create header with execution buttons
    const headerDiv = document.createElement('div');
    headerDiv.style.cssText = 'margin-bottom: 16px; display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px;';
    headerDiv.innerHTML = `
        <div style="display: flex; align-items: center; gap: 8px;">
            <span style="font-size: 13px; color: var(--text-muted);">${filteredTests.length} teste${filteredTests.length !== 1 ? 's' : ''}</span>
        </div>
        <div style="display: flex; align-items: center; gap: 8px;">
            <button onclick="runMenuTests('${moduleKey}', '${menuKey}')" class="btn-run" title="Executar todos os testes deste menu">
                <i class="fas fa-play"></i>
                <span>Executar Menu</span>
            </button>
            <button onclick="runModuleTests('${moduleKey}')" class="btn-secondary" title="Executar todos os testes do módulo">
                <i class="fas fa-layer-group"></i>
                <span>Executar Módulo</span>
            </button>
        </div>
    `;
    testList.appendChild(headerDiv);

    // Create test list
    const testListEl = document.createElement('div');
    testListEl.className = 'test-list';
    testListEl.innerHTML = `
        <div class="test-list-header">
            <span class="test-list-title">${menuStructure[moduleKey].menus[menuKey]}</span>
        </div>
    `;

    filteredTests.forEach(test => {
        const testRow = document.createElement('div');
        testRow.className = 'test-row';
        testRow.innerHTML = `
            <div class="test-row-icon">
                <i class="fas fa-vial"></i>
            </div>
            <div class="test-row-body">
                <div class="test-row-title">${test.name}</div>
                <div class="test-row-preview">${test.description || 'Sem descrição'}</div>
                <div class="test-row-badges">
                    ${getStatusBadge(test.status)}
                    ${getPriorityBadge(test.priority)}
                    ${getTypeBadge(test.testType)}
                </div>
            </div>
            <div class="test-row-meta"></div>
            <div class="test-row-actions">
                <button onclick="editTest('${test.id}')" class="btn-icon" title="Editar">
                    <i class="fas fa-edit"></i>
                </button>
                <button onclick="runSingleTest('${test.id}')" class="btn-icon run" title="Executar">
                    <i class="fas fa-play"></i>
                </button>
                <button onclick="deleteTest('${test.id}')" class="btn-icon danger" title="Excluir">
                    <i class="fas fa-trash"></i>
                </button>
            </div>
        `;
        testListEl.appendChild(testRow);
    });

    testList.appendChild(testListEl);
}

// Get priority color class
function getPriorityColor(priority) {
    const colors = {
        'Alta': 'var(--red-text)',
        'Média': 'var(--amber-text)',
        'Baixa': 'var(--green-text)'
    };
    return colors[priority] || 'var(--text-muted)';
}

// Get type color class
function getTypeColor(type) {
    const colors = {
        'smoke': 'var(--green-text)',
        'regression': 'var(--blue-500)',
        'critico': 'var(--red-text)'
    };
    return colors[type] || 'var(--text-muted)';
}

// Get test type class
function getTestTypeClass(type) {
    switch(type) {
        case 'smoke': return 'var(--green-text)';
        case 'regression': return 'var(--blue-500)';
        case 'critico': return 'var(--red-text)';
        default: return 'var(--text-muted)';
    }
}

// Generate test with AI
async function generateTest() {
    const module = document.getElementById('moduleSelect').value;
    const menu = document.getElementById('menuSelect').value;
    const testType = document.getElementById('testTypeSelect').value;
    const priority = document.getElementById('prioritySelect').value;
    const testName = document.getElementById('testNameInput').value;
    const description = document.getElementById('testDescription').value;
    const testData = document.getElementById('testData').value;
    
    if (!module || !menu || !testName || !description) {
        showAlert('Por favor, preencha todos os campos obrigatórios', 'warning');
        return;
    }
    
    showLoading('Gerando testes com IA...');
    
    try {
        // Call the backend API to generate tests
        const response = await fetch('http://localhost:8080/api/generate-test', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                module: menuStructure[module].name,
                menu: menuStructure[module].menus[menu],
                testType: testType,
                priority: priority,
                testName: testName,
                description: description,
                testData: testData
            })
        });
        
        const result = await response.json();
        
        hideLoading();
        
        // Display generated output
        document.getElementById('generatedOutput').classList.remove('hidden');
        document.getElementById('featureOutput').textContent = result.feature;
        document.getElementById('javaOutput').textContent = result.java;
        
        // Store for later application
        window.currentGenerated = {
            module,
            menu,
            testType,
            priority,
            name: testName, // Changed from testName to name
            description,
            testData,
            feature: result.feature,
            java: result.java
        };
        
    } catch (error) {
        hideLoading();
        showAlert('Erro ao gerar teste: ' + error.message, 'error');
        console.error(error);
    }
}

// Save test draft
function saveTest() {
    const module = document.getElementById('moduleSelect').value;
    const menu = document.getElementById('menuSelect').value;
    const testType = document.getElementById('testTypeSelect').value;
    const priority = document.getElementById('prioritySelect').value;
    const testName = document.getElementById('testNameInput').value;
    const description = document.getElementById('testDescription').value;
    const testData = document.getElementById('testData').value;
    
    console.log('Saving test with data:', { module, menu, testName, description, editingTestId });
    
    if (!module || !menu || !testName || !description) {
        showAlert('Por favor, preencha todos os campos obrigatórios', 'warning');
        return;
    }
    
    if (editingTestId) {
        // Update existing test
        const testIndex = savedTests.findIndex(t => t.id === editingTestId);
        if (testIndex !== -1) {
            savedTests[testIndex] = {
                ...savedTests[testIndex],
                module,
                menu,
                testType,
                priority,
                name: testName,
                description,
                testData,
                updatedAt: new Date().toISOString()
            };
            
            localStorage.setItem('qaTests', JSON.stringify(savedTests));
            
            showAlert('Teste atualizado com sucesso!', 'success', () => {
                resetForm();
                showModuleTests(module, menu);
            });
        }
    } else {
        // Create new test
        const test = {
            id: Date.now().toString(),
            module,
            menu,
            testType,
            priority,
            name: testName,
            description,
            testData,
            createdAt: new Date().toISOString(),
            status: 'draft'
        };
        
        console.log('Test object created:', test);
        
        savedTests.push(test);
        localStorage.setItem('qaTests', JSON.stringify(savedTests));
        
        console.log('Tests after save:', savedTests);
        
        showAlert('Rascunho salvo com sucesso!', 'success', () => {
            resetForm();
            showModuleTests(module, menu);
        });
    }
    
    // Update dashboard if visible
    if (!document.getElementById('dashboardView').classList.contains('hidden')) {
        updateDashboardStats();
        loadRecentTests();
        loadTestDistribution();
    }
}

// Reset form to new test mode
function resetForm() {
    console.log('Resetting form...');
    editingTestId = null;
    
    // Reset form title
    const formTitle = document.getElementById('pageTitle');
    if (formTitle) {
        formTitle.textContent = 'Novo Teste';
    }
    
    // Reset subtitle
    const pageSubtitle = document.getElementById('pageSubtitle');
    if (pageSubtitle) {
        pageSubtitle.textContent = 'Crie um novo teste automatizado';
    }
    
    // Reset save button
    const saveButton = document.querySelector('#newTestForm button[onclick="saveTest()"]');
    if (saveButton) {
        saveButton.textContent = 'Salvar Rascunho';
        saveButton.classList.remove('bg-green-600', 'hover:bg-green-700');
        saveButton.classList.add('bg-blue-600', 'hover:bg-blue-700');
    }
    
    // Clear form fields and reset states
    const moduleSelect = document.getElementById('moduleSelect');
    const menuSelect = document.getElementById('menuSelect');
    const submenuSelect = document.getElementById('submenuSelect');
    
    if (moduleSelect) {
        moduleSelect.value = '';
    }
    if (menuSelect) {
        menuSelect.value = '';
        menuSelect.innerHTML = '<option value="">Selecione um menu</option>';
        menuSelect.disabled = true;
    }
    if (submenuSelect) {
        submenuSelect.value = '';
        submenuSelect.innerHTML = '<option value="">Selecione um submenu</option>';
        submenuSelect.disabled = true;
    }
    
    document.getElementById('testTypeSelect').value = 'smoke';
    document.getElementById('prioritySelect').value = 'Média';
    document.getElementById('testNameInput').value = '';
    document.getElementById('testDescription').value = '';
    document.getElementById('testData').value = '';
    
    console.log('Form reset completed');
}

// Apply generated test to project
function applyGenerated() {
    if (!window.currentGenerated) return;
    
    showLoading('Aplicando teste ao projeto...');
    
    // Save the generated test to localStorage as well
    const test = {
        id: Date.now().toString(),
        module: window.currentGenerated.module,
        menu: window.currentGenerated.menu,
        testType: window.currentGenerated.testType,
        priority: window.currentGenerated.priority,
        name: window.currentGenerated.name,
        description: window.currentGenerated.description,
        testData: window.currentGenerated.testData,
        feature: window.currentGenerated.feature,
        java: window.currentGenerated.java,
        createdAt: new Date().toISOString(),
        status: 'generated' // Mark as generated test
    };
    
    savedTests.push(test);
    localStorage.setItem('qaTests', JSON.stringify(savedTests));
    
    // This would call a backend endpoint to write files
    fetch('http://localhost:8080/api/apply-test', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(window.currentGenerated)
    })
    .then(response => response.json())
    .then(result => {
        hideLoading();
        showAlert('Teste aplicado e salvo com sucesso!', 'success', () => {
            // Clear the generated output
            document.getElementById('generatedOutput').classList.add('hidden');
            window.currentGenerated = null;
            showNewTestForm();
        });
    })
    .catch(error => {
        hideLoading();
        showAlert('Erro ao aplicar teste: ' + error.message, 'error');
    });
}

// Discard generated test
function discardGenerated() {
    document.getElementById('generatedOutput').classList.add('hidden');
    window.currentGenerated = null;
}

// Save generated test as draft
function saveGeneratedAsDraft() {
    if (!window.currentGenerated) return;
    
    // Save the generated test to localStorage
    const test = {
        id: Date.now().toString(),
        module: window.currentGenerated.module,
        menu: window.currentGenerated.menu,
        testType: window.currentGenerated.testType,
        priority: window.currentGenerated.priority,
        name: window.currentGenerated.name,
        description: window.currentGenerated.description,
        testData: window.currentGenerated.testData,
        feature: window.currentGenerated.feature,
        java: window.currentGenerated.java,
        createdAt: new Date().toISOString(),
        status: 'generated_draft' // Mark as generated draft
    };
    
    savedTests.push(test);
    localStorage.setItem('qaTests', JSON.stringify(savedTests));
    
    showAlert('Teste gerado salvo como rascunho!', 'success', () => {
        // Clear the generated output and navigate to test list
        document.getElementById('generatedOutput').classList.add('hidden');
        window.currentGenerated = null;
        showModuleTests(test.module, test.menu);
    });
}

// Run single test
async function runSingleTest(testId) {
    showLoading('Compilando e iniciando teste... Isso pode levar alguns segundos.');
    
    // Find test metadata
    const test = savedTests.find(t => t.id === testId);
    if (!test) {
        hideLoading();
        showAlert('Teste não encontrado!', 'error');
        return;
    }
    
    try {
        // Save metadata first
        const metadata = {
            id: test.id,
            name: test.name,
            module: test.module,
            menu: test.menu,
            description: test.description,
            testType: test.testType || 'smoke',
            priority: test.priority || 'Média'
        };
        
        await fetch('http://localhost:8080/api/save-test-metadata', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(metadata)
        });
        
        // Then run the test
        const response = await fetch(`http://localhost:8080/api/run-test/${testId}`, {
            method: 'POST'
        });
        
        if (!response.ok) {
            throw new Error(`Erro HTTP: ${response.status}`);
        }
        
        const result = await response.json();
        hideLoading();

        if (result.status === 'started') {
            // Save execution to history
            saveExecution({
                testId: test.id,
                testName: test.name,
                module: test.module,
                menu: test.menu,
                status: 'running',
                timestamp: new Date().toISOString()
            });
            showAlert('✅ Teste iniciado com sucesso!\n🎭 O Playwright foi aberto em uma nova janela.\n⏳ Acompanhe a execução no console do servidor.', 'success');
        } else if (result.status === 'error') {
            showAlert('❌ ' + (result.message || 'Erro ao executar teste'), 'error');
        } else {
            showAlert('Teste executado!', 'success');
        }
    } catch (error) {
        hideLoading();
        console.error('Erro ao executar teste:', error);
        showAlert('Erro ao executar teste: ' + error.message, 'error');
    }
}

// Edit test
function editTest(testId) {
    const test = savedTests.find(t => t.id === testId);
    if (!test) {
        showAlert('Teste não encontrado!', 'error');
        return;
    }
    
    console.log('Editing test:', test);
    
    // Set editing mode
    editingTestId = testId;
    
    // Show the form first
    showNewTestForm();
    
    // Load test data into form
    const moduleSelect = document.getElementById('moduleSelect');
    const menuSelect = document.getElementById('menuSelect');
    
    // Set module value
    moduleSelect.value = test.module;
    console.log('Module set to:', test.module);
    
    // Trigger change event to populate menus
    moduleSelect.dispatchEvent(new Event('change'));
    
    // Wait for menu options to be populated, then set values
    setTimeout(() => {
        // Populate menu select for this module
        const menus = menuStructure[test.module]?.menus || {};
        menuSelect.innerHTML = '<option value="">Selecione um menu</option>';
        
        Object.keys(menus).forEach(menuKey => {
            const menu = menus[menuKey];
            const option = document.createElement('option');
            option.value = menuKey;
            option.textContent = menu.name;
            menuSelect.appendChild(option);
        });
        
        // Now set the menu value
        if (menus[test.menu]) {
            menuSelect.value = test.menu;
            console.log('Menu set to:', test.menu);
        } else {
            console.error('Menu not found:', test.menu, 'for module:', test.module);
        }
        
        // Populate other fields
        const testTypeSelect = document.getElementById('testTypeSelect');
        const prioritySelect = document.getElementById('prioritySelect');
        
        if (testTypeSelect) testTypeSelect.value = test.testType || 'smoke';
        if (prioritySelect) prioritySelect.value = test.priority || 'Média';
        document.getElementById('testNameInput').value = test.name || '';
        document.getElementById('testDescription').value = test.description || '';
        document.getElementById('testData').value = test.testData || '';
        
        console.log('Form populated:', {
            module: moduleSelect.value,
            menu: menuSelect.value,
            testType: testTypeSelect?.value,
            priority: prioritySelect?.value
        });
        
        // Change form title to indicate editing
        const formTitle = document.getElementById('pageTitle');
        if (formTitle) {
            formTitle.textContent = 'Editar Teste';
        }
        
        // Change subtitle
        const pageSubtitle = document.getElementById('pageSubtitle');
        if (pageSubtitle) {
            pageSubtitle.textContent = 'Edite as informações do teste';
        }
        
        // Change save button text
        const saveButton = document.querySelector('#newTestForm button[onclick="saveTest()"]');
        if (saveButton) {
            saveButton.textContent = 'Atualizar Teste';
            saveButton.classList.remove('bg-blue-600', 'hover:bg-blue-700');
            saveButton.classList.add('bg-green-600', 'hover:bg-green-700');
        }
        
        console.log('Form populated with test data');
    }, 300); // Increased timeout to ensure menu is populated
}

// Run module tests
function runModuleTests(moduleKey) {
    showLoading('Iniciando testes do módulo...');
    
    fetch(`http://localhost:8080/api/run-module/${moduleKey}`, {
        method: 'POST'
    })
    .then(response => response.json())
    .then(result => {
        hideLoading();
        if (result.status === 'started') {
            showAlert(result.message || 'Testes do módulo iniciados! Verifique o console do servidor.', 'success');
        } else {
            showAlert(`Testes do módulo ${menuStructure[moduleKey].name} executados!`, 'success');
        }
    })
    .catch(error => {
        hideLoading();
        showAlert('Erro ao executar testes do módulo: ' + error.message, 'error');
    });
}

// Run menu tests
function runMenuTests(moduleKey, menuKey) {
    showLoading('Iniciando testes do menu...');
    
    fetch(`http://localhost:8080/api/run-menu/${moduleKey}/${menuKey}`, {
        method: 'POST'
    })
    .then(response => response.json())
    .then(result => {
        hideLoading();
        if (result.status === 'started') {
            showAlert(result.message || 'Testes do menu iniciados! Verifique o console do servidor.', 'success');
        } else {
            showAlert(`Testes do menu ${menuStructure[moduleKey].menus[menuKey]} executados!`, 'success');
        }
    })
    .catch(error => {
        hideLoading();
        showAlert('Erro ao executar testes do menu: ' + error.message, 'error');
    });
}

// Run all tests
function runAllTests() {
    showLoading('Iniciando todos os testes...');
    
    fetch('http://localhost:8080/api/run-all-tests', {
        method: 'POST'
    })
    .then(response => response.json())
    .then(result => {
        hideLoading();
        if (result.status === 'started') {
            showAlert(result.message || 'Testes iniciados! Verifique o console do servidor para ver o Playwright executando.', 'success');
        } else {
            showAlert('Testes executados com sucesso!', 'success');
        }
    })
    .catch(error => {
        hideLoading();
        showAlert('Erro ao executar testes: ' + error.message, 'error');
    });
}

// Delete test
function deleteTest(testId) {
    const test = savedTests.find(t => t.id === testId);
    if (!test) return;
    
    // Show custom confirmation modal
    showModal(
        'Confirmar Exclusão',
        `Tem certeza que deseja excluir o teste "${test.name}"? Esta ação não pode ser desfeita.`,
        'warning',
        () => {
            // Proceed with deletion
            savedTests = savedTests.filter(t => t.id !== testId);
            localStorage.setItem('qaTests', JSON.stringify(savedTests));
            
            // Show success message and exit to dashboard
            showAlert('Teste excluído com sucesso!', 'success', () => {
                showDashboard();
            });
        },
        true // Show cancel button
    );
}

// Run all tests
function runAllTests() {
    showLoading('Executando todos os testes...');
    
    fetch('http://localhost:8080/api/run-all-tests', {
        method: 'POST'
    })
    .then(response => response.json())
    .then(result => {
        hideLoading();
        showAlert('Testes executados: ' + result.summary, 'success');
    })
    .catch(error => {
        hideLoading();
        showAlert('Erro ao executar testes: ' + error.message, 'error');
    });
}

// Open reports
function openReports() {
    window.open('http://localhost:8080/reports', '_blank');
}

// Copy to clipboard
function copyToClipboard(elementId) {
    const text = document.getElementById(elementId).textContent;
    navigator.clipboard.writeText(text).then(() => {
        showAlert('Copiado para a área de transferência!', 'success');
    });
}

// Show loading overlay
function showLoading(text) {
    document.getElementById('loadingText').textContent = text;
    document.getElementById('loadingOverlay').classList.remove('hidden');
}

// Hide loading overlay
function hideLoading() {
    document.getElementById('loadingOverlay').classList.add('hidden');
}

// Show menu editor
function showMenuEditor() {
    hideAllViews();
    document.getElementById('menuEditorView').classList.remove('hidden');
    
    updatePageTitle('Editor de Menus', 'Organize módulos e menus do sistema');
    updateActiveNav('nav-menu-editor');
    
    // Save view state
    saveViewState('menuEditor');
    
    // Render menu editor
    renderMenuEditor();
}

// Show configuration form
function showConfigForm() {
    hideAllViews();
    document.getElementById('configView').classList.remove('hidden');
    
    updatePageTitle('Configurações', 'Configure URLs e credenciais do sistema');
    updateActiveNav('nav-config');
    
    // Save view state
    saveViewState('config');
    
    // Load current configuration
    loadConfig();
}

// Load configuration from localStorage
function loadConfig() {
    const config = JSON.parse(localStorage.getItem('qaAgentConfig') || '{}');
    
    document.getElementById('baseUrl').value = config.baseUrl || '';
    document.getElementById('username').value = config.username || '';
    document.getElementById('password').value = config.password || '';
    document.getElementById('timeout').value = config.timeout || 30;
    document.getElementById('environment').value = config.environment || 'test';
    document.getElementById('browser').value = config.browser || 'chromium';
}

// Save configuration
function saveConfig() {
    const config = {
        baseUrl: document.getElementById('baseUrl').value,
        username: document.getElementById('username').value,
        password: document.getElementById('password').value,
        timeout: parseInt(document.getElementById('timeout').value),
        environment: document.getElementById('environment').value,
        browser: document.getElementById('browser').value
    };
    
    localStorage.setItem('qaAgentConfig', JSON.stringify(config));
    
    // Also send to backend to update config.properties
    fetch('/api/update-config', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(config)
    })
    .then(response => response.json())
    .then(data => {
        if (data.status === 'success') {
            showAlert('Configurações salvas com sucesso!', 'success');
        } else {
            showAlert('Erro ao salvar configurações: ' + data.error, 'error');
        }
    })
    .catch(error => {
        console.error('Error:', error);
        showAlert('Configurações salvas localmente. Reinicie o servidor para aplicar as mudanças.', 'info');
    });
}

// Render menu editor
function renderMenuEditor() {
    const content = document.getElementById('menuEditorContent');
    content.innerHTML = '';
    
    // Create container for modules
    const modulesContainer = document.createElement('div');
    modulesContainer.id = 'modulesContainer';
    modulesContainer.style.cssText = 'display: flex; flex-direction: column; gap: 16px;';
    
    Object.keys(menuStructure).forEach((moduleKey, index) => {
        const module = menuStructure[moduleKey];
        const moduleDiv = document.createElement('div');
        moduleDiv.style.cssText = 'border: 1px solid var(--border); border-radius: var(--radius-md); padding: 16px; background: var(--bg-card); margin-bottom: 16px;';
        moduleDiv.className = 'draggable';
        moduleDiv.draggable = true;
        moduleDiv.dataset.type = 'module';
        moduleDiv.dataset.key = moduleKey;
        moduleDiv.dataset.index = index;

        moduleDiv.innerHTML = `
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;">
                <div style="display: flex; align-items: center; gap: 8px; flex: 1;">
                    <i class="fas fa-grip-vertical" style="color: var(--text-muted); cursor: grab;"></i>
                    <input type="text" value="${module.name}"
                           onchange="updateModuleName('${moduleKey}', this.value)"
                           style="font-weight: 600; font-size: 15px; background: transparent; border: none; border-bottom: 2px solid var(--border); color: var(--text-primary); flex: 1; outline: none; padding: 4px 0;">
                    <span style="font-size: 12px; color: var(--text-muted);">(${moduleKey})</span>
                </div>
                <button onclick="deleteModule('${moduleKey}')" class="btn-icon danger">
                    <i class="fas fa-trash"></i>
                </button>
            </div>
            <div id="menus-${moduleKey}" style="display: flex; flex-direction: column; gap: 8px;">
                ${Object.keys(module.menus).map((menuKey, menuIndex) => `
                    <div class="draggable" style="display: flex; align-items: center; gap: 8px;"
                         draggable="true"
                         data-type="menu"
                         data-module="${moduleKey}"
                         data-key="${menuKey}"
                         data-index="${menuIndex}">
                        <i class="fas fa-grip-vertical" style="color: var(--text-muted); cursor: grab;"></i>
                        <input type="text" value="${module.menus[menuKey]}"
                               onchange="updateMenuName('${moduleKey}', '${menuKey}', this.value)"
                               style="flex: 1; padding: 6px 10px; border: 1px solid var(--border); border-radius: var(--radius-sm); background: var(--bg-input); color: var(--text-primary); outline: none;">
                        <span style="font-size: 11px; color: var(--text-muted);">(${menuKey})</span>
                        <button onclick="deleteMenu('${moduleKey}', '${menuKey}')" class="btn-icon danger">
                            <i class="fas fa-times"></i>
                        </button>
                    </div>
                `).join('')}
                <button onclick="addMenu('${moduleKey}')" style="width: 100%; text-align: left; padding: 8px 12px; border: 2px dashed var(--border); border-radius: var(--radius-sm); color: var(--text-muted); background: transparent; cursor: pointer; transition: all 0.15s;"
                        onmouseover="this.style.borderColor='var(--blue-500)'; this.style.color='var(--blue-500)';"
                        onmouseout="this.style.borderColor='var(--border)'; this.style.color='var(--text-muted)';">
                    <i class="fas fa-plus"></i> Adicionar Menu
                </button>
            </div>
        `;

        modulesContainer.appendChild(moduleDiv);
    });
    
    content.appendChild(modulesContainer);
    
    // Add button for new module
    const addModuleDiv = document.createElement('div');
    addModuleDiv.style.cssText = 'border: 2px dashed var(--border); border-radius: var(--radius-md); padding: 16px; text-align: center; margin-top: 16px;';
    addModuleDiv.innerHTML = `
        <button onclick="addModule()" style="color: var(--text-muted); background: none; border: none; cursor: pointer; transition: color 0.15s;"
                onmouseover="this.style.color='var(--blue-500)'" onmouseout="this.style.color='var(--text-muted)'">
            <i class="fas fa-plus" style="font-size: 24px; margin-bottom: 4px; display: block;"></i>
            <span style="font-size: 13px;">Adicionar Módulo</span>
        </button>
    `;
    content.appendChild(addModuleDiv);
    
    // Initialize drag and drop
    initializeDragAndDrop();
}

// Update module name
function updateModuleName(moduleKey, newName) {
    menuStructure[moduleKey].name = newName;
}

// Update menu name
function updateMenuName(moduleKey, menuKey, newName) {
    menuStructure[moduleKey].menus[menuKey] = newName;
}

// Delete module
function deleteModule(moduleKey) {
    showModal(
        'Confirmar Exclusão',
        `Tem certeza que deseja excluir o módulo "${menuStructure[moduleKey].name}" e todos os seus menus? Esta ação não pode ser desfeita.`,
        'warning',
        () => {
            delete menuStructure[moduleKey];
            renderMenuEditor();
        },
        true
    );
}

// Delete menu
function deleteMenu(moduleKey, menuKey) {
    showModal(
        'Confirmar Exclusão',
        `Tem certeza que deseja excluir o menu "${menuStructure[moduleKey].menus[menuKey]}"? Esta ação não pode ser desfeita.`,
        'warning',
        () => {
            delete menuStructure[moduleKey].menus[menuKey];
            renderMenuEditor();
        },
        true
    );
}

// Add new menu with custom modal and auto-generated ID
function addMenu(moduleKey) {
    // Create custom modal for menu input
    const modalDiv = document.createElement('div');
    modalDiv.id = 'addMenuModal';
    modalDiv.className = 'fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50';
    modalDiv.innerHTML = `
        <div class="bg-white rounded-2xl shadow-2xl max-w-md w-full mx-4 overflow-hidden animate-fade-in">
            <div class="bg-gradient-to-r from-blue-600 to-blue-700 px-6 py-4">
                <h3 class="text-xl font-bold text-white flex items-center gap-2">
                    <i class="fas fa-plus-circle"></i>
                    Adicionar Menu
                </h3>
            </div>
            <div class="p-6 space-y-4">
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">Nome do Menu</label>
                    <input type="text" id="menuNameInput" placeholder="Ex: Escuta Inicial" 
                           class="w-full px-4 py-3 border border-gray-300 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition"
                           oninput="generateMenuId(this.value)">
                    <p class="text-xs text-gray-500 mt-1">Digite o nome e o ID será gerado automaticamente</p>
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">ID do Menu (gerado)</label>
                    <input type="text" id="menuIdInput" placeholder="escuta_inicial" 
                           class="w-full px-4 py-3 border border-gray-300 rounded-xl bg-gray-50 text-gray-600"
                           readonly>
                </div>
                <div class="flex gap-3 pt-2">
                    <button onclick="closeAddMenuModal()" class="flex-1 btn-secondary">
                        Cancelar
                    </button>
                    <button onclick="saveNewMenu('${moduleKey}')" class="flex-1 btn-primary">
                        <i class="fas fa-save mr-2"></i>
                        Salvar
                    </button>
                </div>
            </div>
        </div>
    `;
    document.body.appendChild(modalDiv);
    
    // Focus on name input
    setTimeout(() => document.getElementById('menuNameInput').focus(), 100);
}

// Generate menu ID from name
function generateMenuId(name) {
    const id = name.toLowerCase()
        .normalize('NFD').replace(/[\u0300-\u036f]/g, '') // Remove accents
        .replace(/[^a-z0-9\s]/g, '') // Remove special chars
        .trim()
        .replace(/\s+/g, '_'); // Replace spaces with underscores
    document.getElementById('menuIdInput').value = id;
}

// Close add menu modal
function closeAddMenuModal() {
    const modal = document.getElementById('addMenuModal');
    if (modal) modal.remove();
}

// Save new menu
function saveNewMenu(moduleKey) {
    const menuName = document.getElementById('menuNameInput').value.trim();
    const menuId = document.getElementById('menuIdInput').value.trim();
    
    if (!menuName) {
        showAlert('Digite o nome do menu', 'error');
        return;
    }
    
    if (!menuId) {
        showAlert('ID do menu não pode estar vazio', 'error');
        return;
    }
    
    // Check if ID already exists
    if (menuStructure[moduleKey].menus[menuId]) {
        showAlert('Já existe um menu com este ID', 'error');
        return;
    }
    
    menuStructure[moduleKey].menus[menuId] = menuName;
    closeAddMenuModal();
    renderMenuEditor();
    showAlert('Menu adicionado com sucesso!', 'success');
}

// Add new module
function addModule() {
    const moduleKey = prompt('Digite o ID do módulo (ex: novo_modulo):');
    if (moduleKey && moduleKey.trim()) {
        const moduleName = prompt('Digite o nome do módulo:');
        if (moduleName && moduleName.trim()) {
            menuStructure[moduleKey.trim()] = {
                name: moduleName.trim(),
                menus: {}
            };
            renderMenuEditor();
        }
    }
}

// Save menu structure
function saveMenuStructure() {
    localStorage.setItem('qaMenuStructure', JSON.stringify(menuStructure));
    loadModulesList();
    updateModuleSelect(); // Update the module select in new test form
    showAlert('Estrutura de menus salva com sucesso!', 'success');
}

// Update module select in new test form
function updateModuleSelect() {
    const moduleSelect = document.getElementById('moduleSelect');
    if (!moduleSelect) {
        console.error('moduleSelect element not found!');
        return;
    }
    
    console.log('Updating module select with:', Object.keys(menuStructure));
    
    // Clear existing options
    moduleSelect.innerHTML = '<option value="">Selecione um módulo</option>';
    
    // Add modules from current menu structure
    Object.keys(menuStructure).forEach(moduleKey => {
        const option = document.createElement('option');
        option.value = moduleKey;
        option.textContent = menuStructure[moduleKey].name;
        moduleSelect.appendChild(option);
        console.log('Added module option:', moduleKey, menuStructure[moduleKey].name);
    });
    
    // Enable the select
    moduleSelect.disabled = false;
    console.log('Module select updated successfully');
}

// Reset to default
function resetToDefault() {
    showModal(
        'Confirmar Restauração',
        'Tem certeza que deseja restaurar a estrutura padrão? Todas as alterações serão perdidas.',
        'warning',
        () => {
            menuStructure = JSON.parse(JSON.stringify(defaultMenuStructure));
            localStorage.removeItem('qaMenuStructure');
            loadModulesList();
            updateModuleSelect(); // Update the module select in new test form
            renderMenuEditor();
            showAlert('Estrutura restaurada para o padrão!', 'success');
        },
        true
    );
}

// Initialize drag and drop functionality
function initializeDragAndDrop() {
    const draggables = document.querySelectorAll('.draggable');
    const containers = document.querySelectorAll('#modulesContainer, [id^="menus-"]');
    
    draggables.forEach(draggable => {
        draggable.addEventListener('dragstart', handleDragStart);
        draggable.addEventListener('dragend', handleDragEnd);
    });
    
    containers.forEach(container => {
        container.addEventListener('dragover', handleDragOver);
        container.addEventListener('drop', handleDrop);
        container.addEventListener('dragenter', handleDragEnter);
        container.addEventListener('dragleave', handleDragLeave);
    });
}

let draggedElement = null;
let draggedType = null;
let draggedKey = null;
let draggedModule = null;

function handleDragStart(e) {
    draggedElement = this;
    draggedType = this.dataset.type;
    draggedKey = this.dataset.key;
    draggedModule = this.dataset.module || null;
    
    this.classList.add('dragging');
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/html', this.innerHTML);
}

function handleDragEnd(e) {
    this.classList.remove('dragging');
    
    // Remove all drag-over classes
    document.querySelectorAll('.drag-over').forEach(elem => {
        elem.classList.remove('drag-over');
    });
    
    // Remove all drop indicators
    document.querySelectorAll('.drop-indicator').forEach(elem => {
        elem.remove();
    });
}

function handleDragOver(e) {
    if (e.preventDefault) {
        e.preventDefault();
    }
    
    e.dataTransfer.dropEffect = 'move';
    
    const afterElement = getDragAfterElement(e.currentTarget, e.clientY);
    const dragging = document.querySelector('.dragging');
    
    if (afterElement == null) {
        e.currentTarget.appendChild(dragging);
    } else {
        e.currentTarget.insertBefore(dragging, afterElement);
    }
    
    return false;
}

function handleDragEnter(e) {
    if (draggedType === 'menu' && this.id.startsWith('menus-')) {
        if (draggedModule === this.id.replace('menus-', '') || !draggedModule) {
            this.classList.add('drag-over');
        }
    } else if (draggedType === 'module' && this.id === 'modulesContainer') {
        this.classList.add('drag-over');
    }
}

function handleDragLeave(e) {
    this.classList.remove('drag-over');
}

function handleDrop(e) {
    if (e.stopPropagation) {
        e.stopPropagation();
    }
    
    const dropTarget = this;
    
    if (draggedType === 'module' && dropTarget.id === 'modulesContainer') {
        // Reorder modules
        reorderModules();
    } else if (draggedType === 'menu' && dropTarget.id.startsWith('menus-')) {
        const targetModule = dropTarget.id.replace('menus-', '');
        if (draggedModule === targetModule || !draggedModule) {
            // Reorder menus within the same module or move to new module
            reorderMenus(targetModule);
        }
    }
    
    return false;
}

function getDragAfterElement(container, y) {
    const draggableElements = [...container.querySelectorAll('.draggable:not(.dragging)')];
    
    return draggableElements.reduce((closest, child) => {
        const box = child.getBoundingClientRect();
        const offset = y - box.top - box.height / 2;
        
        if (offset < 0 && offset > closest.offset) {
            return { offset: offset, element: child };
        } else {
            return closest;
        }
    }, { offset: Number.NEGATIVE_INFINITY }).element;
}

function reorderModules() {
    const modulesContainer = document.getElementById('modulesContainer');
    const moduleElements = modulesContainer.querySelectorAll('[data-type="module"]');
    
    const newOrder = {};
    moduleElements.forEach(element => {
        const key = element.dataset.key;
        newOrder[key] = menuStructure[key];
    });
    
    menuStructure = newOrder;
}

function reorderMenus(targetModule) {
    const menusContainer = document.getElementById(`menus-${targetModule}`);
    const menuElements = menusContainer.querySelectorAll('[data-type="menu"]');
    
    const newOrder = {};
    menuElements.forEach(element => {
        const key = element.dataset.key;
        newOrder[key] = menuStructure[targetModule].menus[key];
    });
    
    menuStructure[targetModule].menus = newOrder;
}
