// ========== Tab 切换 ==========
document.querySelectorAll('.login-tab').forEach(tab => {
    tab.addEventListener('click', () => {
        document.querySelectorAll('.login-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');

        const target = tab.dataset.tab;
        document.getElementById('loginForm').style.display = target === 'login' ? '' : 'none';
        document.getElementById('registerForm').style.display = target === 'register' ? '' : 'none';
        // Clear messages
        document.getElementById('loginMsg').textContent = '';
        document.getElementById('registerMsg').textContent = '';
    });
});

// ========== 登录 ==========
document.getElementById('loginForm').addEventListener('submit', async event => {
    event.preventDefault();
    const msg = document.getElementById('loginMsg');
    msg.textContent = '';
    try {
        const result = await apiPost('/login', {
            phone: document.getElementById('loginPhone').value.trim(),
            password: document.getElementById('loginPassword').value
        });
        if (result.code === 1) {
            if (result.data && result.data.role === 'admin') {
                window.location.href = 'admin.html';
            } else {
                window.location.href = 'index.html';
            }
        } else {
            msg.textContent = result.msg || '登录失败';
        }
    } catch (error) {
        msg.textContent = error.message || '登录失败';
    }
});

// ========== 注册 ==========
let avatarUrl = '';
let smsTimer = null;

// Avatar upload — label 原生行为已触发 file input，无需 JS 再 click
const avatarInput = document.getElementById('avatarInput');
const avatarPreview = document.getElementById('avatarPreview');

avatarInput.addEventListener('change', async () => {
    const file = avatarInput.files[0];
    if (!file) return;

    // Preview before upload
    const reader = new FileReader();
    reader.onload = e => avatarPreview.src = e.target.result;
    reader.readAsDataURL(file);

    // Upload to OSS
    const msg = document.getElementById('registerMsg');
    msg.style.color = '#526071';
    msg.textContent = '头像上传中...';

    try {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch(API_BASE + '/register/upload-avatar', {
            method: 'POST',
            body: formData
        });
        const result = await response.json();
        if (result.code === 1) {
            avatarUrl = result.data.url;
            msg.textContent = '头像上传成功';
            msg.style.color = '#06965c';
        } else {
            msg.textContent = result.msg || '头像上传失败';
            msg.style.color = '#b42318';
        }
    } catch (error) {
        msg.textContent = '头像上传失败，请重试';
        msg.style.color = '#b42318';
    }
});

// Send SMS code
document.getElementById('sendCodeBtn').addEventListener('click', async () => {
    const phone = document.getElementById('regPhone').value.trim();
    const msg = document.getElementById('registerMsg');
    if (!/^1[3-9]\d{9}$/.test(phone)) {
        msg.textContent = '请输入正确的手机号';
        msg.style.color = '#b42318';
        return;
    }

    try {
        const result = await apiPost('/register/send-code', { phone });
        if (result.code === 1) {
            msg.textContent = '验证码已发送';
            msg.style.color = '#06965c';
            startCountdown(60);
        } else {
            msg.textContent = result.msg || '发送失败';
            msg.style.color = '#b42318';
        }
    } catch (error) {
        msg.textContent = error.message || '发送失败，请重试';
        msg.style.color = '#b42318';
    }
});

function startCountdown(seconds) {
    const btn = document.getElementById('sendCodeBtn');
    btn.disabled = true;
    clearInterval(smsTimer);

    function tick() {
        if (seconds <= 0) {
            btn.disabled = false;
            btn.textContent = '重新获取';
            return;
        }
        btn.textContent = seconds + 's 后重试';
        seconds--;
        smsTimer = setTimeout(tick, 1000);
    }
    tick();
}

// Register form submit
document.getElementById('registerForm').addEventListener('submit', async event => {
    event.preventDefault();
    const msg = document.getElementById('registerMsg');
    msg.textContent = '';
    msg.style.color = '#b42318';

    const phone = document.getElementById('regPhone').value.trim();
    const code = document.getElementById('regCode').value.trim();
    const nickname = document.getElementById('regNickname').value.trim();
    const ageValue = document.getElementById('regAge').value;
    const age = Number(ageValue);
    const gender = document.getElementById('regGender').value;
    const password = document.getElementById('regPassword').value;

    if (!phone || !code || !nickname || ageValue === '' || !password) {
        msg.textContent = '请填写所有必填项';
        return;
    }
    if (!/^1[3-9]\d{9}$/.test(phone)) {
        msg.textContent = '手机号格式不正确';
        return;
    }
    if (!Number.isInteger(age) || age < 6 || age > 60) {
        msg.textContent = '年龄必须在6到60之间';
        return;
    }

    try {
        const result = await apiPost('/register', {
            phone,
            code,
            nickname,
            age,
            gender: gender || undefined,
            password,
            avatar: avatarUrl || undefined
        });
        if (result.code === 1) {
            msg.style.color = '#06965c';
            msg.textContent = '注册成功，正在跳转...';
            setTimeout(() => { window.location.href = 'index.html'; }, 800);
        } else {
            msg.textContent = result.msg || '注册失败';
        }
    } catch (error) {
        msg.textContent = error.message || '注册失败，请重试';
    }
});
