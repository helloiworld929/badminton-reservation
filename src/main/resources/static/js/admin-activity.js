const defaultImage = 'https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png';
let activities = [];
let editingId = null;

const tbody = document.querySelector('#activityTable tbody');
const form = document.getElementById('activityForm');
const formTitle = document.getElementById('formTitle');
const submitBtn = document.getElementById('submitBtn');
const cancelEditBtn = document.getElementById('cancelEditBtn');
const signupModal = document.getElementById('signupModal');
const signupTbody = document.querySelector('#signupTable tbody');
const actImageHidden = document.getElementById('actImage');
const actImageFile = document.getElementById('actImageFile');
const actImagePreview = document.getElementById('actImagePreview');
const coverPlaceholder = document.getElementById('coverPlaceholder');
const coverUploadWrap = document.getElementById('coverUploadWrap');
const uploadMsg = document.getElementById('uploadMsg');

form.addEventListener('submit', saveActivity);
cancelEditBtn.addEventListener('click', resetForm);
document.getElementById('closeSignupModal').addEventListener('click', closeSignupModal);
signupModal.addEventListener('click', e => { if (e.target === signupModal) closeSignupModal(); });

// Image upload
actImageFile.addEventListener('change', async () => {
    const file = actImageFile.files[0];
    if (!file) return;

    // Show preview immediately
    const reader = new FileReader();
    reader.onload = e => {
        actImagePreview.src = e.target.result;
        actImagePreview.style.display = 'block';
        coverPlaceholder.style.display = 'none';
        coverUploadWrap.classList.add('has-image');
    };
    reader.readAsDataURL(file);

    uploadMsg.textContent = '上传中...';
    uploadMsg.className = 'cover-upload-msg';
    try {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch(API_BASE + '/admin/activities/upload-image', {
            method: 'POST',
            body: formData,
            credentials: 'include'
        });
        const result = await response.json();
        if (result.code === 1) {
            actImageHidden.value = result.data.url;
            uploadMsg.textContent = '✓ 上传成功';
            uploadMsg.className = 'cover-upload-msg success';
        } else {
            uploadMsg.textContent = result.msg || '上传失败';
            uploadMsg.className = 'cover-upload-msg error';
        }
    } catch (error) {
        uploadMsg.textContent = '上传失败';
        uploadMsg.className = 'cover-upload-msg error';
    }
});

loadActivities();

async function loadActivities() {
    tbody.innerHTML = '<tr><td colspan="7">加载中...</td></tr>';
    try {
        const result = await apiGet('/admin/activities');
        if (result.code === 1) {
            activities = result.data || [];
            renderTable();
        } else {
            tbody.innerHTML = `<tr><td colspan="7">${escapeHtml(result.msg || '加载失败')}</td></tr>`;
        }
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="7">${escapeHtml(error.message || '网络错误')}</td></tr>`;
    }
}

function renderTable() {
    if (!activities.length) {
        tbody.innerHTML = '<tr><td colspan="7">暂无活动</td></tr>';
        return;
    }
    tbody.innerHTML = '';
    activities.forEach((act, i) => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${i + 1}</td>
            <td style="text-align:left;">${escapeHtml(act.title)}</td>
            <td>${escapeHtml(act.activityTime || '-')}</td>
            <td>${escapeHtml(act.location || '-')}</td>
            <td style="text-align:left;max-width:200px;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(truncate(act.description, 30))}</td>
            <td><a href="javascript:void(0)" class="signup-link" data-id="${act.id}" data-title="${escapeAttr(act.title)}">${act.signupCount || 0} 人</a></td>
            <td>
                <button class="btn" data-action="edit" data-id="${act.id}" style="font-size:12px;height:28px;margin-right:4px;">编辑</button>
                <button class="btn" data-action="delete" data-id="${act.id}" style="font-size:12px;height:28px;background:#b42318;">删除</button>
            </td>
        `;
        tbody.appendChild(tr);
    });

    // Bind signup link clicks
    tbody.querySelectorAll('.signup-link').forEach(link => {
        link.addEventListener('click', () => {
            const id = Number(link.dataset.id);
            const title = link.dataset.title;
            loadSignups(id, title);
        });
    });

    // Bind action buttons via event delegation
    tbody.addEventListener('click', async (e) => {
        const btn = e.target.closest('button[data-action]');
        if (!btn) return;
        const id = Number(btn.dataset.id);
        if (btn.dataset.action === 'edit') {
            startEdit(id);
        } else if (btn.dataset.action === 'delete') {
            await deleteActivity(id);
        }
    });
}

