// API Base URL - detecta porta automaticamente
const API_BASE = window.location.origin.includes('localhost') 
    ? window.location.origin 
    : 'http://localhost:8080';

// Menu structure - loaded ONLY from server (SQLite), never from localStorage
// Estrutura vazia inicial - será populada pelo usuário no GUI
let menuStructure = {};

// Default menu structure for fallback when database is empty
const defaultMenuStructure = {
    "atencao_primaria": {
        "name": "Atendimento da Atenção Primária",
        "menus": {
            "acolhimento": "Acolhimento",
            "escuta_inicial": "Escuta Inicial",
            "consulta_medica": "Consulta Médica"
        }
    }
};

// Saved tests storage - will be loaded from database
let savedTests = [];
let editingTestId = null; // Track if we're editing a test

const SIDEBAR_COLLAPSED_MODULES_KEY = 'qaSidebarCollapsedModules';

function isNonEmptyString(value) {
    return typeof value === 'string' && value.trim().length > 0;
}

function qaHumanizeKey(key) {
    const raw = String(key ?? '').replace(/[_-]+/g, ' ').trim();
    if (!raw) return '';
    return raw
        .split(' ')
        .filter(Boolean)
        .map(part => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ');
}

function normalizeMenuStructure(structure) {
    const input = structure && typeof structure === 'object' ? structure : {};
    const normalized = {};

    Object.keys(input).forEach(moduleKey => {
        const moduleValue = input[moduleKey];
        if (!moduleValue || typeof moduleValue !== 'object') return;

        const rawName = isNonEmptyString(moduleValue.name) ? moduleValue.name.trim() : '';
        const rawMenus = moduleValue.menus && typeof moduleValue.menus === 'object' ? moduleValue.menus : {};

        const menus = {};
        Object.keys(rawMenus).forEach(menuKey => {
            const rawLabel = rawMenus[menuKey];
            menus[menuKey] = isNonEmptyString(rawLabel) ? rawLabel.trim() : '';
        });

        let name = rawName;
        if (moduleKey === 'atencao_primaria' && (!name || name === 'Atenção Primária')) {
            name = 'Atendimento da Atenção Primária';
        }
        if (!name && defaultMenuStructure[moduleKey]?.name) {
            name = defaultMenuStructure[moduleKey].name;
        }
        if (!name) name = qaHumanizeKey(moduleKey) || moduleKey;

        Object.keys(menus).forEach(menuKey => {
            if (isNonEmptyString(menus[menuKey])) return;
            const fallback = defaultMenuStructure[moduleKey]?.menus?.[menuKey];
            menus[menuKey] = isNonEmptyString(fallback) ? fallback : (qaHumanizeKey(menuKey) || menuKey);
        });

        normalized[moduleKey] = { name, menus };
    });

    return normalized;
}

// API functions for database persistence - NO localStorage fallback
async function saveTestToDB(test) {
    try {
        console.log('[saveTestToDB] Enviando teste:', test);
        console.log('[saveTestToDB] Descrição:', test.description);
        const response = await fetch(`${API_BASE}/api/save-test`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(test)
        });
        const result = await response.json();
        if (result.status === 'success') {
            // Recarrega do servidor para garantir sincronização
            await loadTestsFromDB();
        }
        return result;
    } catch (error) {
        console.error('Error saving test to DB:', error);
        showAlert('Erro ao salvar teste no servidor', 'error');
        return { status: 'error', message: error.message };
    }
}

async function loadTestsFromDB() {
    try {
        const response = await fetch(`${API_BASE}/api/load-tests`);
        const tests = await response.json();
        console.log('[loadTestsFromDB] Testes carregados:', tests);
        if (tests && tests.length > 0) {
            console.log('[loadTestsFromDB] Primeiro teste descricao:', tests[0].description);
        }
        savedTests = tests || [];
        return savedTests;
    } catch (error) {
        console.error('Error loading tests from DB:', error);
        showAlert('Erro ao carregar testes do servidor', 'error');
        savedTests = [];
        return [];
    }
}

async function deleteTestFromDB(testId) {
    try {
        const response = await fetch(`${API_BASE}/api/delete-test`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id: testId })
        });
        return await response.json();
    } catch (error) {
        console.error('Error deleting test from DB:', error);
        return { status: 'fallback' };
    }
}

async function saveMenuStructureToDB(structure) {
    try {
        const response = await fetch(`${API_BASE}/api/save-menu-structure`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(structure)
        });
        const result = await response.json();
        if (result.status === 'success') {
            menuStructure = structure;
        }
        return result;
    } catch (error) {
        console.error('Error saving menu structure to DB:', error);
        showAlert('Erro ao salvar estrutura do menu', 'error');
        return { status: 'error', message: error.message };
    }
}

async function loadMenuStructureFromDB() {
    try {
        const response = await fetch(`${API_BASE}/api/load-menu-structure`);
        if (!response.ok) {
            throw new Error(`Falha ao carregar menus (HTTP ${response.status})`);
        }

        const structure = await response.json();
        if (structure && typeof structure === 'object' && 'error' in structure && Object.keys(structure).length === 1) {
            throw new Error(String(structure.error || 'Erro ao carregar menus'));
        }

        const normalized = normalizeMenuStructure(structure);

        if (Object.keys(normalized).length > 0) {
            menuStructure = normalized;
            console.log('[Menu] Loaded from database:', Object.keys(menuStructure));
        } else {
            // Database is empty, use default structure
            menuStructure = JSON.parse(JSON.stringify(defaultMenuStructure));
            console.log('[Menu] Using default structure (database empty)');
            // Save default to database for next time
            await saveMenuStructureToDB(menuStructure);
        }
        return menuStructure;
    } catch (error) {
        console.error('Error loading menu structure from DB:', error);
        // Fallback to default on error
        menuStructure = JSON.parse(JSON.stringify(defaultMenuStructure));
        showAlert(`Não foi possível carregar os menus do servidor. Usando um menu padrão.\n\nDetalhe: ${String(error?.message || error)}`, 'warning', 6000);
        return menuStructure;
    }
}

async function saveConfigToDB(config) {
    try {
        const response = await fetch(`${API_BASE}/api/save-config`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });
        return await response.json();
    } catch (error) {
        console.error('Error saving config to DB:', error);
        showAlert('Erro ao salvar configuração', 'error');
        return { status: 'error', message: error.message };
    }
}

async function loadConfigFromDB() {
    try {
        const response = await fetch(`${API_BASE}/api/load-config`);
        const config = await response.json();
        return config || {};
    } catch (error) {
        console.error('Error loading config from DB:', error);
        return {};
    }
}

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
        const response = await fetch(`${API_BASE}/api/save-data`, {
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
        const response = await fetch(`${API_BASE}/api/load-data/${key}`);
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
let modalAutoCloseTimer = null;

function showModal(title, message, type = 'success', confirmCallback = null, showCancel = false, autoCloseMs = 0) {
    const modal = document.getElementById('customModal');
    const modalIcon = document.getElementById('modalIcon');
    const modalTitle = document.getElementById('modalTitle');
    const modalMessage = document.getElementById('modalMessage');
    const modalConfirm = document.getElementById('modalConfirm');
    const modalCancel = document.getElementById('modalCancel');
    
    if (modalAutoCloseTimer) {
        clearTimeout(modalAutoCloseTimer);
        modalAutoCloseTimer = null;
    }

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
        if (modalAutoCloseTimer) {
            clearTimeout(modalAutoCloseTimer);
            modalAutoCloseTimer = null;
        }
        if (confirmCallback) {
            confirmCallback();
        }
    };
    
    // Set up cancel button callback
    modalCancel.onclick = () => {
        modal.classList.add('hidden');
        if (modalAutoCloseTimer) {
            clearTimeout(modalAutoCloseTimer);
            modalAutoCloseTimer = null;
        }
    };
    
    // Show modal
    modal.classList.remove('hidden');

    const ms = Number(autoCloseMs || 0);
    if (ms > 0) {
        modalAutoCloseTimer = setTimeout(() => {
            modal.classList.add('hidden');
            modalAutoCloseTimer = null;
        }, ms);
    }
}

// Replace alert with custom modal
function showAlert(message, type = 'info', callbackOrDuration = null) {
    const titles = {
        'success': 'Sucesso!',
        'error': 'Erro!',
        'warning': 'Atenção!',
        'info': 'Informação'
    };
    
    let callback = null;
    let autoCloseMs = 0;
    if (typeof callbackOrDuration === 'function') {
        callback = callbackOrDuration;
    } else if (typeof callbackOrDuration === 'number') {
        autoCloseMs = callbackOrDuration;
    } else if (callbackOrDuration && typeof callbackOrDuration === 'object') {
        callback = typeof callbackOrDuration.onConfirm === 'function' ? callbackOrDuration.onConfirm : null;
        autoCloseMs = Number(callbackOrDuration.autoCloseMs || 0);
    }

    showModal(titles[type] || 'Informação', message, type, callback, false, autoCloseMs);
}

// Initialize
document.addEventListener('DOMContentLoaded', async () => {
    // Load data from database on startup
    await loadTestsFromDB();
    await loadMenuStructureFromDB();
    await loadConfigFromDB();

    normalizeSidebarActions();
    
    // Restore previous view state
    const state = restoreViewState();
    console.log('Initializing with state:', state);

    switch (state.view) {
        case 'coverage':
            showCoverage();
            break;
        case 'alerts':
            showAlerts();
            break;
        case 'schedules':
            showSchedules();
            break;
        case 'environments':
            showEnvironments();
            break;
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

    loadModulesList();
});

function normalizeSidebarActions() {
    document.querySelectorAll('.nav-item').forEach(item => {
        const label = item.textContent.trim();
        if (label === 'Agendamentos') {
            item.id = 'nav-schedules';
            item.onclick = showSchedules;
        }
        if (label === 'Ambientes') {
            item.id = 'nav-environments';
            item.onclick = showEnvironments;
        }
        if (label === 'Configurações') {
            item.id = 'nav-config-shortcut';
            item.onclick = showConfigForm;
        }
    });
}

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
    renderExecutiveDashboard();
    refreshDashboardExecutionsFromDisk();
}

async function refreshDashboardExecutionsFromDisk() {
    const raw = await loadFromDisk('qaAgentExecutions');
    if (!raw) return;
    try {
        const parsed = JSON.parse(raw);
        if (!Array.isArray(parsed)) return;
        const local = JSON.parse(localStorage.getItem('qaAgentExecutions') || '[]');
        const merged = mergeExecutionHistories(local, parsed);
        localStorage.setItem('qaAgentExecutions', JSON.stringify(merged));
        renderExecutiveDashboard();
    } catch (error) {
        return;
    }
}

function mergeExecutionHistories(local, fromDisk) {
    const result = [];
    const indexById = new Map();

    const push = (item) => {
        if (!item || typeof item !== 'object') return;
        const id = item.executionId || item.id;
        if (!id) {
            result.push(item);
            return;
        }
        if (indexById.has(id)) {
            result[indexById.get(id)] = { ...result[indexById.get(id)], ...item };
            return;
        }
        indexById.set(id, result.length);
        result.push(item);
    };

    (Array.isArray(local) ? local : []).forEach(push);
    (Array.isArray(fromDisk) ? fromDisk : []).forEach(push);

    result.sort((a, b) => new Date(b.timestamp || b.startedAt || 0) - new Date(a.timestamp || a.startedAt || 0));
    return result.slice(0, 200);
}

function showCoverage() {
    hideAllViews();
    document.getElementById('coverageView')?.classList.remove('hidden');
    updatePageTitle('Cobertura', 'Visao analitica da cobertura de automacao por modulo');
    updateActiveNav('nav-coverage');
    saveViewState('coverage');
    renderCoverageView();
}

function showAlerts() {
    hideAllViews();
    document.getElementById('alertsView')?.classList.remove('hidden');
    updatePageTitle('Alertas', 'Monitoramento de riscos e estabilidade dos testes');
    updateActiveNav('nav-alerts');
    saveViewState('alerts');
    renderAlertsView();
}

function showSchedules() {
    hideAllViews();
    document.getElementById('schedulesView')?.classList.remove('hidden');
    updatePageTitle('Agendamentos', 'Planejamento de execucoes automatizadas');
    updateActiveNav('nav-schedules');
    saveViewState('schedules');
    renderSchedulesView();
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

// Executive dashboard state and reusable widgets
const dashboardRecentState = {
    page: 1,
    pageSize: 8,
    filter: 'all',
    search: '',
    sortKey: 'timestamp',
    sortDir: 'desc'
};

const dashboardCharts = {};

function renderExecutiveDashboard() {
    const metrics = buildDashboardMetrics();
    updateExecutiveHeader(metrics);
    renderDashboardKpis(metrics);
    renderDashboardCharts(metrics);
    renderDashboardRecentExecutions();
    renderDashboardInsights(metrics);
    renderDashboardAiMetrics(metrics);
    renderDashboardTimeline(metrics);
}

function buildDashboardMetrics() {
    const executions = getDashboardExecutions();
    const today = new Date().toDateString();
    const todayExecutions = executions.filter(exec => new Date(exec.timestamp).toDateString() === today);
    const failures = executions.filter(exec => exec.status === 'failed');
    const successes = executions.filter(exec => exec.status === 'success');
    const running = executions.filter(exec => exec.status === 'running');
    const cancelled = executions.filter(exec => exec.status === 'cancelled');
    const successRate = executions.length ? Math.round((successes.length / executions.length) * 100) : 0;
    const avgDurationMs = executions.length
        ? Math.round(executions.reduce((sum, exec) => sum + (exec.durationMs || 0), 0) / executions.length)
        : 0;
    const modules = Object.keys(menuStructure || {});
    const smokeTests = savedTests.filter(test => test.testType === 'smoke').length;
    const regressionTests = savedTests.filter(test => test.testType === 'regression').length;
    const aiTests = savedTests.filter(test => {
        const text = `${test.name || ''} ${test.description || ''}`.toLowerCase();
        return text.includes('ia') || text.includes('ai') || text.includes('inteligente') || test.status === 'generated' || test.status === 'generated_draft';
    }).length;
    const parallelExecutions = Math.max(0, Math.min(12, Math.ceil(todayExecutions.length / 3)));
    const moduleCoverage = modules.length
        ? Math.round((new Set(savedTests.map(test => test.module).filter(Boolean)).size / modules.length) * 100)
        : 0;
    const criticalFailures = failures.filter(exec => {
        const relatedTest = savedTests.find(test => String(test.id) === String(exec.testId));
        return relatedTest?.priority === 'Crítica' || relatedTest?.priority === 'Critica' || exec.critical;
    }).length;

    return {
        executions,
        todayExecutions,
        failures,
        successes,
        running,
        cancelled,
        successRate,
        avgDurationMs,
        modules,
        smokeTests,
        regressionTests,
        aiTests,
        parallelExecutions,
        moduleCoverage,
        criticalFailures,
        healthScore: calculateHealthScore(successRate, criticalFailures, failures.length, running.length)
    };
}

function getDashboardExecutions() {
    let executions = [];
    try {
        executions = JSON.parse(localStorage.getItem('qaAgentExecutions') || '[]');
    } catch (error) {
        executions = [];
    }

    if (!Array.isArray(executions)) executions = [];

    const normalized = executions.map((exec, index) => normalizeDashboardExecution(exec, index));
    if (normalized.length > 0) {
        return normalized;
    }
    return [];
}

function normalizeDashboardExecution(exec, index = 0) {
    const status = normalizeDashboardStatus(exec.status);
    const relatedTest = savedTests.find(test => String(test.id) === String(exec.testId));
    const durationMs = Number(exec.durationMs || exec.duration || exec.durationMillis || 0);
    return {
        id: exec.id || `${exec.testId || 'exec'}-${index}`,
        testId: exec.testId,
        testName: exec.testName || relatedTest?.name || 'Teste sem nome',
        module: exec.module || relatedTest?.module || 'sem_modulo',
        menu: exec.menu || relatedTest?.menu || '',
        testType: exec.testType || relatedTest?.testType || 'functional',
        status,
        durationMs,
        timestamp: exec.timestamp || exec.startedAt || exec.createdAt || new Date().toISOString(),
        owner: exec.owner || exec.responsavel || exec.responsible || 'QA Automation',
        environment: exec.environment || getDashboardEnvironmentLabel(),
        critical: Boolean(exec.critical),
        lastError: exec.lastError || exec.error || exec.mensagemErro || exec.messageError || exec.message || ''
    };
}

function normalizeDashboardStatus(status) {
    const value = String(status || '').toLowerCase();
    if (['success', 'passed', 'passou', 'ok', 'completed'].includes(value)) return 'success';
    if (['failed', 'failure', 'falhou', 'error', 'erro'].includes(value)) return 'failed';
    if (['running', 'executando', 'in_progress'].includes(value)) return 'running';
    if (['cancelled', 'canceled', 'cancelado'].includes(value)) return 'cancelled';
    return value || 'running';
}

function updateExecutiveHeader(metrics) {
    const environmentEl = document.getElementById('dashboardEnvironment');
    if (environmentEl) environmentEl.textContent = getDashboardEnvironmentLabel();

    const lastExecutionEl = document.getElementById('dashboardLastExecution');
    const last = metrics.executions
        .slice()
        .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))[0];
    if (lastExecutionEl) {
        lastExecutionEl.textContent = last ? `Última execução: ${formatRelativeTime(last.timestamp)}` : 'Última execução: sem registros';
    }

    const scoreEl = document.getElementById('automationHealthScore');
    const ringEl = document.getElementById('automationHealthRing');
    const textEl = document.getElementById('automationHealthText');
    const statusEl = document.getElementById('automationHealthStatus');
    const score = metrics.healthScore;
    const color = score >= 85 ? 'var(--green)' : score >= 65 ? 'var(--amber)' : 'var(--red)';
    const statusClass = score >= 85 ? 'stable' : score >= 65 ? 'warning' : 'critical';
    const statusText = score >= 85 ? 'Estável' : score >= 65 ? 'Atenção' : 'Falhas críticas';

    if (scoreEl) scoreEl.textContent = `${score}%`;
    if (ringEl) ringEl.style.background = `conic-gradient(${color} 0deg, ${color} ${score * 3.6}deg, #E2E8F0 ${score * 3.6}deg)`;
    if (textEl) textEl.textContent = `${metrics.successRate}% de sucesso, ${metrics.criticalFailures} falha(s) crítica(s) e ${metrics.running.length} execução(ões) em andamento.`;
    if (statusEl) {
        statusEl.className = `health-status ${statusClass}`;
        statusEl.innerHTML = `<i class="fas fa-circle"></i>${statusText}`;
    }
}

