let currentUser = null;
const modal = document.getElementById('modifyModal');
const form = document.getElementById('modifyForm');
const defaultAvatar = 'https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png';

document.getElementById('modifyBtn').addEventListener('click', openModal);
document.getElementById('closeModify').addEventListener('click', closeModal);
document.getElementById('cancelModify').addEventListener('click', closeModal);
modal.addEventListener('click', event => {
    if (event.target === modal) closeModal();
});
form.addEventListener('submit', saveUser);

// 密码区折叠
document.getElementById('togglePasswordBtn').addEventListener('click', () => {
    const fields = document.getElementById('passwordFields');
    const btn = document.getElementById('togglePasswordBtn');
    if (fields.style.display === 'none') {
        fields.style.display = '';
        btn.textContent = '修改密码 ▴';
    } else {
        fields.style.display = 'none';
        btn.textContent = '修改密码 ▾';
        document.getElementById('modalNewPassword').value = '';
        document.getElementById('modalConfirmPassword').value = '';
    }
});

// 头像 —— 点击直接换，上传后自动保存
const avatarWrap = document.getElementById('avatarWrap');
const avatarInput = document.getElementById('avatarInput');
avatarWrap.addEventListener('click', () => avatarInput.click());

avatarInput.addEventListener('change', async () => {
    const file = avatarInput.files[0];
    if (!file) return;

    // 本地预览
    const reader = new FileReader();
    reader.onload = e => document.getElementById('avatar').src = e.target.result;
    reader.readAsDataURL(file);

    try {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch(API_BASE + '/upload-avatar', {
            method: 'POST',
            body: formData,
            credentials: 'include'
        });
        const result = await response.json();
        if (result.code === 1) {
            const saveResult = await apiPost('/user', { avatar: result.data.url });
            if (saveResult.code === 1) {
                currentUser = saveResult.data;
                alert('头像已更新');
                // 同步右上角头像
                if (window.parent && window.parent.document) {
                    const topAvatar = window.parent.document.getElementById('welcomeAvatar');
                    if (topAvatar) topAvatar.src = currentUser.avatar || defaultAvatar;
                }
            }
        } else {
            alert(result.msg || '头像上传失败');
            renderUser(currentUser);
        }
    } catch (error) {
        alert('头像上传失败');
        renderUser(currentUser);
    }
});

loadUser();

async function loadUser() {
    try {
        const result = await apiGet('/user');
        if (result.code === 1) {
            currentUser = result.data;
            renderUser(currentUser);
        } else {
            alert(result.msg || '用户信息加载失败');
        }
    } catch (error) {
        alert(error.message || '网络错误，请稍后重试');
    }
}

function renderUser(user) {
    document.getElementById('avatar').src = user.avatar || defaultAvatar;
    document.getElementById('nickname').textContent = user.nickname;
    document.getElementById('gender').textContent = user.gender;
    document.getElementById('age').textContent = user.age;
    document.getElementById('phone').textContent = user.phone;
    const statusEl = document.getElementById('status');
    if (user.status === 'active') {
        statusEl.textContent = '正常';
        statusEl.className = 'tag success';
    } else if (user.status === 'restricted') {
        statusEl.textContent = '已限制';
        statusEl.className = 'tag error';
    } else {
        statusEl.textContent = user.status || '未知';
        statusEl.className = 'tag';
    }
}

function openModal() {
    if (!currentUser) return;
    document.getElementById('modalNickname').value = currentUser.nickname || '';
    document.getElementById('modalGender').value = currentUser.gender || '男';
    document.getElementById('modalAge').value = currentUser.age || 0;
    document.getElementById('modalPhone').value = '';
    document.getElementById('modalSmsCode').value = '';
    document.getElementById('modalNewPassword').value = '';
    document.getElementById('modalConfirmPassword').value = '';
    resetSmsBtn();
    modal.classList.add('show');
}

// ====== 短信验证码 ======
let smsTimer = null;

document.getElementById('modalSendSmsBtn').addEventListener('click', async () => {
    const phone = document.getElementById('modalPhone').value.trim();
    if (!/^1[3-9]\d{9}$/.test(phone)) {
        alert('请输入正确的手机号');
        return;
    }
    try {
        const result = await apiPost('/user/send-phone-code', { phone });
        if (result.code === 1) {
            alert('验证码已发送');
            startSmsCountdown(60);
        } else {
            alert(result.msg || '发送失败');
        }
    } catch (error) {
        alert(error.message || '发送失败，请重试');
    }
});

function startSmsCountdown(seconds) {
    const btn = document.getElementById('modalSendSmsBtn');
    btn.disabled = true;
    clearInterval(smsTimer);
    function tick() {
        if (seconds <= 0) {
            resetSmsBtn();
            return;
        }
        btn.textContent = seconds + 's 后重试';
        seconds--;
        smsTimer = setTimeout(tick, 1000);
    }
    tick();
}

function resetSmsBtn() {
    const btn = document.getElementById('modalSendSmsBtn');
    btn.disabled = false;
    btn.textContent = '获取验证码';
    clearInterval(smsTimer);
}

