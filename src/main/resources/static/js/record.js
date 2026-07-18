let recordPage = { list: [], total: 0, pageNum: 1, pageSize: 5, pages: 0 };
const tbody = document.querySelector('#bookingTable tbody');
const searchInput = document.getElementById('searchInput');
const paginationEl = document.getElementById('pagination');

document.getElementById('refreshBtn').addEventListener('click', () => loadRecords(1));
searchInput.addEventListener('input', renderRecords);

window.loadRecords = loadRecords;
loadRecords(1);

async function loadRecords(pageNum) {
    tbody.innerHTML = '<tr><td colspan="9">加载中...</td></tr>';
    try {
        const result = await apiGet('/reservation-records?pageNum=' + pageNum + '&pageSize=' + recordPage.pageSize);
        if (result.code === 1) {
            recordPage = result.data;
            renderRecords();
            renderPagination();
        } else {
            tbody.innerHTML = `<tr><td colspan="9">${escapeHtml(result.msg || '加载失败')}</td></tr>`;
        }
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="9">${escapeHtml(error.message || '网络错误，请稍后重试')}</td></tr>`;
    }
}

function renderRecords() {
    const list = recordPage.list || [];
    const keyword = searchInput.value.trim().toLowerCase();
    const filtered = keyword ? list.filter(item =>
        (item.courtName || '').toLowerCase().includes(keyword)) : list;
    if (!filtered.length) {
        tbody.innerHTML = '<tr><td colspan="9">暂无预约记录</td></tr>';
        return;
    }
    tbody.innerHTML = '';
    filtered.forEach((item, index) => {
        const tr = document.createElement('tr');
        const canCancel = item.status === 'unverified';
        tr.innerHTML = `
            <td>${index + 1}</td>
            <td>${escapeHtml(item.courtName)}</td>
            <td>${escapeHtml(item.reserveDate)}</td>
            <td>${String(item.startTime).padStart(2, '0')}:00</td>
            <td>${String(item.endTime).padStart(2, '0')}:00</td>
            <td>${escapeHtml(item.createdAt || '')}</td>
            <td><span class="tag ${statusClass(item.status)}">${item.statusDisplay || statusText(item.status)}</span></td>
            <td><code class="verify-code">${escapeHtml(item.verificationCode)}</code></td>
            <td>${canCancel ? `<button class="btn cancel-record-btn" data-id="${item.id}" style="font-size:12px;height:28px;background:#b42318;">取消</button>` : '<span style="color:#999;">-</span>'}</td>
        `;
        tbody.appendChild(tr);
    });

    tbody.querySelectorAll('.cancel-record-btn').forEach(btn => {
        btn.addEventListener('click', () => cancelRecord(Number(btn.dataset.id)));
    });
}

function renderPagination() {
    if (!recordPage.pages || recordPage.total === 0) {
        paginationEl.innerHTML = '';
        return;
    }
    if (recordPage.pages <= 1) {
        paginationEl.innerHTML = '<span class="page-info">共 ' + recordPage.total + ' 条</span>';
        return;
    }
    let html = '<span class="page-info">共 ' + recordPage.total + ' 条</span>';
    html += '<button class="page-btn" ' + (recordPage.pageNum <= 1 ? 'disabled' : '') + ' onclick="loadRecords(1)">首页</button>';
    html += '<button class="page-btn" ' + (recordPage.pageNum <= 1 ? 'disabled' : '') + ' onclick="loadRecords(' + (recordPage.pageNum - 1) + ')">上一页</button>';
    let start = Math.max(1, recordPage.pageNum - 2);
    let end = Math.min(recordPage.pages, recordPage.pageNum + 2);
    if (start > 1) html += '<span class="page-ellipsis">...</span>';
    for (let i = start; i <= end; i++) {
        html += '<button class="page-btn' + (i === recordPage.pageNum ? ' active' : '') + '" onclick="loadRecords(' + i + ')">' + i + '</button>';
    }
    if (end < recordPage.pages) html += '<span class="page-ellipsis">...</span>';
    html += '<button class="page-btn" ' + (recordPage.pageNum >= recordPage.pages ? 'disabled' : '') + ' onclick="loadRecords(' + (recordPage.pageNum + 1) + ')">下一页</button>';
    html += '<button class="page-btn" ' + (recordPage.pageNum >= recordPage.pages ? 'disabled' : '') + ' onclick="loadRecords(' + recordPage.pages + ')">末页</button>';
    html += '<span class="page-info">第 ' + recordPage.pageNum + '/' + recordPage.pages + ' 页</span>';
    paginationEl.innerHTML = html;
}

function statusText(status) {
    switch (status) {
        case 'unverified': return '未验证';
        case 'verified': return '已验证';
        case 'noshow': return '爽约';
        case 'cancelled': return '已取消';
        default: return status || '未知';
    }
}

function statusClass(status) {
    switch (status) {
        case 'unverified': return 'warning';
        case 'verified': return 'success';
        case 'noshow': return 'error';
        case 'cancelled': return '';
        default: return '';
    }
}

async function cancelRecord(id) {
    if (!confirm('确定取消该预约吗？取消后不可重新预约该时段。')) return;
    try {
        const result = await apiPost(`/reservations/${id}/cancel`);
        if (result.code === 1) {
            alert('取消成功');
            loadRecords(recordPage.pageNum);
        } else {
            alert(result.msg || '取消失败');
        }
    } catch (error) {
        alert(error.message || '网络错误');
    }
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, ch => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    }[ch]));
}