function renderDashboardKpis(metrics) {
    const container = document.getElementById('dashboardKpiGrid');
    if (!container) return;

    const today = new Date().toDateString();
    const todayFailures = metrics.executions.filter(exec => exec.status === 'failed' && new Date(exec.timestamp).toDateString() === today).length;
    const coveredModules = new Set(savedTests.map(test => test.module).filter(Boolean)).size;

    const kpis = [
        { label: 'Testes Cadastrados', value: savedTests.length, icon: 'fa-clipboard-list', accent: '#2563EB', trend: `${coveredModules}/${metrics.modules.length || 0} módulos cobertos`, trendType: 'neutral', help: 'Inventário de testes cadastrados na base local.' },
        { label: 'Execuções Registradas', value: metrics.executions.length, icon: 'fa-circle-play', accent: '#10B981', trend: `${metrics.todayExecutions.length} hoje`, trendType: 'neutral', help: 'Execuções concluídas registradas no histórico.' },
        { label: 'Taxa de Sucesso', value: `${metrics.successRate}%`, icon: 'fa-bullseye', accent: '#10B981', trend: `${metrics.successes.length}/${metrics.executions.length || 0} com sucesso`, trendType: 'neutral', help: 'Percentual de execuções concluídas com sucesso.', progress: metrics.successRate },
        { label: 'Falhas', value: metrics.failures.length, icon: 'fa-xmark', accent: '#EF4444', trend: `${todayFailures} hoje`, trendType: 'neutral', help: 'Total de execuções com falha registradas.' },
        { label: 'Tempo Médio', value: formatDuration(metrics.avgDurationMs), icon: 'fa-clock', accent: '#8B5CF6', trend: `${metrics.executions.length} execuções`, trendType: 'neutral', help: 'Média de duração das execuções registradas.' },
        { label: 'Cobertura', value: `${metrics.moduleCoverage}%`, icon: 'fa-chart-pie', accent: '#F59E0B', trend: `${coveredModules} módulos com testes`, trendType: 'neutral', help: 'Cobertura por módulos com pelo menos um teste.' , progress: metrics.moduleCoverage }
    ];

    container.innerHTML = kpis.map(kpi => `
        <article class="executive-kpi-card" style="--kpi-accent:${kpi.accent}" title="${qaEscapeHtml(kpi.help)}">
            <div class="kpi-top">
                <div class="kpi-icon"><i class="fas ${kpi.icon}"></i></div>
                <div class="kpi-label">${qaEscapeHtml(kpi.label)}</div>
            </div>
            <div class="kpi-value">${qaEscapeHtml(String(kpi.value))}</div>
            <div class="kpi-trend ${kpi.trendType === 'down' ? 'down' : kpi.trendType === 'neutral' ? 'neutral' : ''}">
                <i class="fas ${kpi.trendType === 'down' ? 'fa-arrow-trend-down' : kpi.trendType === 'neutral' ? 'fa-minus' : 'fa-arrow-trend-up'}"></i>
                ${qaEscapeHtml(kpi.trend)}
            </div>
            ${kpi.progress != null ? renderKpiProgress(kpi.progress) : renderKpiSparkline(kpi.spark || [], kpi.accent)}
        </article>
    `).join('');
}

function renderKpiProgress(value) {
    const clamped = Math.max(0, Math.min(100, Number(value || 0)));
    return `<div class="kpi-progress"><span style="width:${clamped}%"></span></div>`;
}

function renderKpiSparkline(values, color) {
    if (!values.length) return '';
    const max = Math.max(...values);
    const min = Math.min(...values);
    const points = values.map((value, index) => {
        const x = (index / Math.max(1, values.length - 1)) * 170;
        const y = 36 - (((value - min) / Math.max(1, max - min)) * 28);
        return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');
    return `
        <svg class="kpi-sparkline" viewBox="0 0 170 42" preserveAspectRatio="none" aria-hidden="true">
            <polyline points="${points}" fill="none" stroke="${color}" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"></polyline>
        </svg>
    `;
}

function renderDashboardCharts(metrics) {
    if (!window.Chart) {
        renderChartFallbacks(metrics);
        return;
    }

    renderExecutionsTrendChart(metrics);
    renderStatusDonutChart(metrics);
    renderModuleBarChart(metrics);
    renderPerformanceChart(metrics);
}

function renderExecutionsTrendChart(metrics) {
    const labels = getLastSevenDayLabels();
    const byDay = labels.map(label => {
        const dayExecutions = metrics.executions.filter(exec => formatShortDate(exec.timestamp) === label);
        return {
            total: dayExecutions.length,
            success: dayExecutions.filter(exec => exec.status === 'success').length,
            failed: dayExecutions.filter(exec => exec.status === 'failed').length
        };
    });

    createOrUpdateChart('executionsTrendChart', {
        type: 'line',
        data: {
            labels,
            datasets: [
                chartDataset('Execuções', byDay.map(d => d.total), '#2563EB', true),
                chartDataset('Sucessos', byDay.map(d => d.success), '#10B981', false),
                chartDataset('Falhas', byDay.map(d => d.failed), '#EF4444', false)
            ]
        },
        options: executiveChartOptions()
    });
}

function renderStatusDonutChart(metrics) {
    createOrUpdateChart('statusDonutChart', {
        type: 'doughnut',
        data: {
            labels: ['Sucesso', 'Falha', 'Em execução', 'Cancelado'],
            datasets: [{
                data: [metrics.successes.length, metrics.failures.length, metrics.running.length, metrics.cancelled.length],
                backgroundColor: ['#10B981', '#EF4444', '#2563EB', '#94A3B8'],
                borderWidth: 0,
                hoverOffset: 6
            }]
        },
        options: {
            ...executiveChartOptions(),
            cutout: '68%',
            scales: {}
        }
    });
}

function renderModuleBarChart(metrics) {
    const modules = getModuleDashboardStats(metrics);
    createOrUpdateChart('moduleBarChart', {
        type: 'bar',
        data: {
            labels: modules.map(item => item.name),
            datasets: [
                { label: 'Testes', data: modules.map(item => item.total), backgroundColor: '#2563EB', borderRadius: 8 },
                { label: 'Taxa de falha %', data: modules.map(item => item.failureRate), backgroundColor: '#F97316', borderRadius: 8 }
            ]
        },
        options: {
            ...executiveChartOptions(),
            indexAxis: 'y'
        }
    });
}

function renderPerformanceChart(metrics) {
    const slowest = metrics.executions
        .slice()
        .sort((a, b) => (b.durationMs || 0) - (a.durationMs || 0))
        .slice(0, 6);
    createOrUpdateChart('performanceChart', {
        type: 'bar',
        data: {
            labels: slowest.map(exec => truncateLabel(exec.testName, 18)),
            datasets: [{
                label: 'Minutos',
                data: slowest.map(exec => Math.max(1, Math.round((exec.durationMs || 0) / 60000))),
                backgroundColor: slowest.map((_, index) => index < 2 ? '#EF4444' : '#F59E0B'),
                borderRadius: 8
            }]
        },
        options: executiveChartOptions()
    });
}

function createOrUpdateChart(canvasId, config) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    if (dashboardCharts[canvasId]) {
        dashboardCharts[canvasId].destroy();
    }
    dashboardCharts[canvasId] = new Chart(canvas, config);
}

function chartDataset(label, data, color, fill) {
    return {
        label,
        data,
        borderColor: color,
        backgroundColor: fill ? `${color}22` : color,
        fill,
        tension: 0.38,
        pointRadius: 3,
        pointHoverRadius: 5,
        borderWidth: 2
    };
}

function executiveChartOptions() {
    return {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: { labels: { usePointStyle: true, boxWidth: 8, font: { family: 'Inter', size: 11 } } },
            tooltip: { backgroundColor: '#0F172A', padding: 10, titleFont: { family: 'Inter' }, bodyFont: { family: 'Inter' } }
        },
        scales: {
            x: { grid: { display: false }, ticks: { color: '#64748B', font: { family: 'Inter', size: 11 } } },
            y: { beginAtZero: true, grid: { color: '#EEF2F7' }, ticks: { color: '#64748B', font: { family: 'Inter', size: 11 } } }
        }
    };
}

function renderChartFallbacks(metrics) {
    ['executionsTrendChart', 'statusDonutChart', 'moduleBarChart', 'performanceChart'].forEach(id => {
        const canvas = document.getElementById(id);
        if (!canvas) return;
        const parent = canvas.parentElement;
        parent.innerHTML = `<div class="empty-state"><div class="empty-state-icon"><i class="fas fa-chart-line"></i></div><div class="empty-state-title">Gráfico indisponível</div><div class="empty-state-sub">Chart.js não foi carregado.</div></div>`;
    });
}

