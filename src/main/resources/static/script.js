const tg = window.Telegram?.WebApp;
if (tg) tg.expand();

const API_URL = "/api";
const tgInitData = tg?.initData || "";

let selectedCategoryId = null;
let selectedOperation = "ADD";
let rawCategoriesCache = [];
const categoryCounts = new Map();

document.addEventListener("DOMContentLoaded", () => {
    const today = new Date();
    const dateStr = today.toISOString().split('T')[0];
    const monthStr = dateStr.substring(0, 7);

    document.getElementById('record-date').value = dateStr;
    document.getElementById('report-month').value = monthStr;

    loadCategories();
});

function showToast(message, isError = false) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const existingToasts = container.querySelectorAll('.toast-notification');
    existingToasts.forEach(t => t.classList.add('behind'));

    const toast = document.createElement('div');
    toast.className = 'toast-notification';
    toast.textContent = message;
    toast.style.backgroundColor = isError ? '#ff3b30' : '#2c2c2e';

    container.prepend(toast);

    if (tg?.HapticFeedback) {
        tg.HapticFeedback.notificationOccurred(isError ? 'error' : 'success');
    }

    requestAnimationFrame(() => {
        toast.classList.add('show');
    });

    setTimeout(() => {
        toast.classList.remove('show');
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(-10px) scale(0.8)';

        setTimeout(() => {
            toast.remove();
        }, 300);
    }, 5000);
}

async function loadCategories() {
    const container = document.getElementById('categories-container');
    try {
        const response = await fetch(`${API_URL}/categories`, {
            headers: { 'X-TG-INIT-DATA': tgInitData }
        });

        if (!response.ok) throw new Error('Помилка завантаження');

        rawCategoriesCache = await response.json();
        container.innerHTML = '';

        const filteredCategories = rawCategoriesCache.filter(cat =>
            cat.name.trim().toLowerCase() !== "всього за день" &&
            cat.name.trim().toLowerCase() !== "всего за день"
        );

        const categoryTree = buildCategoryTree(filteredCategories);

        if (!categoryTree || categoryTree.length === 0) {
            container.innerHTML = '<div class="loader">Категорії відсутні</div>';
            return;
        }

        const treeElement = document.createElement('div');
        treeElement.className = 'category-tree';

        categoryTree.forEach(cat => {
            treeElement.appendChild(renderCategoryTree(cat));
        });

        container.appendChild(treeElement);
    } catch (e) {
        console.error(e);
        container.innerHTML = '<div class="loader" style="color:var(--accent-red)">Не вдалося завантажити категорії</div>';
    }
}

function buildCategoryTree(items) {
    const map = new Map();
    const roots = [];

    items.forEach(item => {
        map.set(item.id, { ...item, subCategories: [] });
    });

    items.forEach(item => {
        if (item.parentId !== null && map.has(item.parentId)) {
            map.get(item.parentId).subCategories.push(map.get(item.id));
        } else {
            roots.push(map.get(item.id));
        }
    });

    return roots;
}

function getCategoryFullPath(catId) {
    const path = [];
    let current = rawCategoriesCache.find(c => c.id === catId);

    while (current) {
        path.unshift(current.name);
        current = rawCategoriesCache.find(c => c.id === current.parentId);
    }
    return path.join(' ➔ ');
}

function renderCategoryTree(category) {
    const node = document.createElement('div');
    node.className = 'cat-node';
    node.id = `node-${category.id}`;

    const hasChildren = category.subCategories && category.subCategories.length > 0;

    const item = document.createElement('div');
    item.className = 'cat-item';

    item.innerHTML = `
        <div class="cat-name-container">
            ${hasChildren ? '<span class="arrow-icon">►</span>' : ''}
            <span>${category.name}</span>
        </div>
        ${!hasChildren ? `<span class="check-mark" id="check-${category.id}" style="display: none;">✅</span>` : ''}
    `;

    item.onclick = (e) => {
        e.stopPropagation();
        if (hasChildren) {
            node.classList.toggle('expanded');
        } else {
            selectLeafCategory(category.id);
        }
    };

    node.appendChild(item);

    if (hasChildren) {
        const subContainer = document.createElement('div');
        subContainer.className = 'sub-categories';
        category.subCategories.forEach(sub => {
            subContainer.appendChild(renderCategoryTree(sub));
        });
        node.appendChild(subContainer);
    } else {
        const actionWrapper = document.createElement('div');
        actionWrapper.className = 'action-wrapper';
        actionWrapper.id = `action-wrapper-${category.id}`;
        node.appendChild(actionWrapper);
    }

    return node;
}

