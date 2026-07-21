const postId = Number(new URLSearchParams(window.location.search).get('id'));
const detailBox = document.getElementById('postDetail');
const replyList = document.getElementById('replyList');
const replyPagination = document.getElementById('replyPagination');
let currentUser = null;
let currentPost = null;
let replyPage = 1;
let reportTarget = null;

document.getElementById('backBtn').addEventListener('click', () => { window.location.href = 'forum.html'; });
document.getElementById('replyForm').addEventListener('submit', submitReply);
document.getElementById('closeReportModal').addEventListener('click', closeReportModal);
document.getElementById('cancelReport').addEventListener('click', closeReportModal);
document.getElementById('reportForm').addEventListener('submit', submitReport);
document.getElementById('reportModal').addEventListener('click', e => {
    if (e.target.id === 'reportModal') closeReportModal();
});

if (!postId) {
    detailBox.innerHTML = '<div class="empty">帖子参数无效</div>';
} else {
    loadCurrentUser().then(loadDetail);
}

async function loadCurrentUser() {
    const result = await apiGet('/user');
    if (result.code === 1) currentUser = result.data;
}

async function loadDetail() {
    try {
        const result = await apiGet(`/forum/posts/${postId}?replyPage=${replyPage}&replyPageSize=20`);
        if (result.code !== 1) throw new Error(result.msg || '加载失败');
        currentPost = result.data.post;
        renderPost(currentPost);
        renderReplies(result.data.replies);
    } catch (error) {
        detailBox.innerHTML = `<div class="empty">${escapeHtml(error.message || '加载失败')}</div>`;
    }
}

function renderPost(post) {
    const gallery = (post.imageUrls || []).map(url => `<img src="${escapeHtml(url)}" alt="帖子图片">`).join('');
    detailBox.innerHTML = `<div class="forum-detail-heading">
        <div class="forum-tags">${post.pinned ? '<span class="tag warning">置顶</span>' : ''}<span class="tag success">${escapeHtml(post.category)}</span></div>
        <h1>${escapeHtml(post.title)}</h1>
        <div class="forum-meta"><span>${escapeHtml(post.authorNickname || '校内用户')}</span><span>${formatDate(post.createdAt)}</span><span>浏览 ${post.viewCount || 0}</span><span>回复 ${post.replyCount || 0}</span></div>
    </div><div class="forum-content">${escapeHtml(post.content)}</div>${gallery ? `<div class="forum-gallery">${gallery}</div>` : ''}`;

    const actions = document.getElementById('postActions');
    actions.innerHTML = '<button class="btn secondary" type="button" id="reportPostBtn">举报帖子</button>';
    document.getElementById('reportPostBtn').addEventListener('click', () => openReport('post', post.id));
    if (currentUser && Number(currentUser.id) === Number(post.userId)) {
        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'btn danger';
        deleteBtn.textContent = '删除帖子';
        deleteBtn.addEventListener('click', deletePost);
        actions.appendChild(deleteBtn);
    }
}

function renderReplies(data) {
    const replies = data.list || [];
    if (!replies.length) {
        replyList.innerHTML = '<div class="empty forum-empty-inline">暂无回复</div>';
    } else {
        replyList.innerHTML = replies.map((reply, index) => `<article class="forum-reply">
            <div class="forum-reply-head"><span>#${(data.pageNum - 1) * data.pageSize + index + 1} ${escapeHtml(reply.authorNickname || '校内用户')}</span><span>${formatDate(reply.createdAt)}</span></div>
            <div class="forum-reply-content">${escapeHtml(reply.content)}</div>
            <div class="forum-inline-actions">
                ${reply.status === 'normal' ? `<button class="text-btn" data-report-reply="${reply.id}">举报</button>` : ''}
                ${currentUser && Number(currentUser.id) === Number(reply.userId) && reply.status === 'normal' ? `<button class="text-btn danger-text" data-delete-reply="${reply.id}">删除</button>` : ''}
            </div>
        </article>`).join('');
        replyList.querySelectorAll('[data-report-reply]').forEach(btn => btn.addEventListener('click', () => openReport('reply', Number(btn.dataset.reportReply))));
        replyList.querySelectorAll('[data-delete-reply]').forEach(btn => btn.addEventListener('click', () => deleteReply(Number(btn.dataset.deleteReply))));
    }
    renderPagination(data);
}

async function submitReply(event) {
    event.preventDefault();
    const input = document.getElementById('replyContent');
    try {
        const result = await apiPost(`/forum/posts/${postId}/replies`, { content: input.value.trim() });
        if (result.code !== 1) throw new Error(result.msg || '回复失败');
        input.value = '';
        await loadDetail();
    } catch (error) { alert(error.message || '回复失败'); }
}

async function deletePost() {
    if (!confirm('确定删除这个帖子吗？发布后不可恢复编辑。')) return;
    const result = await apiDelete(`/forum/posts/${postId}`);
    if (result.code === 1) window.location.href = 'forum.html';
}

async function deleteReply(replyId) {
    if (!confirm('确定删除这条回复吗？')) return;
    const result = await apiDelete(`/forum/replies/${replyId}`);
    if (result.code === 1) await loadDetail();
}

function openReport(targetType, targetId) {
    reportTarget = { targetType, targetId };
    document.getElementById('reportReason').value = '';
    document.getElementById('reportModal').classList.add('show');
}
function closeReportModal() { reportTarget = null; document.getElementById('reportModal').classList.remove('show'); }
async function submitReport(event) {
    event.preventDefault();
    if (!reportTarget) return;
    try {
        const result = await apiPost('/forum/reports', { ...reportTarget, reason: document.getElementById('reportReason').value });
        if (result.code !== 1) throw new Error(result.msg || '举报失败');
        alert('举报已提交'); closeReportModal();
    } catch (error) { alert(error.message || '举报失败'); }
}

function renderPagination(data) {
    if (!data || data.pages <= 1) { replyPagination.innerHTML = ''; return; }
    replyPagination.innerHTML = `<button class="page-btn" ${data.pageNum <= 1 ? 'disabled' : ''} data-page="${data.pageNum - 1}">上一页</button><span class="page-info">第 ${data.pageNum} / ${data.pages} 页</span><button class="page-btn" ${data.pageNum >= data.pages ? 'disabled' : ''} data-page="${data.pageNum + 1}">下一页</button>`;
    replyPagination.querySelectorAll('[data-page]').forEach(btn => btn.addEventListener('click', () => { replyPage = Number(btn.dataset.page); loadDetail(); }));
}
function formatDate(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-'; }
function escapeHtml(value) { return String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch])); }
