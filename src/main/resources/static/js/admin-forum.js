const postTbody = document.querySelector('#forumPostTable tbody');
const reportTbody = document.querySelector('#forumReportTable tbody');
const postModal = document.getElementById('adminPostModal');
const reportHandleModal = document.getElementById('reportHandleModal');
let postPage = 1;
let reportPage = 1;
let pendingReportAction = null;

document.getElementById('adminSearch').addEventListener('click', () => { postPage = 1; loadPosts(); });
document.getElementById('loadReports').addEventListener('click', () => { reportPage = 1; loadReports(); });
document.getElementById('closeAdminPost').addEventListener('click', () => postModal.classList.remove('show'));
postModal.addEventListener('click', e => { if (e.target === postModal) postModal.classList.remove('show'); });
document.getElementById('closeReportHandle').addEventListener('click', closeReportHandleModal);
document.getElementById('cancelReportHandle').addEventListener('click', closeReportHandleModal);
document.getElementById('reportHandleForm').addEventListener('submit', submitReportHandle);
reportHandleModal.addEventListener('click', e => { if (e.target === reportHandleModal) closeReportHandleModal(); });

loadPosts();
loadReports();

async function loadPosts() {
    postTbody.innerHTML = '<tr><td colspan="8">加载中...</td></tr>';
    const params = new URLSearchParams({ pageNum: postPage, pageSize: 10 });
    const category = document.getElementById('adminCategory').value;
    const status = document.getElementById('adminStatus').value;
    const keyword = document.getElementById('adminKeyword').value.trim();
    if (category) params.set('category', category); if (status) params.set('status', status); if (keyword) params.set('keyword', keyword);
    try {
        const result = await apiGet(`/admin/forum/posts?${params}`);
        if (result.code !== 1) throw new Error(result.msg || '加载失败');
        renderPosts(result.data.list || []);
        renderPagination(document.getElementById('adminPostPagination'), result.data, page => { postPage = page; loadPosts(); });
    } catch (error) { postTbody.innerHTML = `<tr><td colspan="8">${escapeHtml(error.message)}</td></tr>`; }
}

function renderPosts(posts) {
    if (!posts.length) { postTbody.innerHTML = '<tr><td colspan="8">暂无帖子</td></tr>'; return; }
    postTbody.innerHTML = posts.map(post => `<tr>
        <td>${post.id}</td><td class="forum-table-title">${post.pinned ? '<span class="tag warning">置顶</span> ' : ''}${escapeHtml(post.title)}</td>
        <td>${escapeHtml(post.category)}</td><td>${escapeHtml(post.authorNickname || '-')}</td><td>${statusText(post.status)}</td>
        <td>${post.viewCount || 0} / ${post.replyCount || 0}</td><td>${formatDate(post.createdAt)}</td>
        <td class="forum-admin-actions"><button class="btn mini" data-view="${post.id}">查看</button>
        ${post.status === 'normal' ? `<button class="btn mini secondary" data-pin="${post.id}" data-value="${!post.pinned}">${post.pinned ? '取消置顶' : '置顶'}</button><button class="btn mini secondary" data-status="hidden" data-id="${post.id}">隐藏</button>` : `<button class="btn mini secondary" data-status="normal" data-id="${post.id}">恢复</button>`}
        ${post.status !== 'deleted' ? `<button class="btn mini danger" data-status="deleted" data-id="${post.id}">删除</button>` : ''}</td></tr>`).join('');
    postTbody.querySelectorAll('[data-view]').forEach(btn => btn.addEventListener('click', () => viewPost(Number(btn.dataset.view))));
    postTbody.querySelectorAll('[data-pin]').forEach(btn => btn.addEventListener('click', () => updatePin(Number(btn.dataset.pin), btn.dataset.value === 'true')));
    postTbody.querySelectorAll('[data-status]').forEach(btn => btn.addEventListener('click', () => updatePostStatus(Number(btn.dataset.id), btn.dataset.status)));
}

async function viewPost(id) {
    try {
        const result = await apiGet(`/admin/forum/posts/${id}`);
        if (result.code !== 1) throw new Error(result.msg || '加载失败');
        const post = result.data.post;
        const replies = result.data.replies.list || [];
        document.getElementById('adminPostDetail').innerHTML = `<div class="forum-tags"><span class="tag success">${escapeHtml(post.category)}</span><span>${statusText(post.status)}</span></div><h2>${escapeHtml(post.title)}</h2><div class="forum-meta"><span>${escapeHtml(post.authorNickname || '-')}</span><span>${formatDate(post.createdAt)}</span><span>浏览 ${post.viewCount || 0}</span></div><div class="forum-content">${escapeHtml(post.content)}</div><h3>回复管理</h3><div class="forum-replies">${replies.length ? replies.map(reply => `<article class="forum-reply"><div class="forum-reply-head"><span>${escapeHtml(reply.authorNickname || '-')}</span><span>${statusText(reply.status)}</span></div><div class="forum-reply-content">${escapeHtml(reply.content)}</div><div class="forum-inline-actions">${reply.status === 'normal' ? `<button class="text-btn" data-reply-status="hidden" data-reply-id="${reply.id}">隐藏</button>` : `<button class="text-btn" data-reply-status="normal" data-reply-id="${reply.id}">恢复</button>`}${reply.status !== 'deleted' ? `<button class="text-btn danger-text" data-reply-status="deleted" data-reply-id="${reply.id}">删除</button>` : ''}</div></article>`).join('') : '<div class="empty forum-empty-inline">暂无回复</div>'}</div>`;
        postModal.classList.add('show');
        document.querySelectorAll('[data-reply-status]').forEach(btn => btn.addEventListener('click', async () => {
            try {
                const updateResult = await apiPut(`/admin/forum/replies/${btn.dataset.replyId}/status`, { status: btn.dataset.replyStatus });
                if (updateResult.code !== 1) throw new Error(updateResult.msg || '操作失败');
                await viewPost(id);
            } catch (error) { alert(error.message || '操作失败'); }
        }));
    } catch (error) { alert(error.message || '加载失败'); }
}

