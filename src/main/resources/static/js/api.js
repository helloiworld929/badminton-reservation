const API_BASE = '';

async function apiGet(url) {
    const response = await fetch(API_BASE + url, { credentials: 'include' });
    return readJson(response);
}

async function apiPost(url, data) {
    const response = await fetch(API_BASE + url, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json;charset=UTF-8' },
        body: JSON.stringify(data)
    });
    return readJson(response);
}

async function apiPut(url, data) {
    const response = await fetch(API_BASE + url, {
        method: 'PUT',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json;charset=UTF-8' },
        body: JSON.stringify(data)
    });
    return readJson(response);
}

async function apiDelete(url, data) {
    const options = {
        method: 'DELETE',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json;charset=UTF-8' }
    };
    if (data) options.body = JSON.stringify(data);
    const response = await fetch(API_BASE + url, options);
    return readJson(response);
}

async function readJson(response) {
    let result;
    try {
        result = await response.json();
    } catch (error) {
        throw new Error(`后端没有返回 JSON，状态码：${response.status}`);
    }

    if (response.status === 401 || (result && result.code === -1)) {
        window.top.location.href = 'login.html';
        throw new Error(result.msg || '请先登录');
    }
    if (!response.ok && result && result.msg) {
        throw new Error(result.msg);
    }
    return result;
}