// ==== Form: Save (Create or Update) ====
async function saveActivity(e) {
    e.preventDefault();
    const body = {
        title: document.getElementById('actTitle').value.trim(),
        activityTime: document.getElementById('actTime').value.trim(),
        location: document.getElementById('actLocation').value.trim(),
        description: document.getElementById('actDesc').value.trim(),
        imageUrl: document.getElementById('actImage').value.trim()
    };
    if (!body.title || !body.activityTime || !body.location) {
        alert('标题、活动时间、地点为必填项');
        return;
    }

    try {
        let result;
        if (editingId) {
            result = await apiPut(`/admin/activities/${editingId}`, body);
        } else {
            result = await apiPost('/admin/activities', body);
        }
        if (result.code === 1) {
            resetForm();
            await loadActivities();
        } else {
            alert(result.msg || '操作失败');
        }
    } catch (error) {
        alert(error.message || '网络错误');
    }
}

function startEdit(id) {
    const act = activities.find(a => a.id === id);
    if (!act) return;
    editingId = id;
    document.getElementById('editId').value = id;
    document.getElementById('actTitle').value = act.title || '';
    document.getElementById('actTime').value = act.activityTime || '';
    document.getElementById('actLocation').value = act.location || '';
    document.getElementById('actDesc').value = act.description || '';
    actImageHidden.value = act.imageUrl || '';
    actImageFile.value = '';
    uploadMsg.textContent = '';
    uploadMsg.className = 'cover-upload-msg';

    const hasImg = !!act.imageUrl;
    actImagePreview.src = act.imageUrl || '';
    actImagePreview.style.display = hasImg ? 'block' : 'none';
    coverPlaceholder.style.display = hasImg ? 'none' : 'flex';
    if (hasImg) {
        coverUploadWrap.classList.add('has-image');
    } else {
        coverUploadWrap.classList.remove('has-image');
    }

    formTitle.textContent = '编辑活动';
    submitBtn.textContent = '保存修改';
    cancelEditBtn.style.display = '';
    document.getElementById('actTitle').focus();
}

function resetForm() {
    editingId = null;
    document.getElementById('editId').value = '';
    actImageHidden.value = '';
    actImageFile.value = '';
    actImagePreview.src = '';
    actImagePreview.style.display = 'none';
    coverPlaceholder.style.display = 'flex';
    coverUploadWrap.classList.remove('has-image');
    uploadMsg.textContent = '';
    uploadMsg.className = 'cover-upload-msg';
    form.reset();
    actImageHidden.value = '';
    formTitle.textContent = '新增活动';
    submitBtn.textContent = '添加活动';
    cancelEditBtn.style.display = 'none';
}

async function deleteActivity(id) {
    const act = activities.find(a => a.id === id);
    if (!confirm(`确定删除活动「${act?.title || id}」吗？\n此操作不可撤销。`)) return;
    try {
        const result = await apiDelete(`/admin/activities/${id}`);
        if (result.code === 1) {
            if (editingId === id) resetForm();
            await loadActivities();
        } else {
            alert(result.msg || '删除失败');
        }
    } catch (error) {
        alert(error.message || '网络错误');
    }
}

// ==== Signup Modal ====
async function loadSignups(activityId, activityTitle) {
    document.getElementById('signupActivityTitle').textContent = activityTitle;
    signupTbody.innerHTML = '<tr><td colspan="6">加载中...</td></tr>';
    signupModal.classList.add('show');
    try {
        const result = await apiGet(`/admin/activities/${activityId}/signups`);
        if (result.code === 1) {
            const signups = result.data || [];
            if (!signups.length) {
                signupTbody.innerHTML = '<tr><td colspan="6">暂无报名记录</td></tr>';
            } else {
                signupTbody.innerHTML = signups.map((s, i) => `
                    <tr>
                        <td>${i + 1}</td>
                        <td><img src="${escapeHtml(s.avatar || defaultImage)}" style="width:36px;height:36px;border-radius:50%;object-fit:cover;" alt="头像"></td>
                        <td>${escapeHtml(s.nickname || '-')}</td>
                        <td>${escapeHtml(s.name)}</td>
                        <td>${escapeHtml(s.phone)}</td>
                        <td>${s.participantCount || 1}</td>
                    </tr>
                `).join('');
            }
        } else {
            signupTbody.innerHTML = `<tr><td colspan="6">${escapeHtml(result.msg || '加载失败')}</td></tr>`;
        }
    } catch (error) {
        signupTbody.innerHTML = `<tr><td colspan="6">${escapeHtml(error.message || '网络错误')}</td></tr>`;
    }
}

function closeSignupModal() {
    signupModal.classList.remove('show');
}

// ==== Helpers ====
function truncate(text, maxLen) {
    if (!text) return '-';
    return text.length > maxLen ? text.substring(0, maxLen) + '...' : text;
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
}

function escapeAttr(value) {
    return String(value).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/'/g, '&#39;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