async function updatePin(id, pinned) {
    try {
        const result = await apiPut(`/admin/forum/posts/${id}/pin`, { pinned });
        if (result.code !== 1) throw new Error(result.msg || '操作失败');
        await loadPosts();
    } catch (error) { alert(error.message || '操作失败'); }
}
async function updatePostStatus(id, status) {
    if (status === 'deleted' && !confirm('确定逻辑删除该帖子吗？')) return;
    try {
        const result = await apiPut(`/admin/forum/posts/${id}/status`, { status });
        if (result.code !== 1) throw new Error(result.msg || '操作失败');
        await loadPosts();
    } catch (error) { alert(error.message || '操作失败'); }
}

async function loadReports() {
    reportTbody.innerHTML = '<tr><td colspan="7">加载中...</td></tr>';
    const params = new URLSearchParams({ pageNum: reportPage, pageSize: 10 });
    const status = document.getElementById('reportStatus').value; if (status) params.set('status', status);
    try {
        const result = await apiGet(`/admin/forum/reports?${params}`);
        if (result.code !== 1) throw new Error(result.msg || '加载失败');
        renderReports(result.data.list || []);
        renderPagination(document.getElementById('adminReportPagination'), result.data, page => { reportPage = page; loadReports(); });
    } catch (error) { reportTbody.innerHTML = `<tr><td colspan="7">${escapeHtml(error.message)}</td></tr>`; }
}

function renderReports(reports) {
    if (!reports.length) { reportTbody.innerHTML = '<tr><td colspan="7">暂无举报</td></tr>'; return; }
    reportTbody.innerHTML = reports.map(report => `<tr><td>${report.id}</td><td>${escapeHtml(report.reporterNickname || '-')}</td><td>${report.targetType === 'post' ? '帖子' : '回复'} #${report.targetId}</td><td>${escapeHtml(report.reason)}</td><td>${reportStatusText(report.status)}</td><td>${formatDate(report.createdAt)}</td><td>${report.status === 'pending' ? `<button class="btn mini" data-report="${report.id}" data-result="resolved">处理</button> <button class="btn mini secondary" data-report="${report.id}" data-result="rejected">驳回</button>` : escapeHtml(report.result || '-')}</td></tr>`).join('');
    reportTbody.querySelectorAll('[data-report]').forEach(btn => btn.addEventListener('click', () => handleReport(Number(btn.dataset.report), btn.dataset.result)));
}

async function handleReport(id, status) {
    pendingReportAction = { id, status };
    document.getElementById('reportHandleTitle').textContent = status === 'resolved' ? '处理举报' : '驳回举报';
    document.getElementById('reportHandleResult').value = '';
    document.getElementById('reportHandleMessage').textContent = '';
    reportHandleModal.classList.add('show');
}

function closeReportHandleModal() {
    pendingReportAction = null;
    reportHandleModal.classList.remove('show');
}

async function submitReportHandle(event) {
    event.preventDefault();
    if (!pendingReportAction) return;
    const confirmButton = document.getElementById('confirmReportHandle');
    const message = document.getElementById('reportHandleMessage');
    confirmButton.disabled = true;
    message.textContent = '正在提交...';
    try {
        const result = await apiPut(`/admin/forum/reports/${pendingReportAction.id}`, {
            status: pendingReportAction.status,
            result: document.getElementById('reportHandleResult').value.trim()
        });
        if (result.code !== 1) throw new Error(result.msg || '处理失败');
        closeReportHandleModal();
        await loadReports();
    } catch (error) {
        message.textContent = error.message || '处理失败';
    } finally {
        confirmButton.disabled = false;
    }
}

function renderPagination(container, data, change) {
    if (!data || data.pages <= 1) { container.innerHTML = ''; return; }
    container.innerHTML = `<button class="page-btn" ${data.pageNum <= 1 ? 'disabled' : ''} data-page="${data.pageNum - 1}">上一页</button><span class="page-info">第 ${data.pageNum} / ${data.pages} 页</span><button class="page-btn" ${data.pageNum >= data.pages ? 'disabled' : ''} data-page="${data.pageNum + 1}">下一页</button>`;
    container.querySelectorAll('[data-page]').forEach(btn => btn.addEventListener('click', () => change(Number(btn.dataset.page))));
}
function statusText(status) { return ({normal:'正常',hidden:'已隐藏',deleted:'已删除'})[status] || status; }
function reportStatusText(status) { return ({pending:'待处理',resolved:'已处理',rejected:'已驳回'})[status] || status; }
function formatDate(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-'; }
function escapeHtml(value) { return String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch])); }
