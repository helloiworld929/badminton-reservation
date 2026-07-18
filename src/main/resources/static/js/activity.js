let currentUser = null;
const list = document.getElementById('activityList');
const modal = document.getElementById('signupModal');
const form = document.getElementById('signupForm');
let selectedActivity = null;

document.getElementById('closeSignup').addEventListener('click', closeSignup);
document.getElementById('cancelSignup').addEventListener('click', closeSignup);
modal.addEventListener('click', event => {
    if (event.target === modal) closeSignup();
});
form.addEventListener('submit', submitSignup);

const defaultImage = 'https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png';

loadCurrentUser();
loadActivities();

async function loadCurrentUser() {
    try {
        const result = await apiGet('/user');
        if (result.code === 1) {
            currentUser = result.data;
        }
    } catch (e) {
        // 获取失败不影响页面使用
    }
}

async function loadActivities() {
    try {
        const result = await apiGet('/activities');
        if (result.code === 1) {
            renderActivities(result.data || []);
        } else {
            list.textContent = result.msg || '加载失败';
        }
    } catch (error) {
        list.textContent = error.message || '网络错误，请稍后重试';
    }
}

function renderActivities(activities) {
    if (!activities.length) {
        list.className = 'empty';
        list.textContent = '暂无活动';
        return;
    }
    list.className = 'activity-list';
    list.innerHTML = '';
    activities.forEach(activity => {
        const card = document.createElement('article');
        card.className = 'activity-card';
        card.innerHTML = `
            <img src="${escapeHtml(activity.imageUrl || '')}" alt="${escapeHtml(activity.title)}" onerror="this.src='${defaultImage}'">
            <div class="activity-body">
                <h3>${escapeHtml(activity.title)}</h3>
                <p>时间：${escapeHtml(activity.activityTime)}</p>
                <p>地点：${escapeHtml(activity.location)}</p>
                <p>内容：${escapeHtml(activity.description)}</p>
                <button class="btn" type="button">我要报名</button>
            </div>
        `;
        card.querySelector('button').addEventListener('click', () => openSignup(activity));
        list.appendChild(card);
    });
}

function openSignup(activity) {
    selectedActivity = activity;
    document.getElementById('currentActivity').textContent = activity.title;
    document.getElementById('signupName').value = currentUser ? (currentUser.nickname || '') : '';
    document.getElementById('signupPhone').value = currentUser ? (currentUser.phone || '') : '';
    modal.classList.add('show');
}

function closeSignup() {
    selectedActivity = null;
    form.reset();
    document.getElementById('participantCount').value = 1;
    modal.classList.remove('show');
}

async function submitSignup(event) {
    event.preventDefault();
    if (!selectedActivity) return;
    try {
        const result = await apiPost('/activity-signups', {
            activityId: selectedActivity.id,
            name: document.getElementById('signupName').value.trim(),
            phone: document.getElementById('signupPhone').value.trim(),
            participantCount: Number(document.getElementById('participantCount').value)
        });
        if (result.code === 1) {
            alert('报名成功');
            closeSignup();
        } else {
            alert(result.msg || '报名失败');
        }
    } catch (error) {
        alert(error.message || '网络错误，请稍后重试');
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
