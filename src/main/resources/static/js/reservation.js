let selectedDateOffset = 0;
let selectedStartTime = null;
let selectedEndTime = null;
let selectedCourt = null;

const END_HOUR = 20; // 每日最后可预约时段
const dateGroup = document.getElementById('dateGroup');
const startTime = document.getElementById('startTime');
const endTime = document.getElementById('endTime');
const container = document.getElementById('courtContainer');
const modal = document.getElementById('confirmModal');
const defaultAvatar = 'https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png';

function isTodayLocked() {
    const now = new Date();
    return now.getHours() >= END_HOUR;
}

function formatDate(offset) {
    const d = new Date();
    d.setDate(d.getDate() + offset);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function formatDateDisplay(offset) {
    const d = new Date();
    d.setDate(d.getDate() + offset);
    return `${d.getMonth() + 1}月${d.getDate()}日`;
}

document.getElementById('today').textContent = formatDateDisplay(0);
document.getElementById('tomorrow').textContent = formatDateDisplay(1);
document.getElementById('afterTomorrow').textContent = formatDateDisplay(2);

// 当天已过最后预约时段则锁定"今天"
function updateTodayLock() {
    const todayBtn = dateGroup.querySelector('[data-offset="0"]');
    if (isTodayLocked()) {
        todayBtn.classList.add('locked');
        todayBtn.title = '今日预约时间已过';
    } else {
        todayBtn.classList.remove('locked');
        todayBtn.title = '';
    }
}
updateTodayLock();

dateGroup.querySelectorAll('.segment').forEach(button => {
    button.addEventListener('click', () => {
        if (button.classList.contains('locked')) return;
        dateGroup.querySelectorAll('.segment').forEach(item => item.classList.remove('selected'));
        button.classList.add('selected');
        selectedDateOffset = Number(button.dataset.offset);
        filterStartTimeOptions();
        // If selected option got hidden, pick the first visible one
        if (startTime.selectedOptions[0] && startTime.selectedOptions[0].hidden) {
            const first = startTime.querySelector('option:not([hidden])');
            if (first) startTime.value = first.value;
        }
        syncEndTime();
    });
});

startTime.addEventListener('change', syncEndTime);
document.getElementById('queryBtn').addEventListener('click', loadCourts);
document.getElementById('resetBtn').addEventListener('click', resetForm);
document.getElementById('modalClose').addEventListener('click', closeModal);
document.getElementById('cancelReservation').addEventListener('click', closeModal);
document.getElementById('confirmReservation').addEventListener('click', confirmReservation);
modal.addEventListener('click', event => {
    if (event.target === modal) closeModal();
});

initDefaultQuery();

function filterStartTimeOptions() {
    const now = new Date();
    const currentHour = now.getHours();
    const options = startTime.querySelectorAll('option');
    options.forEach(opt => {
        if (!opt.value) {
            opt.hidden = true; // hide placeholder
            return;
        }
        const h = Number(opt.value);
        if (selectedDateOffset === 0 && h <= currentHour) {
            opt.hidden = true;
        } else {
            opt.hidden = false;
            opt.textContent = `${String(h).padStart(2, '0')}:00`;
        }
    });
}

function initDefaultQuery() {
    const now = new Date();
    let hour = now.getHours() + 1; // next available hour
    if (isTodayLocked() || hour < 8 || hour > 20) {
        // No available slots today, switch to tomorrow
        selectedDateOffset = 1;
        hour = 8;
        dateGroup.querySelectorAll('.segment').forEach(item => item.classList.remove('selected'));
        const tomorrowBtn = dateGroup.querySelector('[data-offset="1"]');
        if (tomorrowBtn) tomorrowBtn.classList.add('selected');
    }
    filterStartTimeOptions();
    startTime.value = String(hour);
    syncEndTime();
    loadCourts();
}

function syncEndTime() {
    const hour = Number(startTime.value);
    endTime.innerHTML = '<option value="">结束时间</option>';
    selectedStartTime = hour || null;
    selectedEndTime = null;

    if (hour >= 8 && hour <= 20) {
        const option = document.createElement('option');
        option.value = String(hour + 1);
        option.textContent = `${String(hour + 1).padStart(2, '0')}:00`;
        endTime.appendChild(option);
        endTime.value = option.value;
        selectedEndTime = hour + 1;
    }
    endTime.disabled = true;
}

function resetForm() {
    dateGroup.querySelectorAll('.segment').forEach(item => item.classList.remove('selected'));
    const todayBtn = dateGroup.querySelector('[data-offset="0"]');
    if (isTodayLocked()) {
        const tomorrowBtn = dateGroup.querySelector('[data-offset="1"]');
        tomorrowBtn.classList.add('selected');
        selectedDateOffset = 1;
    } else {
        todayBtn.classList.add('selected');
        selectedDateOffset = 0;
    }
    selectedCourt = null;
    initDefaultQuery();
}

async function loadCourts() {
    selectedStartTime = Number(startTime.value);
    selectedEndTime = Number(endTime.value);
    if (!selectedStartTime || !selectedEndTime) {
        alert('请选择开始时间和结束时间');
        return;
    }

    container.className = 'empty';
    container.textContent = '加载中...';
    try {
        const result = await apiGet(`/reserve?date=${formatDate(selectedDateOffset)}&startTime=${selectedStartTime}`);
        if (result.code === 1) {
            renderCourts(result.data || []);
        } else {
            container.textContent = result.msg || '加载失败';
        }
    } catch (error) {
        container.textContent = error.message || '网络错误，请稍后重试';
    }
}

function renderCourts(courts) {
    if (!courts.length) {
        container.className = 'empty';
        container.textContent = '暂无场地数据';
        return;
    }
    container.className = 'court-grid';
    container.innerHTML = '';
    courts.forEach(court => container.appendChild(createCourtCard(court)));
}

function createCourtCard(court) {
    const card = document.createElement('div');
    card.className = 'court-card';
    if (court.status !== 0) {
        card.classList.add('disabled');
    } else {
        card.addEventListener('click', () => openModal(court));
    }

    // Build avatars from players array
    let avatarsHtml = '';
    const players = court.players || [];
    for (let i = 0; i < 4; i++) {
        const player = players[i] || { avatar: defaultAvatar, gender: 0 };
        const imgSrc = player.avatar || defaultAvatar;
        avatarsHtml += `<img class="player-avatar" src="${imgSrc}" style="border: 2px solid ${borderColor(player.gender)}" alt="预约人">`;
    }

    card.innerHTML = `
        <div class="court-title">${escapeHtml(court.name)}${court.remark ? ' <small>(' + escapeHtml(court.remark) + ')</small>' : ''}</div>
        <div class="avatar-grid">${avatarsHtml}</div>
        <div class="card-footer">
            <span>场地状态</span>
            <span class="tag ${statusClass(court.status)}">${court.statusDisplay || statusText(court.status)}</span>
        </div>
    `;
    return card;
}

function openModal(court) {
    selectedCourt = court;
    document.getElementById('modalCourtName').textContent = court.name;
    document.getElementById('modalDate').textContent = formatDateDisplay(selectedDateOffset);
    document.getElementById('modalTime').textContent =
        `${String(selectedStartTime).padStart(2, '0')}:00 - ${String(selectedEndTime).padStart(2, '0')}:00`;

    // Render booked players (only those with nickname)
    const playersDiv = document.getElementById('modalPlayers');
    const bookedPlayers = (court.players || []).filter(p => p.nickname);
    if (bookedPlayers.length) {
        playersDiv.innerHTML = bookedPlayers.map(p => `
            <div style="display:flex;align-items:center;gap:10px;margin-bottom:6px;">
                <img src="${escapeHtml(p.avatar || defaultAvatar)}" style="width:32px;height:32px;border-radius:50%;object-fit:cover;border:2px solid ${borderColor(p.gender)};">
                <span>${escapeHtml(p.nickname)}</span>
                <span style="color:#697586;font-size:12px;">${p.gender === 1 ? '男' : p.gender === 2 ? '女' : ''}</span>
            </div>
        `).join('');
    } else {
        playersDiv.innerHTML = '<span style="color:#697586;">暂无</span>';
    }

    modal.classList.add('show');
}

function closeModal() {
    selectedCourt = null;
    modal.classList.remove('show');
}

async function confirmReservation() {
    if (!selectedCourt) return;
    try {
        const result = await apiPost('/reserve', {
            courtId: selectedCourt.courtId,
            date: formatDate(selectedDateOffset),
            startTime: selectedStartTime,
            endTime: selectedEndTime
        });
        if (result.code === 1) {
            let msg = `预约成功！`;
            if (result.data.verificationCode) msg += `\n验证码：${result.data.verificationCode}`;
            alert(msg);
            closeModal();
            loadCourts();
        } else {
            alert(result.msg || '预约失败');
        }
    } catch (error) {
        alert(error.message || '网络错误，请稍后重试');
    }
}

function statusText(status) {
    if (status === 0) return '可预约';
    if (status === 1) return '已锁定';
    if (status === 2) return '维护中';
    if (status === 3) return '已满';
    return '未知';
}

function statusClass(status) {
    if (status === 0) return 'success';
    if (status === 1) return 'locked';
    if (status === 2) return 'warning';
    if (status === 3) return 'full';
    return '';
}

function borderColor(gender) {
    if (gender === 1) return '#409eff';
    if (gender === 2) return '#e95a9a';
    return '#b1b3b8';
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
