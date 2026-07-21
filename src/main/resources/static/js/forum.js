const postList = document.getElementById('postList');
const loadMoreContainer = document.getElementById('postLoadMore');
const postModal = document.getElementById('postModal');
const postForm = document.getElementById('postForm');
const imageInput = document.getElementById('postImages');
const imagePreview = document.getElementById('imagePreview');
const PAGE_SIZE = 6;

let nextPage = 1;
let selectedCategory = '';
let previewObjectUrls = [];
let loading = false;
let hasMore = true;
let loadFailed = false;
let totalPosts = 0;
let requestGeneration = 0;
const loadedPostIds = new Set();

document.getElementById('searchBtn').addEventListener('click', resetPostsAndLoad);
document.getElementById('keywordInput').addEventListener('keydown', e => {
    if (e.key === 'Enter') { e.preventDefault(); resetPostsAndLoad(); }
});
document.querySelectorAll('.forum-category').forEach(button => {
    button.addEventListener('click', () => {
        document.querySelectorAll('.forum-category').forEach(item => {
            item.classList.remove('active');
            item.setAttribute('aria-pressed', 'false');
        });
        button.classList.add('active');
        button.setAttribute('aria-pressed', 'true');
        selectedCategory = button.dataset.category || '';
        resetPostsAndLoad();
    });
});
document.getElementById('openPostModal').addEventListener('click', () => postModal.classList.add('show'));
document.getElementById('closePostModal').addEventListener('click', closePostModal);
document.getElementById('cancelPost').addEventListener('click', closePostModal);
postModal.addEventListener('click', e => { if (e.target === postModal) closePostModal(); });
postForm.addEventListener('submit', publishPost);
imageInput.addEventListener('change', previewImages);

resetPostsAndLoad();

function resetPostsAndLoad() {
    // 递增代次，使筛选前尚未完成的请求不能覆盖新结果。
    requestGeneration++;
    nextPage = 1;
    loading = false;
    hasMore = true;
    loadFailed = false;
    totalPosts = 0;
    loadedPostIds.clear();
    postList.classList.add('is-empty');
    postList.innerHTML = '<div class="empty">加载中...</div>';
    renderLoadMore();
    return loadPosts();
}

async function loadPosts() {
    if (loading || !hasMore) return;

    const generation = requestGeneration;
    const requestedPage = nextPage;
    const keyword = document.getElementById('keywordInput').value.trim();
    const params = new URLSearchParams({ pageNum: requestedPage, pageSize: PAGE_SIZE });
    if (selectedCategory) params.set('category', selectedCategory);
    if (keyword) params.set('keyword', keyword);

    loading = true;
    loadFailed = false;
    renderLoadMore();
    try {
        const result = await apiGet(`/forum/posts?${params}`);
        if (result.code !== 1) throw new Error(result.msg || '加载失败');
        if (generation !== requestGeneration) return;

        const data = result.data || {};
        const posts = data.list || [];
        appendPosts(posts);
        totalPosts = Number(data.total) || 0;
        nextPage = (Number(data.pageNum) || requestedPage) + 1;
        hasMore = (Number(data.pageNum) || requestedPage) < (Number(data.pages) || 0);

        if (requestedPage === 1 && loadedPostIds.size === 0) {
            postList.classList.add('is-empty');
            postList.innerHTML = '<div class="empty">暂无符合条件的帖子</div>';
            hasMore = false;
        }
    } catch (error) {
        if (generation !== requestGeneration) return;
        loadFailed = true;
        if (loadedPostIds.size === 0) {
            postList.classList.add('is-empty');
            postList.innerHTML = `<div class="empty">${escapeHtml(error.message || '网络错误')}</div>`;
        }
    } finally {
        if (generation === requestGeneration) {
            loading = false;
            renderLoadMore();
        }
    }
}

function appendPosts(posts) {
    const newPosts = posts.filter(post => {
        const id = String(post.id);
        if (loadedPostIds.has(id)) return false;
        loadedPostIds.add(id);
        return true;
    });
    if (!newPosts.length) return;

    if (!postList.querySelector('.forum-card')) {
        postList.innerHTML = '';
    }
    postList.classList.remove('is-empty');
    postList.insertAdjacentHTML('beforeend', newPosts.map(postCardHtml).join(''));
}