function renderDashboardRecentExecutions() {
    const body = document.getElementById('dashboardRecentExecutionsBody');
    if (!body) return;

    let rows = getDashboardExecutions();
    const search = dashboardRecentState.search.toLowerCase().trim();
    if (dashboardRecentState.filter !== 'all') {
        rows = rows.filter(exec => exec.status === dashboardRecentState.filter);
    }
    if (search) {
        rows = rows.filter(exec => `${exec.testName} ${exec.module} ${exec.owner}`.toLowerCase().includes(search));
    }

    rows.sort((a, b) => compareDashboardRows(a, b, dashboardRecentState.sortKey, dashboardRecentState.sortDir));
    const totalRows = rows.length;
    const start = (dashboardRecentState.page - 1) * dashboardRecentState.pageSize;
    const pageRows = rows.slice(start, start + dashboardRecentState.pageSize);

    if (!totalRows) {
        body.innerHTML = `<tr><td colspan="8"><div class="empty-state" style="padding: 18px;"><div class="empty-state-sub">Nenhuma execução registrada ainda.</div></div></td></tr>`;
        renderDashboardRecentExecutionsPagination(0);
        return;
    }

    body.innerHTML = pageRows.map(exec => `
        <tr>
            <td><span class="exec-test-name">${qaEscapeHtml(exec.testName)}</span><span class="exec-subtext">${qaEscapeHtml(exec.menu || 'Fluxo automatizado')}</span></td>
            <td>${qaEscapeHtml(getModuleLabel(exec.module))}</td>
            <td>${qaEscapeHtml(getTestTypeLabel(exec.testType))}</td>
            <td>${renderDashboardStatusBadge(exec.status, exec.status === 'failed' ? exec.lastError : '')}</td>
            <td>${formatDuration(exec.durationMs)}</td>
            <td>${formatRelativeTime(exec.timestamp)}</td>
            <td>${qaEscapeHtml(exec.owner)}</td>
            <td>${qaEscapeHtml(exec.environment)}</td>
        </tr>
    `).join('') || `
        <tr><td colspan="8"><div class="empty-state" style="padding: 20px;"><div class="empty-state-sub">Nenhuma execução encontrada para os filtros atuais</div></div></td></tr>
    `;
    renderDashboardRecentExecutionsPagination(totalRows);
}

function renderDashboardRecentExecutionsPagination(totalRows) {
    const total = Number(totalRows || 0);
    const pageSize = dashboardRecentState.pageSize;
    const maxPage = Math.max(1, Math.ceil(total / pageSize));
    const page = Math.min(maxPage, Math.max(1, dashboardRecentState.page));
    dashboardRecentState.page = page;

    const start = total ? (page - 1) * pageSize : 0;
    const end = total ? Math.min(start + pageSize, total) : 0;

    const pageInfo = document.getElementById('dashboardPaginationInfo');
    if (pageInfo) {
        pageInfo.textContent = total ? `${start + 1}-${end} de ${total} registros` : '0 registros';
    }

    const controls = document.querySelectorAll('.table-pagination .pagination-btn');
    if (controls.length >= 2) {
        controls[0].disabled = page <= 1;
        controls[1].disabled = page >= maxPage;
    }
}

function dashboardSetRecentSearch(value) {
    dashboardRecentState.search = value || '';
    dashboardRecentState.page = 1;
    renderDashboardRecentExecutions();
}

function dashboardSetRecentFilter(value) {
    dashboardRecentState.filter = value || 'all';
    dashboardRecentState.page = 1;
    renderDashboardRecentExecutions();
}

function dashboardSortRecent(key) {
    if (dashboardRecentState.sortKey === key) {
        dashboardRecentState.sortDir = dashboardRecentState.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
        dashboardRecentState.sortKey = key;
        dashboardRecentState.sortDir = 'asc';
    }
    renderDashboardRecentExecutions();
}

function dashboardChangeRecentPage(delta) {
    const total = getDashboardExecutions().length;
    const maxPage = Math.max(1, Math.ceil(total / dashboardRecentState.pageSize));
    dashboardRecentState.page = Math.min(maxPage, Math.max(1, dashboardRecentState.page + delta));
    renderDashboardRecentExecutions();
}

function renderDashboardInsights(metrics) {
    const container = document.getElementById('dashboardInsightsList');
    if (!container) return;
    if (!metrics.executions.length) {
        container.innerHTML = `
            <div class="insight-card info">
                <div class="insight-icon"><i class="fas fa-circle-info"></i></div>
                <div>
                    <div class="insight-title">Sem histórico de execuções</div>
                    <div class="insight-copy">Execute um teste para preencher a taxa de sucesso, falhas e duração.</div>
                </div>
            </div>
            <div class="insight-card info">
                <div class="insight-icon"><i class="fas fa-database"></i></div>
                <div>
                    <div class="insight-title">Métricas baseadas em dados reais</div>
                    <div class="insight-copy">O dashboard não usa mais dados sintéticos quando não há execuções registradas.</div>
                </div>
            </div>
        `;
        return;
    }
    const modules = getModuleDashboardStats(metrics);
    const worstModule = modules.slice().sort((a, b) => b.failureRate - a.failureRate)[0];
    const insights = [
        metrics.failures.length >= 3
            ? { type: 'critical', icon: 'fa-triangle-exclamation', title: `${metrics.failures.length} testes com falha recente`, copy: 'Priorize análise de recorrência e bloqueios em pipeline.' }
            : { type: 'success', icon: 'fa-circle-check', title: 'Falhas sob controle', copy: 'O volume de falhas recentes está dentro do limite operacional.' },
        worstModule && worstModule.failureRate > 0
            ? { type: 'warning', icon: 'fa-chart-simple', title: `${worstModule.name} concentra maior taxa de erro`, copy: `${worstModule.failureRate}% de falha nas execuções mapeadas.` }
            : { type: 'success', icon: 'fa-shield-halved', title: 'Módulos sem concentração crítica', copy: 'Não há frente funcional dominando as falhas atuais.' },
        { type: metrics.avgDurationMs > 90000 ? 'warning' : 'info', icon: 'fa-stopwatch', title: 'Performance monitorada', copy: `Tempo médio atual em ${formatDuration(metrics.avgDurationMs)}.` },
        { type: 'success', icon: 'fa-fire-flame-simple', title: 'Smoke Tests estão estáveis', copy: `${metrics.smokeTests} cenário(s) prontos para validação rápida.` }
    ];

    container.innerHTML = insights.map(item => `
        <div class="insight-card ${item.type}">
            <div class="insight-icon"><i class="fas ${item.icon}"></i></div>
            <div>
                <div class="insight-title">${qaEscapeHtml(item.title)}</div>
                <div class="insight-copy">${qaEscapeHtml(item.copy)}</div>
            </div>
        </div>
    `).join('');
}

function renderDashboardAiMetrics(metrics) {
    const container = document.getElementById('dashboardAiMetrics');
    if (!container) return;
    if (!metrics.executions.length) {
        container.innerHTML = `
            <div class="empty-state" style="padding: 18px;">
                <div class="empty-state-sub">Sem telemetria de execução para exibir.</div>
            </div>
        `;
        return;
    }

    const last = metrics.executions
        .slice()
        .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))[0];

    const items = [
        { value: metrics.executions.length, label: 'Execuções registradas' },
        { value: metrics.successes.length, label: 'Sucessos' },
        { value: metrics.failures.length, label: 'Falhas' },
        { value: formatDuration(metrics.avgDurationMs), label: 'Tempo médio' },
        { value: last ? formatRelativeTime(last.timestamp) : '-', label: 'Última execução' },
        { value: getDashboardEnvironmentLabel(), label: 'Ambiente' }
    ];

    container.innerHTML = items.map(item => `
        <div class="ai-metric">
            <div class="ai-metric-value">${qaEscapeHtml(String(item.value))}</div>
            <div class="ai-metric-label">${qaEscapeHtml(item.label)}</div>
        </div>
    `).join('');
}

function renderDashboardTimeline(metrics) {
    const container = document.getElementById('dashboardTimeline');
    if (!container) return;
    const latest = metrics.executions.slice().sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp)).slice(0, 5);
    const items = latest.map(exec => ({
        icon: exec.status === 'success' ? 'fa-check' : exec.status === 'failed' ? 'fa-xmark' : 'fa-play',
        title: `${getDashboardStatusText(exec.status)} - ${exec.testName}`,
        copy: `${formatRelativeTime(exec.timestamp)} | ${getModuleLabel(exec.module)} | ${formatDuration(exec.durationMs)}`
    }));

    if (!items.length) {
        container.innerHTML = `<div class="empty-state" style="padding: 18px;"><div class="empty-state-sub">Nenhuma execução registrada ainda.</div></div>`;
        return;
    }

    container.innerHTML = items.map(item => `
        <div class="timeline-item">
            <div class="timeline-dot"><i class="fas ${item.icon}"></i></div>
            <div>
                <div class="timeline-title">${qaEscapeHtml(item.title)}</div>
                <div class="timeline-copy">${qaEscapeHtml(item.copy)}</div>
            </div>
        </div>
    `).join('');
}

function renderCoverageView() {
    const metrics = buildDashboardMetrics();
    const modules = getModuleDashboardStats(metrics);
    const moduleGrid = document.getElementById('coverageModuleGrid');
    const summaryGrid = document.getElementById('coverageSummaryGrid');

    if (moduleGrid) {
        moduleGrid.innerHTML = modules.map(module => {
            const coverage = savedTests.length ? Math.round((module.total / savedTests.length) * 100) : 0;
            return `
                <div class="insight-card info">
                    <div class="insight-icon"><i class="fas fa-layer-group"></i></div>
                    <div style="width: 100%;">
                        <div class="insight-title">${qaEscapeHtml(module.name)}</div>
                        <div class="insight-copy">${module.total} teste(s) | ${module.failureRate}% falha</div>
                        <div class="kpi-progress"><span style="width: ${coverage}%"></span></div>
                    </div>
                </div>
            `;
        }).join('') || `
            <div class="empty-state">
                <div class="empty-state-icon"><i class="fas fa-eye"></i></div>
                <div class="empty-state-title">Sem dados de cobertura</div>
                <div class="empty-state-sub">Cadastre testes por modulo para visualizar a distribuicao.</div>
            </div>
        `;
    }

    if (summaryGrid) {
        const automatedModules = new Set(savedTests.map(test => test.module).filter(Boolean)).size;
        const totalModules = Object.keys(menuStructure || {}).length;
        const items = [
            { value: `${metrics.moduleCoverage}%`, label: 'Cobertura geral' },
            { value: automatedModules, label: 'Modulos com testes' },
            { value: totalModules, label: 'Modulos mapeados' },
            { value: metrics.smokeTests, label: 'Smoke tests' },
            { value: metrics.regressionTests, label: 'Regressao' },
            { value: metrics.aiTests, label: 'Testes com IA' }
        ];
        summaryGrid.innerHTML = items.map(item => `
            <div class="ai-metric light">
                <div class="ai-metric-value">${qaEscapeHtml(String(item.value))}</div>
                <div class="ai-metric-label">${qaEscapeHtml(item.label)}</div>
            </div>
        `).join('');
    }
}

function renderAlertsView() {
    const metrics = buildDashboardMetrics();
    const modules = getModuleDashboardStats(metrics);
    const worstModule = modules.slice().sort((a, b) => b.failureRate - a.failureRate)[0];
    const alerts = [
        metrics.criticalFailures > 0
            ? { type: 'critical', icon: 'fa-triangle-exclamation', title: `${metrics.criticalFailures} falha(s) critica(s)`, copy: 'Revisar cenarios prioritarios antes da proxima janela de regressao.' }
            : { type: 'success', icon: 'fa-shield-halved', title: 'Sem falhas criticas', copy: 'Nenhum bloqueio critico foi identificado nas execucoes recentes.' },
        metrics.failures.length > 0
            ? { type: 'warning', icon: 'fa-circle-xmark', title: `${metrics.failures.length} execucao(oes) com falha`, copy: 'Acompanhar recorrencia e evidencias no relatorio de execucao.' }
            : { type: 'success', icon: 'fa-circle-check', title: 'Execucoes estaveis', copy: 'O historico recente nao indica falhas abertas.' },
        worstModule && worstModule.failureRate > 0
            ? { type: 'warning', icon: 'fa-chart-line', title: `${worstModule.name} requer atencao`, copy: `Modulo com ${worstModule.failureRate}% de falha no recorte atual.` }
            : { type: 'info', icon: 'fa-layer-group', title: 'Modulos equilibrados', copy: 'Nenhum modulo concentra falhas no momento.' },
        metrics.avgDurationMs > 90000
            ? { type: 'warning', icon: 'fa-stopwatch', title: 'Tempo medio elevado', copy: `Tempo medio atual: ${formatDuration(metrics.avgDurationMs)}.` }
            : { type: 'success', icon: 'fa-gauge-high', title: 'Performance sob controle', copy: `Tempo medio atual: ${formatDuration(metrics.avgDurationMs)}.` }
    ];

    const container = document.getElementById('alertsList');
    if (container) {
        container.innerHTML = alerts.map(item => `
            <div class="insight-card ${item.type}">
                <div class="insight-icon"><i class="fas ${item.icon}"></i></div>
                <div>
                    <div class="insight-title">${qaEscapeHtml(item.title)}</div>
                    <div class="insight-copy">${qaEscapeHtml(item.copy)}</div>
                </div>
            </div>
        `).join('');
    }
}