function closeModal() {
    modal.classList.remove('show');
}

async function saveUser(event) {
    event.preventDefault();

    const phone = document.getElementById('modalPhone').value.trim();
    const smsCode = document.getElementById('modalSmsCode').value.trim();
    const newPassword = document.getElementById('modalNewPassword').value;
    const confirmPassword = document.getElementById('modalConfirmPassword').value;

    // 手机号：填了就验证
    if (phone) {
        if (!/^1[3-9]\d{9}$/.test(phone)) {
            alert('手机号格式不正确');
            return;
        }
        if (!smsCode) {
            alert('请输入短信验证码');
            return;
        }
    }

    // 密码：填了新密码才校验
    if (newPassword) {
        if (newPassword.length < 6 || newPassword.length > 20) {
            alert('新密码长度应为6-20位');
            return;
        }
        if (newPassword !== confirmPassword) {
            alert('两次输入的新密码不一致');
            return;
        }
    }

    try {
        const body = {
            nickname: document.getElementById('modalNickname').value.trim(),
            gender: document.getElementById('modalGender').value,
            age: Number(document.getElementById('modalAge').value)
        };
        if (phone) {
            body.phone = phone;
            body.code = smsCode;
        }
        if (newPassword) {
            body.newPassword = newPassword;
        }
        const result = await apiPost('/user', body);
        if (result.code === 1) {
            currentUser = result.data;
            renderUser(currentUser);
            alert('信息修改成功');
            closeModal();
        } else {
            alert(result.msg || '保存失败');
        }
    } catch (error) {
        alert(error.message || '网络错误，请稍后重试');
    }
}

// ====== 预约记录 ======
let recordPage = { list: [], total: 0, pageNum: 1, pageSize: 5, pages: 0 };
const recordTbody = document.querySelector('#recordTable tbody');
const recordSearch = document.getElementById('recordSearch');
const recordPaginationEl = document.getElementById('recordPagination');

document.getElementById('refreshRecordsBtn').addEventListener('click', () => loadRecords(1));
recordSearch.addEventListener('input', renderRecords);

window.loadRecords = loadRecords;
loadRecords(1);

async function loadRecords(pageNum) {
    recordTbody.innerHTML = '<tr><td colspan="9">加载中...</td></tr>';
    try {
        const result = await apiGet('/reservation-records?pageNum=' + pageNum + '&pageSize=' + recordPage.pageSize);
        if (result.code === 1) {
            recordPage = result.data;
            renderRecords();
            renderRecordPagination();
        } else {
            recordTbody.innerHTML = `<tr><td colspan="9">${escapeHtml(result.msg || '加载失败')}</td></tr>`;
        }
    } catch (error) {
        recordTbody.innerHTML = `<tr><td colspan="9">${escapeHtml(error.message || '网络错误')}</td></tr>`;
    }
}

function renderRecords() {
    const list = recordPage.list || [];
    const keyword = recordSearch.value.trim().toLowerCase();
    const filtered = keyword ? list.filter(item =>
        (item.courtName || '').toLowerCase().includes(keyword)) : list;
    if (!filtered.length) {
        recordTbody.innerHTML = '<tr><td colspan="9">暂无预约记录</td></tr>';
        recordPaginationEl.innerHTML = recordPage.total > recordPage.pageSize ? '' : '';
        return;
    }
    recordTbody.innerHTML = '';
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
            <td><span class="tag ${recordStatusClass(item.status)}">${item.statusDisplay || recordStatusText(item.status)}</span></td>
            <td><code class="verify-code">${escapeHtml(item.verificationCode)}</code></td>
            <td>${canCancel ? `<button class="btn cancel-record-btn" data-id="${item.id}" style="font-size:12px;height:28px;background:#b42318;">取消</button>` : '<span style="color:#999;">-</span>'}</td>
        `;
        recordTbody.appendChild(tr);
    });

    recordTbody.querySelectorAll('.cancel-record-btn').forEach(btn => {
        btn.addEventListener('click', () => cancelUserRecord(Number(btn.dataset.id)));
    });
}

function renderRecordPagination() {
    if (!recordPage.pages || recordPage.total === 0) {
        recordPaginationEl.innerHTML = '';
        return;
    }
    if (recordPage.pages <= 1) {
        recordPaginationEl.innerHTML = '<span class="page-info">共 ' + recordPage.total + ' 条</span>';
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
    recordPaginationEl.innerHTML = html;
}

function recordStatusText(status) {
    switch (status) {
        case 'unverified': return '未验证';
        case 'verified': return '已验证';
        case 'noshow': return '爽约';
        case 'cancelled': return '已取消';
        default: return status || '未知';
    }
}

function recordStatusClass(status) {
    switch (status) {
        case 'unverified': return 'warning';
        case 'verified': return 'success';
        case 'noshow': return 'error';
        case 'cancelled': return '';
        default: return '';
    }
}

async function cancelUserRecord(id) {
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