function postCardHtml(post) {
    const imageUrls = (post.imageUrls || []).slice(0, 3);
    const images = imageUrls.map(url =>
        `<img src="${escapeAttr(url)}" alt="帖子图片" loading="lazy">`).join('');
    return `<article class="forum-card" data-id="${post.id}">
        <a class="forum-card-link" href="forum-detail.html?id=${Number(post.id)}">
            <div class="forum-card-heading tone-${categoryTone(post.category)}">
                <div class="forum-card-heading-tags">${post.pinned ? '<span class="forum-card-pin">置顶</span>' : ''}<span>${escapeHtml(post.category)}</span></div>
                <h3>${escapeHtml(post.title)}</h3>
            </div>
            <div class="forum-card-body">
                <p class="forum-card-excerpt">${escapeHtml(truncate(post.content, 120))}</p>
                ${images ? `<div class="forum-card-images image-count-${imageUrls.length}">${images}</div>` : '<div class="forum-card-no-image">暂无图片</div>'}
            </div>
            <div class="forum-card-footer forum-meta">
                <span>${escapeHtml(post.authorNickname || '校内用户')}</span>
                <span>${formatDate(post.createdAt)}</span>
                <span>浏览 ${post.viewCount || 0}</span>
                <span>回复 ${post.replyCount || 0}</span>
            </div>
        </a>
    </article>`;
}

function categoryTone(category) {
    return ({
        '约球组队': 'team',
        '技术交流': 'tech',
        '赛事活动': 'event',
        '场地反馈': 'feedback',
        '失物招领': 'lost',
        '其他': 'other'
    })[category] || 'other';
}

function previewImages() {
    const files = Array.from(imageInput.files || []);
    if (files.length > 3) {
        alert('每个帖子最多上传3张图片');
        imageInput.value = '';
        clearImagePreview();
        return;
    }
    const invalid = files.find(file => !file.type.startsWith('image/') || file.size > 5 * 1024 * 1024);
    if (invalid) {
        alert('只支持5MB以内的图片');
        imageInput.value = '';
        clearImagePreview();
        return;
    }
    clearImagePreview();
    previewObjectUrls = files.map(file => URL.createObjectURL(file));
    imagePreview.innerHTML = files.map((file, index) =>
        `<div class="forum-preview-item">
            <img src="${previewObjectUrls[index]}" alt="${escapeAttr(file.name)}">
            <span>${index + 1}</span>
        </div>`).join('');
}

async function publishPost(event) {
    event.preventDefault();
    const publishBtn = document.getElementById('publishBtn');
    const message = document.getElementById('postMessage');
    const formData = new FormData();
    formData.append('title', document.getElementById('postTitle').value.trim());
    formData.append('category', document.getElementById('postCategory').value);
    formData.append('content', document.getElementById('postContent').value.trim());
    Array.from(imageInput.files || []).forEach(file => formData.append('images', file));
    publishBtn.disabled = true;
    message.textContent = '正在发布...';
    try {
        const response = await fetch('/forum/posts', { method: 'POST', body: formData, credentials: 'include' });
        const result = await readJson(response);
        if (result.code !== 1) throw new Error(result.msg || '发布失败');
        closePostModal();
        await resetPostsAndLoad();
    } catch (error) {
        message.textContent = error.message || '发布失败';
    } finally {
        publishBtn.disabled = false;
    }
}

function closePostModal() {
    postModal.classList.remove('show');
    postForm.reset();
    clearImagePreview();
    document.getElementById('postMessage').textContent = '';
}

function clearImagePreview() {
    previewObjectUrls.forEach(url => URL.revokeObjectURL(url));
    previewObjectUrls = [];
    imagePreview.innerHTML = '';
}

function renderLoadMore() {
    if (loading) {
        loadMoreContainer.innerHTML = '<button class="btn secondary forum-load-more-btn" type="button" disabled>加载中...</button>';
        return;
    }
    if (loadFailed) {
        loadMoreContainer.innerHTML = '<button class="btn secondary forum-load-more-btn" type="button" data-load-more>重新加载</button>';
    } else if (loadedPostIds.size === 0) {
        loadMoreContainer.innerHTML = '';
        return;
    } else if (hasMore) {
        loadMoreContainer.innerHTML = '<button class="btn secondary forum-load-more-btn" type="button" data-load-more>加载更多</button>';
    } else {
        loadMoreContainer.innerHTML = `<span class="forum-load-complete">已加载全部，共 ${totalPosts} 条</span>`;
        return;
    }
    loadMoreContainer.querySelector('[data-load-more]').addEventListener('click', loadPosts);
}

function truncate(value, length) { const text = String(value || ''); return text.length > length ? `${text.slice(0, length)}...` : text; }
function formatDate(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-'; }
function escapeHtml(value) { return String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch])); }
function escapeAttr(value) { return escapeHtml(value); }