function renderSchedulesView() {
    const container = document.getElementById('schedulesList');
    if (!container) return;

    const schedules = getSchedules();
    if (!schedules.length) {
        container.innerHTML = `
            <div class="timeline-item">
                <div class="timeline-dot"><i class="fas fa-calendar-day"></i></div>
                <div>
                    <div class="timeline-title">Nenhum agendamento cadastrado</div>
                    <div class="timeline-copy">Crie um agendamento local para organizar janelas de execucao. A integracao com backend virá depois.</div>
                </div>
            </div>
        `;
        return;
    }

    container.innerHTML = schedules.map(item => `
        <div class="timeline-item">
            <div class="timeline-dot"><i class="fas ${item.icon}"></i></div>
            <div>
                <div class="timeline-title">${qaEscapeHtml(item.title)}</div>
                <div class="timeline-copy">${qaEscapeHtml(item.copy)}</div>
                <div style="display:flex; gap:10px; margin-top:10px; flex-wrap:wrap;">
                    <span class="status-badge ${item.enabled ? 'success' : 'warning'}"><i class="fas fa-circle"></i>${item.enabled ? 'Ativo' : 'Pausado'}</span>
                    <span class="status-badge info"><i class="fas fa-layer-group"></i>${qaEscapeHtml(item.environment || 'HOMOLOGAÇÃO')}</span>
                    <span class="status-badge info"><i class="fas fa-clock"></i>${qaEscapeHtml(item.window || '')}</span>
                    <button class="btn-secondary" style="padding: 6px 10px;" onclick="openScheduleEditor('${item.id}')">
                        <i class="fas fa-pen"></i>
                        <span>Editar</span>
                    </button>
                </div>
            </div>
        </div>
    `).join('');
}

const ENVIRONMENTS_STORAGE_KEY = 'qaAgentEnvironments.v1';
let environmentEditorCurrentId = null;

function getEnvironments() {
    try {
        const raw = localStorage.getItem(ENVIRONMENTS_STORAGE_KEY);
        if (raw) {
            const parsed = JSON.parse(raw);
            if (Array.isArray(parsed)) return parsed;
        }
    } catch (e) {}

    const cfg = JSON.parse(localStorage.getItem('qaAgentConfig') || '{}');
    const baseUrl = cfg['base.url'] || cfg.baseUrl || '';
    const envKey = cfg.environment || 'test';

    const defaults = [
        { id: String(Date.now()) + '-env-1', name: 'Testes', key: 'test', baseUrl, notes: '', enabled: true },
        { id: String(Date.now()) + '-env-2', name: 'Produção', key: 'prod', baseUrl: '', notes: '', enabled: false }
    ];

    const normalized = defaults.map(item => ({
        ...item,
        baseUrl: item.key === envKey && baseUrl ? baseUrl : item.baseUrl
    }));

    saveEnvironments(normalized);
    return normalized;
}

function saveEnvironments(list) {
    try {
        localStorage.setItem(ENVIRONMENTS_STORAGE_KEY, JSON.stringify(list || []));
    } catch (e) {}
}

