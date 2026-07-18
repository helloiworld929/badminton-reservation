// ====== 预约核销 ======
const verifyBtn = document.getElementById('verifyBtn');
const codeInput = document.getElementById('verifyCode');
const resultDiv = document.getElementById('verifyResult');

// ====== 动态时钟 ======
const clockTime = document.getElementById('clockTime');
const clockDate = document.getElementById('clockDate');
function tick() {
    const now = new Date();
    clockTime.textContent = [String(now.getHours()).padStart(2, '0'), String(now.getMinutes()).padStart(2, '0'), String(now.getSeconds()).padStart(2, '0')].join(':');
    const week = ['日', '一', '二', '三', '四', '五', '六'];
    clockDate.textContent = now.getFullYear() + '年' + (now.getMonth() + 1) + '月' + now.getDate() + '日 星期' + week[now.getDay()];
}
tick();
setInterval(tick, 1000);

verifyBtn.addEventListener('click', doVerify);
codeInput.addEventListener('keydown', e => { if (e.key === 'Enter') doVerify(); });

async function doVerify() {
    const code = codeInput.value.trim();
    if (!/^\d{6}$/.test(code)) {
        resultDiv.innerHTML = '<p style="color:#b42318;">请输入 6 位数字验证码</p>';
        return;
    }

    verifyBtn.disabled = true;
    verifyBtn.textContent = '核销中...';
    resultDiv.innerHTML = '';

    try {
        const result = await apiPost('/admin/reservations/verify', {code});
        if (result.code === 1) {
            const d = result.data;
            resultDiv.innerHTML = `
                <div class="panel" style="border-left:4px solid #06965c;background:#f0faf5;">
                    <p style="color:#06965c;font-size:16px;font-weight:700;">✅ 核销成功</p>
                    <p>场地：${escapeHtml(d.courtName)}</p>
                    <p>日期：${escapeHtml(d.reserveDate)} ${String(d.startTime).padStart(2,'0')}:00 - ${String(d.endTime).padStart(2,'0')}:00</p>
                    <p>状态：已变更为「已验证」</p>
                </div>`;
            codeInput.value = '';
            codeInput.focus();
        } else {
            resultDiv.innerHTML = `<p style="color:#b42318;">❌ ${escapeHtml(result.msg || '核销失败')}</p>`;
        }
    } catch (error) {
        resultDiv.innerHTML = `<p style="color:#b42318;">❌ ${escapeHtml(error.message || '网络错误')}</p>`;
    } finally {
        verifyBtn.disabled = false;
        verifyBtn.textContent = '确认核销';
    }
}

// ====== 场地管理 ======
let courts = [];
const tbody = document.querySelector('#courtTable tbody');
const addModal = document.getElementById('addModal');
const addForm = document.getElementById('addForm');

document.getElementById('addCourtBtn').addEventListener('click', () => addModal.classList.add('show'));
document.getElementById('closeAdd').addEventListener('click', () => addModal.classList.remove('show'));
document.getElementById('cancelAdd').addEventListener('click', () => addModal.classList.remove('show'));
addModal.addEventListener('click', e => { if (e.target === addModal) addModal.classList.remove('show'); });
addForm.addEventListener('submit', addCourt);

loadCourts();

async function loadCourts() {
    tbody.innerHTML = '<tr><td colspan="5">加载中...</td></tr>';
    try {
        const result = await apiGet('/admin/courts');
        if (result.code === 1) {
            courts = result.data || [];
            render();
        } else {
            tbody.innerHTML = `<tr><td colspan="5">${escapeHtml(result.msg || '加载失败')}</td></tr>`;
        }
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="5">${escapeHtml(error.message || '网络错误')}</td></tr>`;
    }
}

function render() {
    if (!courts.length) {
        tbody.innerHTML = '<tr><td colspan="5">暂无场地</td></tr>';
        return;
    }
    tbody.innerHTML = '';
    courts.forEach((court, i) => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${i + 1}</td>
            <td>${escapeHtml(court.name)}</td>
            <td>${escapeHtml(court.remark || '-')}</td>
            <td>${statusTag(court.status)}</td>
            <td>${actionBtns(court)}</td>
        `;
        tbody.appendChild(tr);
    });
}

function statusTag(status) {
    const map = {0: ['正常', 'success'], 1: ['锁定', 'locked'], 2: ['维护', 'warning']};
    const [text, cls] = map[status] || ['未知', ''];
    return `<span class="tag ${cls}">${text}</span>`;
}

function actionBtns(court) {
    let statusBtn = '';
    if (court.status === 0) {
        statusBtn = `<button class="btn secondary" data-action="status" data-id="${court.id}" data-status="1" style="font-size:12px;height:28px;">锁定</button>
                     <button class="btn secondary" data-action="status" data-id="${court.id}" data-status="2" style="font-size:12px;height:28px;">维护</button>`;
    } else {
        statusBtn = `<button class="btn" data-action="status" data-id="${court.id}" data-status="0" style="font-size:12px;height:28px;">恢复</button>`;
    }
    return `${statusBtn}
        <button class="btn secondary" data-action="delete" data-id="${court.id}" style="font-size:12px;height:28px;color:#b42318;">删除</button>`;
}

// Event delegation for action buttons
tbody.addEventListener('click', async (e) => {
    const btn = e.target.closest('button[data-action]');
    if (!btn) return;
    const action = btn.dataset.action;
    const id = Number(btn.dataset.id);

    if (action === 'status') {
        const newStatus = Number(btn.dataset.status);
        try {
            const result = await apiPost(`/admin/courts/${id}/status`, {status: newStatus});
            if (result.code === 1) {
                const idx = courts.findIndex(c => c.id === id);
                if (idx >= 0) courts[idx].status = newStatus;
                render();
            } else {
                alert(result.msg || '操作失败');
            }
        } catch (error) {
            alert(error.message || '网络错误');
        }
    } else if (action === 'delete') {
        if (!confirm(`确定删除场地「${courts.find(c => c.id === id)?.name || id}」吗？`)) return;
        try {
            const result = await apiDelete(`/admin/courts/${id}`);
            if (result.code === 1) {
                courts = courts.filter(c => c.id !== id);
                render();
            } else {
                alert(result.msg || '删除失败');
            }
        } catch (error) {
            alert(error.message || '网络错误');
        }
    }
});

async function addCourt(event) {
    event.preventDefault();
    const name = document.getElementById('courtName').value.trim();
    const remark = document.getElementById('courtRemark').value.trim();
    if (!name) return;
    try {
        const result = await apiPost('/admin/courts', {name, remark});
        if (result.code === 1) {
            addModal.classList.remove('show');
            document.getElementById('courtName').value = '';
            document.getElementById('courtRemark').value = '';
            loadCourts();
        } else {
            alert(result.msg || '新增失败');
        }
    } catch (error) {
        alert(error.message || '网络错误');
    }
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
}