function selectLeafCategory(catId) {
    if (tg?.HapticFeedback) tg.HapticFeedback.selectionChanged();

    if (selectedCategoryId && selectedCategoryId !== catId) {
        const prevCheck = document.getElementById(`check-${selectedCategoryId}`);
        const prevWrapper = document.getElementById(`action-wrapper-${selectedCategoryId}`);
        if (prevCheck) prevCheck.style.display = 'none';
        if (prevWrapper) {
            prevWrapper.style.display = 'none';
            prevWrapper.innerHTML = '';
        }
    }

    selectedCategoryId = catId;
    selectedOperation = "ADD";

    const checkMark = document.getElementById(`check-${catId}`);
    if (checkMark) checkMark.style.display = 'inline';

    const wrapper = document.getElementById(`action-wrapper-${catId}`);
    wrapper.style.display = 'block';
    wrapper.innerHTML = `
        <div class="cat-actions">
            <button id="btn-add-${catId}" class="btn-action-tab btn-add active" type="button" onclick="setOperation(${catId}, 'ADD')">＋ Додати</button>
            <button id="btn-sub-${catId}" class="btn-action-tab btn-sub" type="button" onclick="setOperation(${catId}, 'SUBTRACT')">－ Відняти</button>
        </div>
        <div id="form-${catId}">
            <div class="input-form">
                <input type="number" id="input-count-${catId}" value="1" min="1" step="1">
                <button class="btn-confirm" id="btn-submit-${catId}" type="button" onclick="submitRecord(${catId})">Зберегти</button>
            </div>
        </div>
    `;
}

function setOperation(catId, operation) {
    if (tg?.HapticFeedback) tg.HapticFeedback.impactOccurred('light');

    selectedOperation = operation;

    const btnAdd = document.getElementById(`btn-add-${catId}`);
    const btnSub = document.getElementById(`btn-sub-${catId}`);

    if (btnAdd && btnSub) {
        btnAdd.classList.toggle('active', operation === 'ADD');
        btnSub.classList.toggle('active', operation === 'SUBTRACT');
    }
}

function formatDateToUK(dateString) {
    const parts = dateString.split('-');
    if (parts.length === 3) {
        return `${parts[2]}.${parts[1]}.${parts[0]}`;
    }
    return dateString;
}

async function submitRecord(catId) {
    const countInput = document.getElementById(`input-count-${catId}`);
    const submitBtn = document.getElementById(`btn-submit-${catId}`);

    const quantity = Math.abs(parseInt(countInput.value, 10));
    const selectedDate = document.getElementById('record-date').value;

    if (isNaN(quantity) || quantity <= 0) {
        showToast('⚠️ Введіть число більше 0', true);
        return;
    }

    if (!selectedDate) {
        showToast('⚠️ Оберіть дату', true);
        return;
    }

    const key = `${catId}_${selectedDate}`;
    const oldCount = categoryCounts.get(key) || 0;
    const currentOp = selectedOperation;

    if (currentOp === 'SUBTRACT' && oldCount === 0) {
        showToast('⚠️ Записів за цей день немає', true);
        return;
    }

    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerText = 'Збереження...';
    }

    try {
        const response = await fetch(`${API_URL}/records`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-TG-INIT-DATA': tgInitData
            },
            body: JSON.stringify({
                catId: catId,
                date: selectedDate,
                count: quantity,
                operation: currentOp
            })
        });

        if (response.ok) {
            const resData = await response.json().catch(() => null);

            let newCount = resData && typeof resData.count === 'number'
                ? resData.count
                : (currentOp === 'ADD' ? oldCount + quantity : Math.max(0, oldCount - quantity));

            categoryCounts.set(key, newCount);

            const actionText = currentOp === 'SUBTRACT' ? 'Віднято' : 'Додано';
            showToast(`✅ ${actionText} ${quantity}!`);

            countInput.value = 1;

            addLogEntry({
                catId,
                selectedDate,
                currentOp,
                oldCount,
                newCount
            });

        } else {
            showToast('❌ Помилка при збереженні', true);
        }
    } catch (e) {
        console.error(e);
        showToast('❌ Помилка з\'єднання з сервером', true);
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerText = 'Зберегти';
        }
    }
}

function addLogEntry({ catId, selectedDate, currentOp, oldCount, newCount }) {
    const fullPath = getCategoryFullPath(catId);
    const formattedDate = formatDateToUK(selectedDate);
    const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

    const logCard = document.getElementById('log-card');
    const logContent = document.getElementById('log-content');

    const item = document.createElement('div');
    item.className = `log-item ${currentOp === 'SUBTRACT' ? 'sub' : 'add'}`;

    item.innerHTML = `
        <div class="log-time">${timeStr}</div>
        <div>${fullPath} ➔ Всього за ${formattedDate}.</div>
        <div>Було: ${oldCount}</div>
        <div>Стало: ${newCount}</div>
    `;

    logContent.prepend(item);
    logCard.style.display = 'block';
}

function clearLogs() {
    const logContent = document.getElementById('log-content');
    const logCard = document.getElementById('log-card');
    logContent.innerHTML = '';
    logCard.style.display = 'none';
}

async function generateReport() {
    const monthVal = document.getElementById('report-month').value;
    if (!monthVal) {
        showToast('⚠️ Будь ласка, оберіть місяць', true);
        return;
    }

    const chatId = tg?.initDataUnsafe?.user?.id;
    if (!chatId) {
        showToast('⚠️ Не вдалося визначити ID чату', true);
        return;
    }

    const formattedMonth = `${monthVal}-01`;

    try {
        const response = await fetch(`${API_URL}/reports/generate?chatId=${chatId}&month=${formattedMonth}`, {
            method: 'POST',
            headers: { 'X-TG-INIT-DATA': tgInitData }
        });

        if (response.ok) {
            showToast('🚀 Звіт успішно надіслано в чат!');
            setTimeout(() => tg?.close(), 1200);
        } else {
            showToast('❌ Помилка при генерації звіту', true);
        }
    } catch (e) {
        console.error(e);
        showToast('❌ Не вдалося згенерувати звіт', true);
    }
}