function renderEnvironmentsView() {
    const container = document.getElementById('environmentsList');
    if (!container) return;

    const list = getEnvironments();
    const cfg = JSON.parse(localStorage.getItem('qaAgentConfig') || '{}');
    const activeKey = (cfg.environment || localStorage.getItem('qaAgentEnvironment') || 'test');

    if (!list.length) {
        container.innerHTML = `
            <div class="insight-card info">
                <div class="insight-icon"><i class="fas fa-layer-group"></i></div>
                <div>
                    <div class="insight-title">Nenhum ambiente cadastrado</div>
                    <div class="insight-copy">Crie um perfil para aplicar base URL e chave de ambiente.</div>
                </div>
            </div>
        `;
        return;
    }

    container.innerHTML = list.map(env => {
        const isActive = env.key === activeKey;
        const baseUrlLabel = env.baseUrl ? env.baseUrl : 'sem URL configurada';
        const status = env.enabled ? 'success' : 'warning';
        const statusText = env.enabled ? 'Ativo' : 'Pausado';
        return `
            <div class="insight-card ${isActive ? 'success' : 'info'}" style="align-items: flex-start;">
                <div class="insight-icon"><i class="fas fa-window-restore"></i></div>
                <div style="flex: 1;">
                    <div class="insight-title">${qaEscapeHtml(env.name || env.key || 'Ambiente')}</div>
                    <div class="insight-copy" style="margin-top: 4px;">${qaEscapeHtml(baseUrlLabel)}</div>
                    ${env.notes ? `<div class="insight-copy" style="margin-top: 6px; opacity: 0.9;">${qaEscapeHtml(env.notes)}</div>` : ''}
                    <div style="display:flex; gap:10px; margin-top:10px; flex-wrap:wrap;">
                        <span class="status-badge ${status}"><i class="fas fa-circle"></i>${statusText}</span>
                        <span class="status-badge info"><i class="fas fa-tag"></i>${qaEscapeHtml(env.key || '')}</span>
                        ${isActive ? `<span class="status-badge success"><i class="fas fa-bolt"></i>Em uso</span>` : ''}
                        <button class="btn-secondary" style="padding: 6px 10px;" onclick="openEnvironmentEditor('${env.id}')">
                            <i class="fas fa-pen"></i>
                            <span>Editar</span>
                        </button>
                        <button class="btn-primary" style="padding: 6px 10px;" onclick="applyEnvironment('${env.id}')">
                            <i class="fas fa-check"></i>
                            <span>Aplicar</span>
                        </button>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function openEnvironmentEditor(environmentId) {
    const modal = document.getElementById('environmentModal');
    if (!modal) return;

    const envs = getEnvironments();
    const existing = envs.find(e => e.id === environmentId) || null;

    environmentEditorCurrentId = existing ? existing.id : null;

    document.getElementById('environmentModalTitle').textContent = existing ? 'Editar ambiente' : 'Novo ambiente';
    document.getElementById('environmentNameInput').value = existing?.name || '';
    document.getElementById('environmentKeyInput').value = existing?.key || 'test';
    document.getElementById('environmentEnabledInput').value = String(existing?.enabled ?? true);
    document.getElementById('environmentBaseUrlInput').value = existing?.baseUrl || '';
    document.getElementById('environmentNotesInput').value = existing?.notes || '';

    modal.classList.remove('hidden');
}

function closeEnvironmentEditor() {
    const modal = document.getElementById('environmentModal');
    if (!modal) return;
    modal.classList.add('hidden');
    environmentEditorCurrentId = null;
}

function saveEnvironmentEditor() {
    const envs = getEnvironments();

    const name = document.getElementById('environmentNameInput')?.value?.trim() || '';
    const key = document.getElementById('environmentKeyInput')?.value || 'test';
    const baseUrl = document.getElementById('environmentBaseUrlInput')?.value?.trim() || '';

    if (!name) {
        showAlert('Informe o nome do ambiente', 'warning');
        return;
    }
    if (!baseUrl) {
        showAlert('Informe a Base URL do ambiente', 'warning');
        return;
    }

    const data = {
        id: environmentEditorCurrentId || String(Date.now()),
        name,
        key,
        baseUrl,
        notes: document.getElementById('environmentNotesInput')?.value?.trim() || '',
        enabled: (document.getElementById('environmentEnabledInput')?.value || 'true') === 'true'
    };

    const next = envs.slice();
    const idx = next.findIndex(e => e.id === data.id);
    if (idx >= 0) next[idx] = { ...next[idx], ...data };
    else next.unshift(data);

    saveEnvironments(next);
    closeEnvironmentEditor();
    renderEnvironmentsView();
    showAlert('Ambiente salvo localmente', 'success');
}

async function applyEnvironment(environmentId) {
    const envs = getEnvironments();
    const env = envs.find(e => e.id === environmentId);
    if (!env) return;

    localStorage.setItem('qaAgentEnvironment', env.key);

    const current = JSON.parse(localStorage.getItem('qaAgentConfig') || '{}');
    const merged = {
        'base.url': env.baseUrl || current['base.url'] || current.baseUrl || '',
        'user.username': current['user.username'] || current.username || (document.getElementById('username')?.value || ''),
        'user.password': current['user.password'] || current.password || (document.getElementById('password')?.value || ''),
        'timeout.default': Number(current['timeout.default'] || current.timeout || document.getElementById('timeout')?.value || 30),
        'environment': env.key,
        'browser.type': current['browser.type'] || current.browser || (document.getElementById('browser')?.value || 'chromium')
    };

    localStorage.setItem('qaAgentConfig', JSON.stringify(merged));

    if (document.getElementById('environment')) document.getElementById('environment').value = env.key;
    if (document.getElementById('baseUrl')) document.getElementById('baseUrl').value = env.baseUrl;

    try {
        const response = await fetch('/api/save-config', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(merged)
        });
        const data = await response.json();
        if (data.status === 'success') {
            showAlert('Ambiente aplicado e salvo', 'success');
        } else {
            showAlert('Ambiente aplicado (falha ao salvar no servidor)', 'warning');
        }
    } catch (e) {
        showAlert('Ambiente aplicado (offline)', 'warning');
    }
}

const SCHEDULES_STORAGE_KEY = 'qaAgentSchedules.v1';
let scheduleEditorCurrentId = null;

function getSchedules() {
    try {
        const raw = localStorage.getItem(SCHEDULES_STORAGE_KEY);
        if (raw) {
            const parsed = JSON.parse(raw);
            if (Array.isArray(parsed)) return parsed;
        }
    } catch (e) {}

    const defaults = [
        { id: String(Date.now()) + '-1', icon: 'fa-moon', title: 'Regressao noturna', copy: 'Todos os dias | ambiente HOMOLOGAÇÃO', environment: 'HOMOLOGAÇÃO', window: '22:00', enabled: true },
        { id: String(Date.now()) + '-2', icon: 'fa-bolt', title: 'Smoke de abertura', copy: 'Dias úteis | execucao rapida', environment: 'HOMOLOGAÇÃO', window: '08:00', enabled: true },
        { id: String(Date.now()) + '-3', icon: 'fa-code-branch', title: 'Validacao pos-deploy', copy: 'Manual sob demanda | aguardando integracao CI/CD', environment: 'HOMOLOGAÇÃO', window: '', enabled: false }
    ];
    saveSchedules(defaults);
    return defaults;
}

function saveSchedules(list) {
    try {
        localStorage.setItem(SCHEDULES_STORAGE_KEY, JSON.stringify(list || []));
    } catch (e) {}
}

function openScheduleEditor(scheduleId) {
    const modal = document.getElementById('scheduleModal');
    if (!modal) return;

    const schedules = getSchedules();
    const existing = schedules.find(s => s.id === scheduleId) || null;

    scheduleEditorCurrentId = existing ? existing.id : null;

    document.getElementById('scheduleModalTitle').textContent = existing ? 'Editar agendamento' : 'Novo agendamento';
    document.getElementById('scheduleTitleInput').value = existing?.title || '';
    document.getElementById('scheduleCopyInput').value = existing?.copy || '';
    document.getElementById('scheduleEnvInput').value = existing?.environment || 'HOMOLOGAÇÃO';
    document.getElementById('scheduleWindowInput').value = existing?.window || '';
    document.getElementById('scheduleEnabledInput').value = String(existing?.enabled ?? true);
    document.getElementById('scheduleIconInput').value = existing?.icon || 'fa-calendar-day';

    modal.classList.remove('hidden');
}

function closeScheduleEditor() {
    const modal = document.getElementById('scheduleModal');
    if (!modal) return;
    modal.classList.add('hidden');
    scheduleEditorCurrentId = null;
}

function saveScheduleEditor() {
    const schedules = getSchedules();

    const title = document.getElementById('scheduleTitleInput')?.value?.trim() || '';
    if (!title) {
        showAlert('Informe o nome do agendamento', 'warning');
        return;
    }

    const data = {
        id: scheduleEditorCurrentId || String(Date.now()),
        title,
        copy: document.getElementById('scheduleCopyInput')?.value?.trim() || '',
        environment: document.getElementById('scheduleEnvInput')?.value || 'HOMOLOGAÇÃO',
        window: document.getElementById('scheduleWindowInput')?.value?.trim() || '',
        enabled: (document.getElementById('scheduleEnabledInput')?.value || 'true') === 'true',
        icon: document.getElementById('scheduleIconInput')?.value || 'fa-calendar-day'
    };

    const next = schedules.slice();
    const idx = next.findIndex(s => s.id === data.id);
    if (idx >= 0) next[idx] = { ...next[idx], ...data };
    else next.unshift(data);

    saveSchedules(next);
    closeScheduleEditor();
    renderSchedulesView();
    showAlert('Agendamento salvo localmente', 'success');
}

function getModuleDashboardStats(metrics) {
    const moduleMap = new Map();
    savedTests.forEach(test => {
        const key = test.module || 'sem_modulo';
        if (!moduleMap.has(key)) moduleMap.set(key, { key, name: getModuleLabel(key), total: 0, failures: 0, failureRate: 0 });
        moduleMap.get(key).total += 1;
    });
    metrics.executions.forEach(exec => {
        const key = exec.module || 'sem_modulo';
        if (!moduleMap.has(key)) moduleMap.set(key, { key, name: getModuleLabel(key), total: 0, failures: 0, failureRate: 0 });
        if (exec.status === 'failed') moduleMap.get(key).failures += 1;
    });
    return Array.from(moduleMap.values())
        .map(item => ({ ...item, failureRate: item.total ? Math.round((item.failures / Math.max(item.total, 1)) * 100) : 0 }))
        .sort((a, b) => b.total - a.total)
        .slice(0, 8);
}

function calculateHealthScore(successRate, criticalFailures, failures, running) {
    if (!savedTests.length && !failures && !running) return 0;
    return Math.max(0, Math.min(100, successRate - (criticalFailures * 12) - Math.min(20, failures * 3) + Math.min(5, running)));
}

function renderDashboardStatusBadge(status, details) {
    const title = details ? ` title="${qaEscapeHtml(details)}"` : '';
    return `<span class="status-badge ${status}"${title}><i class="fas fa-circle"></i>${qaEscapeHtml(getDashboardStatusText(status))}</span>`;
}

function getDashboardStatusText(status) {
    return {
        success: 'Sucesso',
        failed: 'Falha',
        running: 'Em execução',
        cancelled: 'Cancelado'
    }[status] || 'Em execução';
}

function compareDashboardRows(a, b, key, dir) {
    const mappedKey = key === 'duration' ? 'durationMs' : key;
    const aValue = mappedKey === 'timestamp' ? new Date(a[mappedKey]).getTime() : (a[mappedKey] || '');
    const bValue = mappedKey === 'timestamp' ? new Date(b[mappedKey]).getTime() : (b[mappedKey] || '');
    const result = aValue > bValue ? 1 : aValue < bValue ? -1 : 0;
    return dir === 'asc' ? result : -result;
}

function getDashboardEnvironmentLabel() {
    const configEnv = document.getElementById('environment')?.value || '';
    const env = (configEnv || localStorage.getItem('qaAgentEnvironment') || 'local').toLowerCase();
    if (env.includes('prod')) return 'PRODUÇÃO';
    if (env.includes('test') || env.includes('staging') || env.includes('homolog')) return 'HOMOLOGAÇÃO';
    return 'LOCAL';
}

function getModuleLabel(moduleKey) {
    return menuStructure?.[moduleKey]?.name || moduleKey || 'Sem módulo';
}

function getTestTypeLabel(type) {
    return {
        smoke: 'Smoke',
        regression: 'Regressão',
        functional: 'Funcional',
        integration: 'Integração'
    }[type] || type || 'Funcional';
}

function formatDuration(durationMs) {
    const ms = Number(durationMs || 0);
    if (!ms) return '0s';
    const seconds = Math.round(ms / 1000);
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    const rest = seconds % 60;
    return rest ? `${minutes}m ${rest}s` : `${minutes}m`;
}

function formatRelativeTime(timestamp) {
    const date = new Date(timestamp);
    if (Number.isNaN(date.getTime())) return 'sem registro';
    const diffMs = Date.now() - date.getTime();
    const diffMinutes = Math.max(0, Math.round(diffMs / 60000));
    if (diffMinutes < 1) return 'agora';
    if (diffMinutes < 60) return `há ${diffMinutes} minutos`;
    const diffHours = Math.round(diffMinutes / 60);
    if (diffHours < 24) return `há ${diffHours} horas`;
    return date.toLocaleDateString('pt-BR');
}

function getLastSevenDayLabels() {
    return Array.from({ length: 7 }, (_, index) => {
        const date = new Date();
        date.setDate(date.getDate() - (6 - index));
        return date.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
    });
}

function formatShortDate(timestamp) {
    return new Date(timestamp).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
}

function truncateLabel(value, maxLength) {
    const text = String(value || '');
    return text.length > maxLength ? `${text.slice(0, maxLength - 1)}…` : text;
}

function qaEscapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
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
    let executions = [];
    try {
        executions = JSON.parse(localStorage.getItem('qaAgentExecutions') || '[]');
    } catch (error) {
        executions = [];
    }
    if (!Array.isArray(executions)) executions = [];

    const id = execution?.executionId || execution?.id;
    if (id) {
        const idx = executions.findIndex(item => (item?.executionId || item?.id) === id);
        if (idx >= 0) {
            executions[idx] = { ...executions[idx], ...execution };
        } else {
            executions.push({ ...execution, id: execution.id || id });
        }
    } else {
        executions.push(execution);
    }

    executions.sort((a, b) => new Date(b.timestamp || b.startedAt || 0) - new Date(a.timestamp || a.startedAt || 0));
    localStorage.setItem('qaAgentExecutions', JSON.stringify(executions.slice(0, 200)));
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
    if (!section) return;
    const chevron = header.querySelector('.chevron, .sidebar-inline-chevron');
    
    if (section.classList.contains('open') || !section.classList.contains('hidden')) {
        section.classList.remove('open');
        section.classList.add('hidden');
        if (chevron) chevron.classList.remove('rotate');
    } else {
        section.classList.add('open');
        section.classList.remove('hidden');
        if (chevron) chevron.classList.add('rotate');
    }
}

// Update active navigation item
function updateActiveNav(navId) {
    document.querySelectorAll('.nav-item').forEach(item => {
        item.classList.remove('active');
    });
    const ids = Array.isArray(navId) ? navId : [navId];
    ids.forEach(id => {
        const activeItem = document.getElementById(id);
        if (activeItem) {
            activeItem.classList.add('active');
        }
    });
}

function showEnvironments() {
    hideAllViews();
    document.getElementById('environmentsView')?.classList.remove('hidden');
    updatePageTitle('Ambientes', 'Gerencie perfis para execucoes em diferentes ambientes');
    updateActiveNav('nav-environments');
    saveViewState('environments');
    renderEnvironmentsView();
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
    const views = [
        'dashboardView',
        'testListView',
        'executionsView',
        'newTestForm',
        'menuEditorView',
        'configView',
        'coverageView',
        'alertsView',
        'schedulesView',
        'environmentsView'
    ];
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
    const collapsedRaw = localStorage.getItem(SIDEBAR_COLLAPSED_MODULES_KEY);
    const collapsedSet = new Set();
    try {
        const parsed = JSON.parse(collapsedRaw || '[]');
        if (Array.isArray(parsed)) parsed.forEach(k => collapsedSet.add(String(k)));
    } catch {}

    if (Object.keys(menuStructure).length === 0) {
        menuStructure = JSON.parse(JSON.stringify(defaultMenuStructure));
    }

    console.log('Loading modules:', Object.keys(menuStructure));
    console.log('Menu structure:', menuStructure);
    
    const modulesList = document.getElementById('modulesList');
    if (!modulesList) {
        console.error('modulesList element not found!');
        return;
    }
    
    modulesList.innerHTML = `
        <div class="sidebar-modules-top">
            <button class="sidebar-modules-action" onclick="showMenuEditor()">
                <i class="fas fa-pen-to-square"></i>
                <span>Editar menus</span>
            </button>
        </div>
    `;

    const moduleKeys = Object.keys(menuStructure);
    if (!moduleKeys.length) {
        const empty = document.createElement('div');
        empty.className = 'sidebar-modules-empty';
        empty.textContent = 'Nenhum menu cadastrado';
        modulesList.appendChild(empty);
        return;
    }

    let renderedCount = 0;
    moduleKeys.forEach(moduleKey => {
        const module = menuStructure[moduleKey];
        if (!module || typeof module !== 'object') return;

        const moduleName = isNonEmptyString(module.name)
            ? module.name.trim()
            : (defaultMenuStructure[moduleKey]?.name || qaHumanizeKey(moduleKey) || moduleKey);

        console.log('Creating module:', moduleKey, moduleName);
        
        const moduleDiv = document.createElement('div');
        moduleDiv.className = 'sidebar-module';

        const menuKeys = Object.keys(module.menus || {});
        const expanded = (currentViewState?.moduleKey === moduleKey) || !collapsedSet.has(moduleKey);

        moduleDiv.innerHTML = `
            <div class="sidebar-module-header ${expanded ? 'open' : ''}" id="module-header-${moduleKey}" onclick="toggleModule('${moduleKey}')">
                <div class="sidebar-module-header-title">
                    <i class="far fa-folder-open sidebar-tree-icon"></i>
                    <span class="sidebar-module-header-name">${qaEscapeHtml(moduleName)}</span>
                </div>
                <div class="sidebar-module-header-actions">
                    <button class="sidebar-icon-btn" title="Executar todos os testes deste menu" onclick="event.stopPropagation(); runModuleTests('${moduleKey}')">
                        <i class="fas fa-play"></i>
                    </button>
                    <i class="chevron fas fa-chevron-down" id="chevron-${moduleKey}"></i>
                </div>
            </div>
            <div class="sidebar-module-menus ${expanded ? '' : 'hidden'}" id="menus-${moduleKey}">
                ${menuKeys.map(menuKey => `
                    <div class="sidebar-submenu-row">
                        <button class="sidebar-module-item" id="nav-module-${moduleKey}-${menuKey}" onclick="showModuleTests('${moduleKey}', '${menuKey}')">
                            <span class="sidebar-submenu-name">${qaEscapeHtml(isNonEmptyString(module?.menus?.[menuKey]) ? module.menus[menuKey].trim() : (defaultMenuStructure[moduleKey]?.menus?.[menuKey] || qaHumanizeKey(menuKey) || menuKey))}</span>
                        </button>
                        <button class="sidebar-icon-btn sidebar-icon-btn-secondary" title="Executar testes deste submenu" onclick="event.stopPropagation(); runMenuTests('${moduleKey}', '${menuKey}')">
                            <i class="fas fa-play"></i>
                        </button>
                    </div>
                `).join('')}
            </div>
        `;
        modulesList.appendChild(moduleDiv);
        renderedCount += 1;
    });

    if (renderedCount === 0) {
        const empty = document.createElement('div');
        empty.className = 'sidebar-modules-empty';
        empty.textContent = 'Nenhum menu válido cadastrado';
        modulesList.appendChild(empty);
    }
    
    console.log('Modules loaded successfully');
}

// Toggle module expansion
function toggleModule(moduleKey) {
    console.log('Toggling module:', moduleKey);
    const menusDiv = document.getElementById(`menus-${moduleKey}`);
    const chevron = document.getElementById(`chevron-${moduleKey}`);
    const header = document.getElementById(`module-header-${moduleKey}`);
    
    console.log('Found elements:', menusDiv, chevron);
    if (!menusDiv) return;

    const collapsedRaw = localStorage.getItem(SIDEBAR_COLLAPSED_MODULES_KEY);
    const collapsedSet = new Set();
    try {
        const parsed = JSON.parse(collapsedRaw || '[]');
        if (Array.isArray(parsed)) parsed.forEach(k => collapsedSet.add(String(k)));
    } catch {}
    
    if (menusDiv.classList.contains('hidden')) {
        menusDiv.classList.remove('hidden');
        header?.classList.add('open');
        collapsedSet.delete(String(moduleKey));
        localStorage.setItem(SIDEBAR_COLLAPSED_MODULES_KEY, JSON.stringify(Array.from(collapsedSet)));
        console.log('Module expanded');
    } else {
        menusDiv.classList.add('hidden');
        header?.classList.remove('open');
        collapsedSet.add(String(moduleKey));
        localStorage.setItem(SIDEBAR_COLLAPSED_MODULES_KEY, JSON.stringify(Array.from(collapsedSet)));
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
    
    // Update module select only for new tests. Edit mode populates it with the saved values.
    if (!editingTestId) {
        setTimeout(() => {
            updateModuleSelect();
            console.log('Module select updated in showNewTestForm');
        }, 100);
    }
    
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
async function deleteTest(testId) {
    if (!confirm('Tem certeza que deseja excluir este teste?')) {
        return;
    }
    
    savedTests = savedTests.filter(test => test.id !== testId);
    await deleteTestFromDB(testId);
    
    // Reload the current view
    const currentModule = document.querySelector('[onclick*="showModuleTests"]').getAttribute('onclick').match(/'([^']+)'/)[1];
    const currentMenu = document.querySelector('[onclick*="showModuleTests"]').getAttribute('onclick').match(/'([^']+)'/)[3];
    showModuleTests(currentModule, currentMenu);
}

// Show module tests
async function showModuleTests(moduleKey, menuKey) {
    hideAllViews();
    document.getElementById('testListView').classList.remove('hidden');
    updateActiveNav('nav-modules');
    document.querySelectorAll('.sidebar-module-item').forEach(item => item.classList.remove('active'));
    document.getElementById(`nav-module-${moduleKey}-${menuKey}`)?.classList.add('active');

    // Update page title (Menu = Módulo / Submenu = menuKey)
    document.getElementById('pageTitle').textContent = menuStructure[moduleKey].name || moduleKey;
    document.getElementById('pageSubtitle').textContent = `Submenu: ${menuStructure[moduleKey].menus[menuKey]}`;

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

    // Recarrega do servidor para garantir dados atualizados
    await loadTestsFromDB();

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
                <div class="empty-state-sub">Não há testes cadastrados para este submenu.</div>
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
            <button onclick="runMenuTests('${moduleKey}', '${menuKey}')" class="btn-run" title="Executar todos os testes deste submenu">
                <i class="fas fa-play"></i>
                <span>Executar Submenu</span>
            </button>
            <button onclick="runModuleTests('${moduleKey}')" class="btn-secondary" title="Executar todos os testes deste menu">
                <i class="fas fa-layer-group"></i>
                <span>Executar Menu</span>
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

// Variable to store the generated TestPlan
let currentTestPlan = null;

// Generate TestPlan from natural language description
async function generateTestPlanPreview() {
    const module = document.getElementById('moduleSelect').value;
    const menu = document.getElementById('menuSelect').value;
    const testType = document.getElementById('testTypeSelect').value;
    const priority = document.getElementById('prioritySelect').value;
    const testName = document.getElementById('testNameInput').value;
    const description = document.getElementById('testDescription').value;

    if (!module || !menu || !testName || !description) {
        showAlert('Por favor, preencha todos os campos obrigatórios', 'warning');
        return;
    }

    showLoading('Gerando passos com IA...');

    try {
        // Call the new backend API to generate TestPlan
        const response = await fetch(`${API_BASE}/api/tests/generate`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                module: menuStructure[module]?.name || module,
                menu: menuStructure[module]?.menus?.[menu] || menu,
                testType: testType,
                priority: priority,
                name: testName,
                description: description
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const testPlan = await response.json();
        hideLoading();

        // Store for later
        currentTestPlan = testPlan;

        // Show preview
        showTestPlanPreview(testPlan);

    } catch (error) {
        hideLoading();
        showAlert('Erro ao gerar teste: ' + error.message, 'error');
        console.error(error);
    }
}

// Show preview of generated TestPlan steps
function showTestPlanPreview(testPlan) {
    const previewContainer = document.getElementById('generatedOutput');
    const stepsHtml = testPlan.steps?.map((step, index) => `
        <div class="preview-step" style="
            display: flex;
            align-items: center;
            padding: 12px;
            margin: 8px 0;
            background: var(--bg-input);
            border-radius: 8px;
            border-left: 4px solid ${getActionColor(step.action)};
        ">
            <span style="
                width: 28px;
                height: 28px;
                display: flex;
                align-items: center;
                justify-content: center;
                background: var(--bg-elevated);
                border-radius: 50%;
                font-size: 12px;
                font-weight: 600;
                color: var(--text-primary);
                margin-right: 12px;
            ">${index + 1}</span>
            <div style="flex: 1;">
                <div style="font-weight: 500; color: var(--text-primary);">
                    ${getActionIcon(step.action)} ${getActionLabel(step.action)}
                </div>
                <div style="font-size: 13px; color: var(--text-secondary); margin-top: 2px;">
                    ${step.target ? `<strong>Alvo:</strong> ${step.target}` : ''}
                    ${step.value ? ` | <strong>Valor:</strong> ${step.value}` : ''}
                </div>
            </div>
            <span style="
                font-size: 11px;
                padding: 4px 8px;
                background: ${getActionColor(step.action)}20;
                color: ${getActionColor(step.action)};
                border-radius: 4px;
                text-transform: uppercase;
                font-weight: 600;
            ">${step.action}</span>
        </div>
    `).join('') || '<p style="color: var(--text-muted);">Nenhum passo gerado</p>';

    previewContainer.innerHTML = `
        <div class="panel">
            <div class="panel-header">
                <h3 class="panel-title">
                    <i class="fas fa-list-check"></i>
                    Preview dos Passos Gerados (${testPlan.steps?.length || 0} passos)
                </h3>
            </div>
            <div class="panel-body">
                <div style="margin-bottom: 16px; padding: 12px; background: var(--bg-elevated); border-radius: 8px;">
                    <strong>Nome:</strong> ${testPlan.name}<br>
                    <strong>Módulo:</strong> ${testPlan.module}<br>
                    <strong>Descrição:</strong> ${testPlan.description?.substring(0, 100)}...
                </div>
                <div style="max-height: 400px; overflow-y: auto;">
                    ${stepsHtml}
                </div>
                <div style="display: flex; gap: 12px; margin-top: 16px;">
                    <button onclick="saveTestFromPlan()" class="btn-primary">
                        <i class="fas fa-save"></i>
                        <span>Salvar Teste</span>
                    </button>
                    <button onclick="editDescription()" class="btn-secondary">
                        <i class="fas fa-edit"></i>
                        <span>Editar Descrição</span>
                    </button>
                    <button onclick="closeGeneratedOutput()" class="btn-secondary">
                        <i class="fas fa-times"></i>
                        <span>Cancelar</span>
                    </button>
                </div>
            </div>
        </div>
    `;

    previewContainer.classList.remove('hidden');
    previewContainer.scrollIntoView({ behavior: 'smooth' });
}

// Get action color for UI
function getActionColor(action) {
    const colors = {
        'NAVIGATE': '#3b82f6',  // blue
        'CLICK': '#10b981',     // green
        'FILL': '#f59e0b',      // amber
        'SELECT': '#8b5cf6',    // purple
        'CHECKBOX': '#ec4899',  // pink
        'ASSERT': '#ef4444',    // red
        'WAIT': '#6b7280',      // gray
        'MENU': '#06b6d4'       // cyan
    };
    return colors[action] || '#6b7280';
}

// Get action icon
function getActionIcon(action) {
    const icons = {
        'NAVIGATE': '<i class="fas fa-globe"></i>',
        'CLICK': '<i class="fas fa-hand-pointer"></i>',
        'FILL': '<i class="fas fa-keyboard"></i>',
        'SELECT': '<i class="fas fa-list-check"></i>',
        'CHECKBOX': '<i class="fas fa-check-square"></i>',
        'ASSERT': '<i class="fas fa-check-circle"></i>',
        'WAIT': '<i class="fas fa-clock"></i>',
        'MENU': '<i class="fas fa-bars"></i>'
    };
    return icons[action] || '<i class="fas fa-cog"></i>';
}

// Get action label in Portuguese
function getActionLabel(action) {
    const labels = {
        'NAVIGATE': 'Navegar',
        'CLICK': 'Clicar',
        'FILL': 'Preencher',
        'SELECT': 'Selecionar',
        'CHECKBOX': 'Marcar Checkbox',
        'ASSERT': 'Validar',
        'WAIT': 'Aguardar',
        'MENU': 'Menu'
    };
    return labels[action] || action;
}

// Save test from the generated TestPlan
async function saveTestFromPlan() {
    if (!currentTestPlan) {
        showAlert('Nenhum teste gerado para salvar', 'warning');
        return;
    }

    const module = document.getElementById('moduleSelect').value;
    const menu = document.getElementById('menuSelect').value;
    const testType = document.getElementById('testTypeSelect').value;
    const priority = document.getElementById('prioritySelect').value;

    const test = {
        id: editingTestId || Date.now().toString(),
        module,
        menu,
        testType,
        priority,
        name: currentTestPlan.name,
        description: document.getElementById('testDescription').value,
        testData: JSON.stringify({
            steps: (currentTestPlan.steps || []).map((step, index) => ({
                ...step,
                order: typeof step?.order === 'number' ? step.order : (index + 1)
            }))
        }),
        createdAt: new Date().toISOString(),
        status: 'active'
    };

    try {
        await saveTestToDB(test);
        showAlert('Teste salvo com sucesso!', 'success', () => {
            resetForm();
            showModuleTests(module, menu);
        });
    } catch (error) {
        showAlert('Erro ao salvar teste: ' + error.message, 'error');
    }
}

// Edit description and regenerate
function editDescription() {
    document.getElementById('generatedOutput').classList.add('hidden');
    currentTestPlan = null;
    document.getElementById('testDescription').focus();
}

// Generate test with AI (legacy - keep for compatibility)
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
        const response = await fetch(`${API_BASE}/api/generate-test`, {
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
async function saveTest() {
    let module = document.getElementById('moduleSelect').value;
    let menu = document.getElementById('menuSelect').value;
    const testType = document.getElementById('testTypeSelect').value;
    const priority = document.getElementById('prioritySelect').value;
    const testName = document.getElementById('testNameInput').value;
    const description = document.getElementById('testDescription').value;
    const testData = document.getElementById('testData').value;
    const existingTest = editingTestId ? savedTests.find(t => t.id === editingTestId) : null;

    if (existingTest) {
        module = module || existingTest.module;
        menu = menu || existingTest.menu;
    }
    
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
            
            await saveTestToDB(savedTests[testIndex]);
            
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
        await saveTestToDB(test);
        
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
        renderExecutiveDashboard();
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
async function applyGenerated() {
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
    await saveTestToDB(test);
    
    // This would call a backend endpoint to write files
    fetch(`${API_BASE}/api/apply-test`, {
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
async function saveGeneratedAsDraft() {
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
    await saveTestToDB(test);
    
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
        
        await fetch(`${API_BASE}/api/save-test-metadata`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(metadata)
        });
        
        // Then run the test
        const response = await fetch(`${API_BASE}/api/run-test/${testId}`, {
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
            showAlert(
                'Teste iniciado com sucesso.\n' +
                'O Playwright foi aberto em uma nova janela.\n' +
                'Acompanhe a execução no console do servidor.',
                'success'
            );
        } else if (result.status === 'error') {
            showAlert(result.message || 'Erro ao executar teste', 'error');
        } else {
            showAlert('Teste executado!', 'success', 2000);
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

    updateModuleSelect();

    if (test.module && !menuStructure[test.module]) {
        const option = document.createElement('option');
        option.value = test.module;
        option.textContent = test.module;
        moduleSelect.appendChild(option);
    }
    
    // Set module value
    moduleSelect.value = test.module;
    console.log('Module set to:', test.module);
    
    const menus = menuStructure[test.module]?.menus || {};
    menuSelect.innerHTML = '<option value="">Selecione um menu</option>';
    menuSelect.disabled = false;

    Object.keys(menus).forEach(menuKey => {
        const menu = menus[menuKey];
        const option = document.createElement('option');
        option.value = menuKey;
        option.textContent = typeof menu === 'string' ? menu : (menu?.name || menuKey);
        menuSelect.appendChild(option);
    });

    if (test.menu && !menus[test.menu]) {
        const option = document.createElement('option');
        option.value = test.menu;
        option.textContent = test.menu;
        menuSelect.appendChild(option);
    }

    menuSelect.value = test.menu || '';
    console.log('Menu set to:', test.menu);

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
}

// Run module tests
function runModuleTests(moduleKey) {
    showLoading('Iniciando testes do menu...');
    
    fetch(`${API_BASE}/api/run-module/${moduleKey}`, {
        method: 'POST'
    })
    .then(response => response.json())
    .then(result => {
        hideLoading();
        if (result.status === 'started') {
            showAlert(result.message || 'Testes do menu iniciados! Verifique o console do servidor.', 'success');
        } else {
            showAlert(`Testes do menu ${menuStructure[moduleKey].name} executados!`, 'success');
        }
    })
    .catch(error => {
        hideLoading();
        showAlert('Erro ao executar testes do menu: ' + error.message, 'error');
    });
}

// Run menu tests
function runMenuTests(moduleKey, menuKey) {
    showLoading('Iniciando testes do submenu...');
    
    fetch(`${API_BASE}/api/run-menu/${moduleKey}/${menuKey}`, {
        method: 'POST'
    })
    .then(response => response.json())
    .then(result => {
        hideLoading();
        if (result.status === 'started') {
            showAlert(result.message || 'Testes do submenu iniciados! Verifique o console do servidor.', 'success');
        } else {
            showAlert(`Testes do submenu ${menuStructure[moduleKey].menus[menuKey]} executados!`, 'success');
        }
    })
    .catch(error => {
        hideLoading();
        showAlert('Erro ao executar testes do submenu: ' + error.message, 'error');
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
        async () => {
            // Proceed with deletion
            savedTests = savedTests.filter(t => t.id !== testId);
            await deleteTestFromDB(testId);
            
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

// Execute tests in parallel
async function executeTestsParallel(testIds, maxParallel = 3, visualMode = false) {
    showLoading(`Iniciando execução paralela de ${testIds.length} testes...`);

    try {
        const response = await fetch(`${API_BASE}/api/tests/execute-parallel`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                testIds: testIds,
                maxParallel: maxParallel,
                visualMode: visualMode
            })
        });

        const result = await response.json();

        if (response.ok) {
            hideLoading();
            const mode = visualMode ? 'Visual' : 'Headless';
            showAlert(`Execução paralela iniciada (${mode}).\nBatch ID: ${result.batchId}`, 'success', 3000);
            return result.batchId;
        } else {
            throw new Error(result.message || 'Failed to start parallel execution');
        }
    } catch (error) {
        hideLoading();
        showAlert('Erro ao iniciar execução paralela: ' + error.message, 'error');
        throw error;
    }
}

// Execute single test with visual mode option
async function runSingleTestVisual(testId, visualMode = true, slowMo = 500) {
    const test = savedTests.find(t => t.id === testId);
    if (!test) {
        showAlert('Teste não encontrado!', 'error');
        return;
    }

    const modeText = visualMode ? '🎭 Modo Visual (acompanhar)' : '⚡ Modo Rápido (headless)';
    showLoading(`Iniciando execução... ${modeText}`);

    try {
        const response = await fetch(`${API_BASE}/api/tests/execute-visual`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                testId: testId,
                visualMode: visualMode,
                slowMo: slowMo,
                recordVideo: true
            })
        });

        const result = await response.json();
        hideLoading();

        if (response.ok && result.status === 'started') {
            // Connect to WebSocket for real-time updates
            const ws = connectToExecutionWebSocket(result.executionId, {
                onConnect: () => {
                    console.log('WebSocket connected for execution:', result.executionId);
                },
                onStart: (data) => {
                    console.log('Execution started:', data);
                    showAlert(`${data.testName}\n${data.totalSteps} passos`, 'info', 2500);
                },
                onProgress: (data) => {
                    console.log('Step progress:', data);
                    // Update UI with progress
                    updateExecutionProgress(data);
                },
                onComplete: (data) => {
                    console.log('Execution complete:', data);
                    const successRate = data.successRate || 0;
                    showAlert(
                        `Execução concluída.\n` +
                        `${data.successCount} passos OK\n` +
                        `${data.failedCount} falhas\n` +
                        `${successRate.toFixed(1)}% de sucesso`,
                        successRate >= 80 ? 'success' : 'warning',
                        3500
                    );
                    refreshDashboardExecutionsFromDisk();
                },
                onError: (data) => {
                    console.error('Execution error:', data);
                    showAlert('Erro na execução: ' + data.error, 'error');
                    refreshDashboardExecutionsFromDisk();
                }
            });

            // Save to execution history
            saveExecution({
                testId: test.id,
                testName: test.name,
                module: test.module,
                menu: test.menu,
                status: 'running',
                visualMode: visualMode,
                executionId: result.executionId,
                timestamp: new Date().toISOString()
            });
            startExecutionHistoryPoll(result.executionId);

            const modeMsg = visualMode
                ? '🎭 Modo Visual ativado!\nO navegador será aberto para você acompanhar.'
                : '⚡ Modo Headless ativado!\nExecução rápida em segundo plano.';

            showAlert(`Teste iniciado.\n${modeMsg}`, 'success', 2500);

            return result.executionId;
        } else {
            throw new Error(result.message || 'Failed to start test');
        }
    } catch (error) {
        hideLoading();
        console.error('Erro ao executar teste:', error);
        showAlert('Erro ao executar teste: ' + error.message, 'error');
    }
}

function startExecutionHistoryPoll(executionId) {
    const startedAt = Date.now();
    const maxMs = 10 * 60 * 1000;
    const intervalMs = 3000;

    const tick = async () => {
        if (!executionId) return;
        if (Date.now() - startedAt > maxMs) return;
        await refreshDashboardExecutionsFromDisk();

        try {
            const executions = getDashboardExecutions();
            const match = executions.find(item => item.id === executionId || item.executionId === executionId);
            if (match && match.status !== 'running') return;
        } catch (error) {
            return;
        }

        setTimeout(tick, intervalMs);
    };

    setTimeout(tick, intervalMs);
}

// Update execution progress in UI
function updateExecutionProgress(data) {
    // Find or create progress indicator
    let progressEl = document.getElementById('execution-progress');
    if (!progressEl) {
        progressEl = document.createElement('div');
        progressEl.id = 'execution-progress';
        progressEl.style.cssText = `
            position: fixed;
            bottom: 20px;
            right: 20px;
            background: var(--bg-elevated);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 16px;
            min-width: 300px;
            z-index: 1000;
            box-shadow: 0 4px 20px rgba(0,0,0,0.3);
        `;
        document.body.appendChild(progressEl);
    }

    const statusIcon = data.status === 'SUCCESS' ? '✅' :
                       data.status === 'FAILED' ? '❌' : '⏳';

    progressEl.innerHTML = `
        <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
            <span style="font-size: 20px;">🎭</span>
            <strong>Execução em andamento</strong>
        </div>
        <div style="margin-bottom: 8px;">
            Passo ${data.stepNumber}/${data.totalSteps}: ${data.action}
        </div>
        <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 8px;">
            ${statusIcon} ${data.description || ''}
        </div>
        <div style="background: var(--bg-input); height: 6px; border-radius: 3px; overflow: hidden;">
            <div style="background: linear-gradient(90deg, #3b82f6, #8b5cf6); height: 100%; width: ${data.progress}%; transition: width 0.3s;"></div>
        </div>
        <div style="text-align: right; font-size: 12px; color: var(--text-muted); margin-top: 4px;">
            ${data.progress}%
        </div>
    `;

    // Auto-hide when complete
    if (data.progress >= 100) {
        setTimeout(() => {
            if (progressEl) progressEl.remove();
        }, 5000);
    }
}

// Check parallel execution status
async function checkParallelExecutionStatus(batchId) {
    try {
        const response = await fetch(`${API_BASE}/api/parallel-execution/${batchId}`);
        return await response.json();
    } catch (error) {
        console.error('Error checking parallel status:', error);
        return null;
    }
}

// Load OpenAI config
async function loadOpenAIConfig() {
    try {
        const response = await fetch(`${API_BASE}/api/openai-config`);
        return await response.json();
    } catch (error) {
        console.error('Error loading OpenAI config:', error);
        return { configured: false };
    }
}

// Save OpenAI config
async function saveOpenAIConfig(config) {
    try {
        const response = await fetch(`${API_BASE}/api/openai-config`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });
        return await response.json();
    } catch (error) {
        console.error('Error saving OpenAI config:', error);
        throw error;
    }
}

async function loadContext7Status() {
    try {
        const response = await fetch(`${API_BASE}/api/context7-config`);
        const data = await response.json();
        const statusEl = document.getElementById('context7Configured');
        const keyEl = document.getElementById('context7ApiKeyMasked');
        if (statusEl) statusEl.textContent = `Status: ${data.configured ? 'Configurado' : 'Não configurado'}`;
        if (keyEl) keyEl.textContent = data.apiKey ? `(${data.apiKey})` : '';
        return data;
    } catch (error) {
        console.error('Error loading Context7 status:', error);
        const statusEl = document.getElementById('context7Configured');
        const keyEl = document.getElementById('context7ApiKeyMasked');
        if (statusEl) statusEl.textContent = 'Status: Erro';
        if (keyEl) keyEl.textContent = '';
        return { configured: false };
    }
}

function context7SetOutput(text) {
    const el = document.getElementById('context7Output');
    if (!el) return;
    el.textContent = text || '';
}

function context7ClearOutput() {
    context7SetOutput('');
}

async function context7LookupLibrary() {
    const name = (document.getElementById('context7LibraryName')?.value || '').trim();
    const query = (document.getElementById('context7LibraryQuery')?.value || '').trim();
    if (!name || !query) {
        context7SetOutput('Preencha: library name e query');
        return;
    }

    context7SetOutput('Consultando Context7 (library)...');
    await loadContext7Status();

    try {
        const url = `${API_BASE}/api/context7/library?name=${encodeURIComponent(name)}&query=${encodeURIComponent(query)}`;
        const response = await fetch(url);
        if (!response.ok) {
            const t = await response.text();
            context7SetOutput(`Erro (${response.status}): ${t}`);
            return;
        }
        const data = await response.json();
        context7SetOutput((data.stdout || '') + (data.stderr ? `\n\n[stderr]\n${data.stderr}` : ''));
    } catch (error) {
        console.error('Error in Context7 library:', error);
        context7SetOutput(`Erro: ${error.message || error}`);
    }
}

async function context7GetDocs() {
    const libraryId = (document.getElementById('context7LibraryId')?.value || '').trim();
    const query = (document.getElementById('context7DocsQuery')?.value || '').trim();
    const research = !!document.getElementById('context7Research')?.checked;
    if (!libraryId || !query) {
        context7SetOutput('Preencha: libraryId e query');
        return;
    }

    context7SetOutput('Consultando Context7 (docs)...');
    await loadContext7Status();

    try {
        const url = `${API_BASE}/api/context7/docs?libraryId=${encodeURIComponent(libraryId)}&query=${encodeURIComponent(query)}&research=${research}`;
        const response = await fetch(url);
        if (!response.ok) {
            const t = await response.text();
            context7SetOutput(`Erro (${response.status}): ${t}`);
            return;
        }
        const data = await response.json();
        context7SetOutput((data.stdout || '') + (data.stderr ? `\n\n[stderr]\n${data.stderr}` : ''));
    } catch (error) {
        console.error('Error in Context7 docs:', error);
        context7SetOutput(`Erro: ${error.message || error}`);
    }
}

// Connect to execution WebSocket
function connectToExecutionWebSocket(executionId, callbacks) {
    const wsUrl = `${API_BASE.replace('http', 'ws')}/ws/execution/${executionId}`;
    const ws = new WebSocket(wsUrl);

    ws.onopen = () => {
        console.log('WebSocket connected:', executionId);
        if (callbacks.onConnect) callbacks.onConnect();
    };

    ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        console.log('WebSocket message:', data);

        switch (data.type) {
            case 'execution_start':
                if (callbacks.onStart) callbacks.onStart(data);
                break;
            case 'step_progress':
                if (callbacks.onProgress) callbacks.onProgress(data);
                break;
            case 'execution_complete':
                if (callbacks.onComplete) callbacks.onComplete(data);
                ws.close();
                break;
            case 'execution_error':
                if (callbacks.onError) callbacks.onError(data);
                break;
        }
    };

    ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        if (callbacks.onError) callbacks.onError({ error: 'WebSocket error' });
    };

    ws.onclose = () => {
        console.log('WebSocket disconnected:', executionId);
        if (callbacks.onDisconnect) callbacks.onDisconnect();
    };

    return ws;
}

// Open reports
function openReports() {
    window.open(`${API_BASE}/reports`, '_blank');
}

// Open timeline report
function openTimelineReport() {
    window.open(`${API_BASE}/timeline-report`, '_blank');
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
async function showConfigForm() {
    hideAllViews();
    document.getElementById('configView').classList.remove('hidden');

    updatePageTitle('Configurações', 'Configure URLs e credenciais do sistema');
    updateActiveNav(['nav-config-shortcut', 'nav-config']);

    // Save view state
    saveViewState('config');

    // Load configuration from database first
    console.log('[Config] Carregando configurações do banco de dados...');
    const dbConfig = await loadConfigFromDB();
    console.log('[Config] Configurações do DB:', dbConfig);
    if (dbConfig && Object.keys(dbConfig).length > 0) {
        // Update localStorage with DB values
        localStorage.setItem('qaAgentConfig', JSON.stringify(dbConfig));
        console.log('[Config] Configurações salvas no localStorage');
    } else {
        console.log('[Config] Nenhuma configuração encontrada no DB');
    }

    // Load current configuration
    loadConfig();

    // Load execution configuration
    loadExecutionConfig();

    await loadContext7Status();
}

// Load configuration from localStorage
function loadConfig() {
    const config = JSON.parse(localStorage.getItem('qaAgentConfig') || '{}');
    console.log('[Config] Configurações do localStorage:', config);

    document.getElementById('baseUrl').value = config['base.url'] || config.baseUrl || '';
    document.getElementById('username').value = config['user.username'] || config.username || '';
    document.getElementById('password').value = config['user.password'] || config.password || '';
    document.getElementById('timeout').value = config['timeout.default'] || config.timeout || 30;
    document.getElementById('environment').value = config.environment || 'test';
    document.getElementById('browser').value = config['browser.type'] || config.browser || 'chromium';

    console.log('[Config] Valores carregados nos campos:', {
        baseUrl: document.getElementById('baseUrl').value,
        username: document.getElementById('username').value,
        password: document.getElementById('password').value ? '***' : ''
    });
}

// Save configuration
async function saveConfig() {
    // Log dos valores dos campos antes de criar o objeto config
    const baseUrlValue = document.getElementById('baseUrl').value;
    const usernameValue = document.getElementById('username').value;
    const passwordValue = document.getElementById('password').value;

    console.log('[Config] Valores dos campos capturados:', {
        baseUrl: baseUrlValue,
        username: usernameValue,
        password: passwordValue ? '***' : '(vazio)'
    });

    const config = {
        'base.url': baseUrlValue,
        'user.username': usernameValue,
        'user.password': passwordValue,
        'timeout.default': parseInt(document.getElementById('timeout').value),
        'environment': document.getElementById('environment').value,
        'browser.type': document.getElementById('browser').value
    };

    console.log('[Config] Objeto config a ser enviado:', config);

    // Save to database (this will also sync to config.properties via DatabaseManager)
    const response = await fetch('/api/save-config', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(config)
    });

    const data = await response.json();
    console.log('[Config] Resposta do servidor:', data);
    if (data.status === 'success') {
        // Also save to localStorage for immediate use
        localStorage.setItem('qaAgentConfig', JSON.stringify(config));
        console.log('[Config] Configurações salvas no localStorage');
        showAlert('Configurações salvas com sucesso!', 'success');
    } else {
        console.error('[Config] Erro ao salvar:', data);
        showAlert('Erro ao salvar configurações: ' + (data.error || 'Erro desconhecido'), 'error');
    }
}

// ============================================
// CONFIGURAÇÃO DE EXECUÇÃO (Modo Visual/Headless)
// ============================================

// Default execution configuration
let executionConfig = {
    mode: 'visual', // 'visual', 'headless', 'debug'
    slowMo: 500,
    recordVideo: true,
    takeScreenshots: true,
    continueOnError: true
};

// Initialize execution mode selection
document.addEventListener('DOMContentLoaded', () => {
    // Load execution config
    loadExecutionConfig();

    // Setup mode selection listeners
    const modeOptions = document.querySelectorAll('.mode-option');
    modeOptions.forEach(option => {
        option.addEventListener('click', () => {
            const mode = option.dataset.mode;
            selectExecutionMode(mode);
        });
    });

    // Setup speed slider
    const speedSlider = document.getElementById('executionSpeed');
    if (speedSlider) {
        speedSlider.addEventListener('input', (e) => {
            const value = e.target.value;
            document.getElementById('speedValue').textContent = value + 'ms';
            executionConfig.slowMo = parseInt(value);
        });
    }

    // Setup checkboxes
    const recordVideo = document.getElementById('recordVideo');
    const takeScreenshots = document.getElementById('takeScreenshots');
    const continueOnError = document.getElementById('continueOnError');

    if (recordVideo) {
        recordVideo.addEventListener('change', (e) => {
            executionConfig.recordVideo = e.target.checked;
        });
    }
    if (takeScreenshots) {
        takeScreenshots.addEventListener('change', (e) => {
            executionConfig.takeScreenshots = e.target.checked;
        });
    }
    if (continueOnError) {
        continueOnError.addEventListener('change', (e) => {
            executionConfig.continueOnError = e.target.checked;
        });
    }
});

// Select execution mode
function selectExecutionMode(mode) {
    executionConfig.mode = mode;

    // Update UI
    document.querySelectorAll('.mode-option').forEach(opt => {
        opt.style.borderColor = 'var(--border)';
        opt.style.background = 'transparent';
    });

    const selected = document.querySelector(`.mode-option[data-mode="${mode}"]`);
    if (selected) {
        selected.style.borderColor = '#3b82f6';
        selected.style.background = 'rgba(59, 130, 246, 0.1)';
    }

    // Update radio
    const radio = document.getElementById(`mode${mode.charAt(0).toUpperCase() + mode.slice(1)}`);
    if (radio) radio.checked = true;

    // Update description and speed
    const descriptions = {
        visual: '🎭 Modo Visual: Abre o navegador para você acompanhar cada passo. Ideal para demonstrações e debug.',
        headless: '⚡ Modo Rápido: Executa em segundo plano sem abrir navegador. Ideal para CI/CD e suites completas.',
        debug: '🐛 Modo Debug: Execução lenta com pausas para investigar falhas intermitentes.'
    };

    const descEl = document.getElementById('modeDescription');
    if (descEl) descEl.textContent = descriptions[mode];

    // Auto-adjust speed based on mode
    const speedSlider = document.getElementById('executionSpeed');
    const speedValue = document.getElementById('speedValue');
    if (speedSlider && speedValue) {
        const speeds = { visual: 500, headless: 0, debug: 1000 };
        speedSlider.value = speeds[mode];
        speedValue.textContent = speeds[mode] + 'ms';
        executionConfig.slowMo = speeds[mode];
    }
}

// Load execution configuration
function loadExecutionConfig() {
    console.log('[Config] Loading execution config...');

    const saved = localStorage.getItem('qaAgentExecutionConfig');
    if (saved) {
        try {
            const parsed = JSON.parse(saved);
            executionConfig = { ...executionConfig, ...parsed };
            console.log('[Config] Loaded from localStorage:', executionConfig);
        } catch (e) {
            console.error('[Config] Error parsing saved config:', e);
        }
    } else {
        console.log('[Config] No saved config found, using defaults');
    }

    // Apply to UI - make sure elements exist
    setTimeout(() => {
        // Apply mode selection
        selectExecutionMode(executionConfig.mode);

        // Apply speed slider
        const speedSlider = document.getElementById('executionSpeed');
        const speedValue = document.getElementById('speedValue');
        if (speedSlider) {
            speedSlider.value = executionConfig.slowMo;
            console.log('[Config] Set speed slider to:', executionConfig.slowMo);
        }
        if (speedValue) {
            speedValue.textContent = executionConfig.slowMo + 'ms';
        }

        // Apply checkboxes
        const recordVideo = document.getElementById('recordVideo');
        const takeScreenshots = document.getElementById('takeScreenshots');
        const continueOnError = document.getElementById('continueOnError');

        if (recordVideo) {
            recordVideo.checked = executionConfig.recordVideo;
            console.log('[Config] Set recordVideo to:', executionConfig.recordVideo);
        }
        if (takeScreenshots) {
            takeScreenshots.checked = executionConfig.takeScreenshots;
            console.log('[Config] Set takeScreenshots to:', executionConfig.takeScreenshots);
        }
        if (continueOnError) {
            continueOnError.checked = executionConfig.continueOnError;
            console.log('[Config] Set continueOnError to:', executionConfig.continueOnError);
        }

        console.log('[Config] Execution config applied to UI');
    }, 100);
}

// Save execution configuration to localStorage and backend
async function saveExecutionConfig() {
    // Update config from current UI values
    const mode = document.querySelector('input[name="executionMode"]:checked')?.value || 'visual';
    const slowMo = parseInt(document.getElementById('executionSpeed')?.value || 500);
    const recordVideo = document.getElementById('recordVideo')?.checked ?? true;
    const takeScreenshots = document.getElementById('takeScreenshots')?.checked ?? true;
    const continueOnError = document.getElementById('continueOnError')?.checked ?? true;

    executionConfig = {
        mode,
        slowMo,
        recordVideo,
        takeScreenshots,
        continueOnError
    };

    // Save to localStorage
    localStorage.setItem('qaAgentExecutionConfig', JSON.stringify(executionConfig));

    // Save to backend
    try {
        const response = await fetch(`${API_BASE}/api/save-config`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                executionMode: mode,
                slowMo: slowMo,
                headless: mode === 'headless',
                recordVideo: recordVideo,
                takeScreenshots: takeScreenshots
            })
        });

        const result = await response.json();
        if (result.status === 'success') {
            showAlert('✅ Configurações de execução salvas!', 'success');
        }
    } catch (error) {
        console.error('Error saving execution config:', error);
        // Already saved to localStorage, so show success anyway
        showAlert('✅ Configurações salvas localmente!', 'success');
    }

    return executionConfig;
}

// Get current execution configuration
function getExecutionConfig() {
    return { ...executionConfig };
}

// Check if visual mode is enabled
function isVisualMode() {
    return executionConfig.mode === 'visual' || executionConfig.mode === 'debug';
}

// Check if headless mode is enabled
function isHeadlessMode() {
    return executionConfig.mode === 'headless';
}

// Execute test with current configuration
async function executeTestWithConfig(testId) {
    const config = getExecutionConfig();
    const visualMode = isVisualMode();

    if (visualMode) {
        return await runSingleTestVisual(testId, true, config.slowMo);
    } else {
        return await runSingleTestVisual(testId, false, 0);
    }
}

// Reset execution configuration to defaults
function resetExecutionConfig() {
    executionConfig = {
        mode: 'visual',
        slowMo: 500,
        recordVideo: true,
        takeScreenshots: true,
        continueOnError: true
    };

    // Apply to UI
    selectExecutionMode('visual');
    document.getElementById('executionSpeed').value = 500;
    document.getElementById('speedValue').textContent = '500ms';
    document.getElementById('recordVideo').checked = true;
    document.getElementById('takeScreenshots').checked = true;
    document.getElementById('continueOnError').checked = true;

    // Save to localStorage
    localStorage.setItem('qaAgentExecutionConfig', JSON.stringify(executionConfig));

    showAlert('✅ Configurações restauradas para o padrão!', 'success');
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
                    <i class="fas fa-grip-vertical" style="color: var(--text-muted); cursor: grab; pointer-events: none;"></i>
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
                        <i class="fas fa-grip-vertical" style="color: var(--text-muted); cursor: grab; pointer-events: none;"></i>
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

// Add new module with custom modal
function addModule() {
    // Create custom modal for module input
    const modalDiv = document.createElement('div');
    modalDiv.id = 'addModuleModal';
    modalDiv.style.cssText = 'position: fixed; inset: 0; background: rgba(0,0,0,0.7); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 1000;';
    modalDiv.innerHTML = `
        <div style="background: var(--bg-card); border: 1px solid var(--border); border-radius: 16px; box-shadow: 0 25px 50px rgba(0,0,0,0.5); max-width: 450px; width: 90%; overflow: hidden; animation: fadeIn 0.2s ease-out;">
            <!-- Header -->
            <div style="background: linear-gradient(135deg, #3b82f6, #8b5cf6); padding: 20px 24px;">
                <h3 style="font-size: 18px; font-weight: 600; color: white; display: flex; align-items: center; gap: 10px; margin: 0;">
                    <i class="fas fa-plus-circle"></i>
                    Adicionar Novo Módulo
                </h3>
            </div>

            <!-- Body -->
            <div style="padding: 24px;">
                <div style="margin-bottom: 16px;">
                    <label style="display: block; font-size: 13px; font-weight: 500; color: var(--text-secondary); margin-bottom: 6px;">
                        ID do Módulo
                    </label>
                    <input type="text" id="moduleIdInput" placeholder="ex: atendimento, cadastros, financeiro"
                           style="width: 100%; padding: 12px 14px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg-input); color: var(--text-primary); font-size: 14px;"
                           oninput="generateModuleId(this.value)">
                    <p style="font-size: 11px; color: var(--text-muted); margin-top: 4px;">
                        Usado na URL e identificação interna (sem espaços, minúsculas)
                    </p>
                </div>

                <div style="margin-bottom: 8px;">
                    <label style="display: block; font-size: 13px; font-weight: 500; color: var(--text-secondary); margin-bottom: 6px;">
                        Nome do Módulo
                    </label>
                    <input type="text" id="moduleNameInput" placeholder="ex: Atendimento ao Cliente"
                           style="width: 100%; padding: 12px 14px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg-input); color: var(--text-primary); font-size: 14px;">
                    <p style="font-size: 11px; color: var(--text-muted); margin-top: 4px;">
                        Nome exibido no menu lateral
                    </p>
                </div>
            </div>

            <!-- Footer -->
            <div style="padding: 16px 24px 24px; display: flex; gap: 12px;">
                <button onclick="closeAddModuleModal()" style="flex: 1; padding: 12px 16px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg-elevated); color: var(--text-secondary); font-size: 14px; font-weight: 500; cursor: pointer; transition: all 0.2s;">
                    <i class="fas fa-times" style="margin-right: 6px;"></i>
                    Cancelar
                </button>
                <button onclick="saveNewModule()" style="flex: 1; padding: 12px 16px; border: none; border-radius: 8px; background: linear-gradient(135deg, #3b82f6, #8b5cf6); color: white; font-size: 14px; font-weight: 500; cursor: pointer; transition: all 0.2s; box-shadow: 0 4px 12px rgba(59,130,246,0.3);">
                    <i class="fas fa-check" style="margin-right: 6px;"></i>
                    Adicionar Módulo
                </button>
            </div>
        </div>
    `;
    document.body.appendChild(modalDiv);

    // Focus on name input
    setTimeout(() => document.getElementById('moduleNameInput').focus(), 100);
}

// Generate module ID from name
function generateModuleId(name) {
    const id = name.toLowerCase()
        .replace(/[^a-z0-9]/g, '_')
        .replace(/_+/g, '_')
        .replace(/^_|_$/g, '');
    document.getElementById('moduleIdInput').value = id;
}

// Close add module modal
function closeAddModuleModal() {
    const modal = document.getElementById('addModuleModal');
    if (modal) modal.remove();
}

// Save new module
function saveNewModule() {
    const moduleId = document.getElementById('moduleIdInput').value.trim();
    const moduleName = document.getElementById('moduleNameInput').value.trim();

    if (!moduleId) {
        showAlert('ID do módulo é obrigatório', 'error');
        return;
    }
    if (!moduleName) {
        showAlert('Nome do módulo é obrigatório', 'error');
        return;
    }

    // Check if ID already exists
    if (menuStructure[moduleId]) {
        showAlert('Já existe um módulo com este ID', 'error');
        return;
    }

    menuStructure[moduleId] = {
        name: moduleName,
        menus: {}
    };
    closeAddModuleModal();
    renderMenuEditor();
    showAlert('Módulo adicionado com sucesso!', 'success');
}

// Save menu structure
async function saveMenuStructure() {
    await saveMenuStructureToDB(menuStructure);
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
    console.log('Initializing drag and drop...');
    
    const draggables = document.querySelectorAll('.draggable');
    const containers = document.querySelectorAll('#modulesContainer, [id^="menus-"]');
    
    console.log(`Found ${draggables.length} draggable elements`);
    console.log(`Found ${containers.length} containers`);
    
    draggables.forEach(draggable => {
        // Remove existing listeners to avoid duplicates
        draggable.removeEventListener('dragstart', handleDragStart);
        draggable.removeEventListener('dragend', handleDragEnd);
        
        // Add new listeners
        draggable.addEventListener('dragstart', handleDragStart);
        draggable.addEventListener('dragend', handleDragEnd);
        
        // Ensure draggable attribute is set
        draggable.setAttribute('draggable', 'true');
        
        console.log(`Setup drag for: ${draggable.dataset.key} (${draggable.dataset.type})`);
    });
    
    containers.forEach(container => {
        // Remove existing listeners
        container.removeEventListener('dragover', handleDragOver);
        container.removeEventListener('drop', handleDrop);
        container.removeEventListener('dragenter', handleDragEnter);
        container.removeEventListener('dragleave', handleDragLeave);
        
        // Add new listeners
        container.addEventListener('dragover', handleDragOver);
        container.addEventListener('drop', handleDrop);
        container.addEventListener('dragenter', handleDragEnter);
        container.addEventListener('dragleave', handleDragLeave);
        
        console.log(`Setup drop for container: ${container.id}`);
    });
}

let draggedElement = null;
let draggedType = null;
let draggedKey = null;
let draggedModule = null;

function handleDragStart(e) {
    console.log('Drag started:', this.dataset.key, this.dataset.type);
    
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
    
    const dragging = document.querySelector('.dragging');
    if (!dragging) return false;
    
    // Validate that we're dropping in the right container
    const targetId = e.currentTarget.id;
    const draggedType = dragging.dataset.type;
    
    // Modules can only be dropped in modulesContainer
    if (draggedType === 'module' && targetId !== 'modulesContainer') {
        return false;
    }
    
    // Menus can only be dropped in menus-* containers
    if (draggedType === 'menu' && !targetId.startsWith('menus-')) {
        return false;
    }
    
    // Prevent dropping an element into itself or its children
    if (e.currentTarget.contains(dragging)) {
        // Already in correct container, just reorder
        const afterElement = getDragAfterElement(e.currentTarget, e.clientY);
        
        if (afterElement == null) {
            e.currentTarget.appendChild(dragging);
        } else {
            e.currentTarget.insertBefore(dragging, afterElement);
        }
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
    saveMenuStructure();
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
    saveMenuStructure();
}
