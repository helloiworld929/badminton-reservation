let pageData = { list: [], total: 0, pageNum: 1, pageSize: 5, pages: 0 };
const tbody = document.querySelector('#userTable tbody');
const paginationEl = document.getElementById('pagination');
const defaultAvatar = 'https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png';
const searchKeyword = document.getElementById('searchKeyword');
const filterStatus = document.getElementById('filterStatus');

window.loadUsers = loadUsers;
loadUsers(1);

document.getElementById('searchBtn').addEventListener('click', () => loadUsers(1));
document.getElementById('resetFilterBtn').addEventListener('click', () => {
    searchKeyword.value = '';
    filterStatus.value = '';
    loadUsers(1);
});
searchKeyword.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') loadUsers(1);
});

async function loadUsers(pageNum) {
    tbody.innerHTML = '<tr><td colspan="7">加载中...</td></tr>';
    try {
        const params = new URLSearchParams();
        const kw = searchKeyword.value.trim();
        const st = filterStatus.value;
        if (kw) params.set('keyword', kw);
        if (st) params.set('status', st);
        params.set('pageNum', pageNum);
        params.set('pageSize', pageData.pageSize);
        const result = await apiGet('/admin/users?' + params.toString());
        if (result.code === 1) {
            pageData = result.data;
            render();
            renderPagination();
        } else {
            tbody.innerHTML = `<tr><td colspan="7">${escapeHtml(result.msg || '加载失败')}</td></tr>`;
        }
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="7">${escapeHtml(error.message || '网络错误')}</td></tr>`;
    }
}

function render() {
    const list = pageData.list || [];
    if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="7">暂无匹配的用户</td></tr>';
        return;
    }
    tbody.innerHTML = '';
    list.forEach((user, i) => {
        const tr = document.createElement('tr');
        const isRestricted = user.status === 'restricted';
        if (isRestricted) tr.style.background = '#fef0f0';
        tr.innerHTML = `
            <td>${(pageData.pageNum - 1) * pageData.pageSize + i + 1}</td>
            <td><img src="${escapeHtml(user.avatar || defaultAvatar)}" style="width:36px;height:36px;border-radius:50%;object-fit:cover;" alt="头像"></td>
            <td>${escapeHtml(user.nickname)}</td>
            <td>${escapeHtml(user.phone || '-')}</td>
            <td>${statusTag(user.status)}</td>
            <td>${user.noshowCount || 0}</td>
            <td>${isRestricted ? `<button class="btn" data-action="unlock" data-id="${user.id}" style="font-size:12px;height:28px;background:#e07b39;">解锁</button>` : '-'}</td>
        `;
        tbody.appendChild(tr);
    });
}

function renderPagination() {
    if (!pageData.pages || pageData.total === 0) {
        paginationEl.innerHTML = '';
        return;
    }
    if (pageData.pages <= 1) {
        paginationEl.innerHTML = '<span class="page-info">共 ' + pageData.total + ' 条</span>';
        return;
    }
    let html = '<span class="page-info">共 ' + pageData.total + ' 条</span>';
    html += '<button class="page-btn" ' + (pageData.pageNum <= 1 ? 'disabled' : '') + ' onclick="loadUsers(1)">首页</button>';
    html += '<button class="page-btn" ' + (pageData.pageNum <= 1 ? 'disabled' : '') + ' onclick="loadUsers(' + (pageData.pageNum - 1) + ')">上一页</button>';
    let start = Math.max(1, pageData.pageNum - 2);
    let end = Math.min(pageData.pages, pageData.pageNum + 2);
    if (start > 1) html += '<span class="page-ellipsis">...</span>';
    for (let i = start; i <= end; i++) {
        html += '<button class="page-btn' + (i === pageData.pageNum ? ' active' : '') + '" onclick="loadUsers(' + i + ')">' + i + '</button>';
    }
    if (end < pageData.pages) html += '<span class="page-ellipsis">...</span>';
    html += '<button class="page-btn" ' + (pageData.pageNum >= pageData.pages ? 'disabled' : '') + ' onclick="loadUsers(' + (pageData.pageNum + 1) + ')">下一页</button>';
    html += '<button class="page-btn" ' + (pageData.pageNum >= pageData.pages ? 'disabled' : '') + ' onclick="loadUsers(' + pageData.pages + ')">末页</button>';
    html += '<span class="page-info">第 ' + pageData.pageNum + '/' + pageData.pages + ' 页</span>';
    paginationEl.innerHTML = html;
}

function statusTag(status) {
    if (status === 'active') return '<span class="tag success">正常</span>';
    if (status === 'restricted') return '<span class="tag error">已限制</span>';
    return `<span class="tag">${escapeHtml(status || '未知')}</span>`;
}

// Event delegation for unlock
tbody.addEventListener('click', async (e) => {
    const btn = e.target.closest('button[data-action="unlock"]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    const user = pageData.list.find(u => u.id === id);
    if (!confirm(`确定解锁用户「${user?.nickname || id}」吗？`)) return;

    try {
        const result = await apiPost(`/admin/users/${id}/status`, {status: 'active'});
        if (result.code === 1) {
            loadUsers(pageData.pageNum);
        } else {
            alert(result.msg || '解锁失败');
        }
    } catch (error) {
        alert(error.message || '网络错误');
    }
});

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
}
