const $ = (selector) => document.querySelector(selector);
const state = { categories: [], items: [], animations: {}, animationSheets: new Map(), animationTimers: new Map(), changes: [], category: 'all', selected: new Set(), selectionMode: false, authenticated: false, current: null, preview: null };
const itemDialog = $('#item-dialog');
const authDialog = $('#auth-dialog');
const changesDialog = $('#changes-dialog');
const tooltip = $('#tooltip');
const detailImage = $('#detail-image');
const detailAnimation = $('#detail-animation');
const renderedCards = new Map();
let tooltipTimer;
let selectionGesture = null;
let suppressPointerClick = false;
let afterAuthentication = null;

function authorization(password) {
  const bytes = new TextEncoder().encode(password);
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return `Bearer ${btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '')}`;
}

async function api(path, options = {}, password = '') {
  const headers = new Headers(options.headers || {});
  if (password) headers.set('Authorization', authorization(password));
  const response = await fetch(`./api/${path}`, { ...options, headers, cache: 'no-store' });
  if (!response.ok) {
    let message = '请求失败，请稍后重试。';
    try { message = (await response.json()).error || message; } catch { /* Keep the generic message. */ }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}

function filteredItems() {
  const query = $('#search').value.trim().toLocaleLowerCase();
  return state.items.filter((item) =>
    (state.category === 'all' || item.categories.includes(state.category)) &&
    (!query || `${item.name}\n${item.id}\n${item.variant}`.toLocaleLowerCase().includes(query))
  );
}

function renderCategories() {
  const container = $('#categories');
  container.replaceChildren();
  for (const category of [{ id: 'all', name: '全部' }, ...state.categories]) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `category${state.category === category.id ? ' active' : ''}`;
    button.textContent = category.name;
    button.setAttribute('aria-pressed', String(state.category === category.id));
    button.addEventListener('click', () => { state.category = category.id; renderCategories(); renderItems(); });
    container.append(button);
  }
}

function hideTooltip() { clearTimeout(tooltipTimer); tooltip.hidden = true; }
function delayHideTooltip() { clearTimeout(tooltipTimer); tooltipTimer = setTimeout(hideTooltip, 180); }
function showTooltip(item, element) {
  if (selectionGesture) return;
  clearTimeout(tooltipTimer);
  tooltip.textContent = [item.name, item.description, item.animation ? '动态贴图预览' : ''].filter(Boolean).join('\n');
  tooltip.hidden = false;
  tooltip.scrollTop = 0;
  const box = element.getBoundingClientRect();
  const left = Math.max(8, Math.min(box.left, innerWidth - tooltip.offsetWidth - 8));
  const below = box.bottom + 8;
  const top = below + tooltip.offsetHeight <= innerHeight - 8 ? below : Math.max(8, box.top - tooltip.offsetHeight - 8);
  tooltip.style.left = `${left}px`;
  tooltip.style.top = `${top}px`;
}

function setSelected(key, selected) {
  if (state.selected.has(key) === selected) return;
  if (selected) state.selected.add(key); else state.selected.delete(key);
  const view = renderedCards.get(key);
  if (view) {
    view.check.checked = selected;
    view.card.classList.toggle('selected', selected);
    if (state.selectionMode) view.button.setAttribute('aria-pressed', String(selected));
  }
  $('#selected-count').textContent = String(state.selected.size);
  $('#export-selected').disabled = state.selected.size === 0;
}

function stopFrameAnimation(canvas) {
  const timer = state.animationTimers.get(canvas);
  if (timer !== undefined) clearInterval(timer);
  state.animationTimers.delete(canvas);
}

function loadAnimationSheet(animation) {
  let loading = state.animationSheets.get(animation.sheet);
  if (!loading) {
    const image = new Image();
    loading = new Promise((resolve, reject) => {
      image.addEventListener('load', () => resolve(image), { once: true });
      image.addEventListener('error', () => reject(new Error('无法加载贴图动画帧')), { once: true });
      image.src = animation.sheet;
    });
    state.animationSheets.set(animation.sheet, loading);
  }
  return loading;
}

function displayFrames(canvas, animation) {
  stopFrameAnimation(canvas);
  loadAnimationSheet(animation).then((sheet) => {
    if (!canvas.isConnected || canvas.hidden) return;
    const frameHeight = sheet.naturalHeight / animation.sourceFrames;
    if (!Number.isInteger(frameHeight) || frameHeight < 1) return;
    canvas.width = sheet.naturalWidth;
    canvas.height = frameHeight;
    const context = canvas.getContext('2d');
    let frame = 0;
    const draw = () => {
      context.clearRect(0, 0, canvas.width, canvas.height);
      context.drawImage(sheet, 0, frame * frameHeight, canvas.width, frameHeight, 0, 0, canvas.width, canvas.height);
      frame = (frame + 1) % animation.sourceFrames;
    };
    draw();
    state.animationTimers.set(canvas, window.setInterval(draw, animation.frameDurationMs));
  }).catch(() => {});
}

function displayItemImage(item, preview = '') {
  const animated = Boolean(item.animation && !preview);
  detailImage.hidden = animated;
  detailAnimation.hidden = !animated;
  stopFrameAnimation(detailAnimation);
  if (animated) displayFrames(detailAnimation, item.animation);
  else detailImage.src = preview || item.image;
}

function startSelection(event, item, card, check) {
  if (event.pointerType !== 'mouse' || event.button !== 0 || (!state.selectionMode && event.target !== check)) return;
  event.preventDefault();
  hideTooltip();
  selectionGesture = { pointerId: event.pointerId, selected: !state.selected.has(item.key), x: event.clientX, y: event.clientY };
  suppressPointerClick = true;
  $('#items').classList.add('dragging');
  setSelected(item.key, selectionGesture.selected);
  try { card.setPointerCapture(event.pointerId); } catch { /* The pointer may already have been released. */ }
}

function moveSelection(event) {
  if (!selectionGesture || selectionGesture.pointerId !== event.pointerId) return;
  const gesture = selectionGesture;
  const steps = Math.max(1, Math.ceil(Math.max(Math.abs(event.clientX - gesture.x), Math.abs(event.clientY - gesture.y)) / 12));
  for (let step = 1; step <= steps; step++) {
    const x = gesture.x + (event.clientX - gesture.x) * step / steps;
    const y = gesture.y + (event.clientY - gesture.y) * step / steps;
    const card = document.elementFromPoint(x, y)?.closest('.card');
    if (card && renderedCards.has(card.dataset.key)) setSelected(card.dataset.key, gesture.selected);
  }
  gesture.x = event.clientX;
  gesture.y = event.clientY;
}

function finishSelection(event) {
  if (!selectionGesture || (event.pointerId !== undefined && selectionGesture.pointerId !== event.pointerId)) return;
  selectionGesture = null;
  $('#items').classList.remove('dragging');
  setTimeout(() => { suppressPointerClick = false; }, 0);
}

function renderItems() {
  hideTooltip();
  const items = filteredItems();
  const container = $('#items');
  for (const [canvas, timer] of state.animationTimers) {
    if (container.contains(canvas)) {
      clearInterval(timer);
      state.animationTimers.delete(canvas);
    }
  }
  container.replaceChildren();
  container.classList.toggle('selection-mode', state.selectionMode);
  renderedCards.clear();
  $('#category-title').textContent = state.category === 'all' ? '全部物品' : state.categories.find((category) => category.id === state.category)?.name || '物品';
  $('#item-count').textContent = `${items.length} 件物品`;
  $('#empty').hidden = items.length !== 0;
  $('#select-visible').disabled = items.length === 0;
  $('#clear-visible').disabled = items.length === 0;
  $('#selected-count').textContent = String(state.selected.size);
  $('#export-selected').disabled = state.selected.size === 0;
  for (const item of items) {
    const card = document.createElement('article');
    card.className = `card${state.selected.has(item.key) ? ' selected' : ''}`;
    card.dataset.key = item.key;
    const check = document.createElement('input');
    check.type = 'checkbox';
    check.className = 'selection';
    check.checked = state.selected.has(item.key);
    check.setAttribute('aria-label', `选择 ${item.name}`);
    check.addEventListener('change', () => setSelected(item.key, check.checked));
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'card-main';
    button.setAttribute('aria-label', `${item.name}，${state.selectionMode ? '选择物品' : '查看详情'}`);
    if (state.selectionMode) button.setAttribute('aria-pressed', String(state.selected.has(item.key)));
    const image = document.createElement(item.animation ? 'canvas' : 'img');
    image.className = `item-art${item.animation ? ' animated-art' : ''}`;
    if (item.animation) {
      image.setAttribute('role', 'img');
      image.setAttribute('aria-label', `${item.name}动态贴图`);
      displayFrames(image, item.animation);
    } else {
      image.src = item.image;
      image.alt = '';
      image.draggable = false;
      image.loading = 'lazy';
    }
    const name = document.createElement('span');
    name.className = 'item-name';
    name.textContent = item.name;
    button.append(image, name);
    if (item.animation) {
      const badge = document.createElement('span');
      badge.className = 'animation-badge';
      badge.textContent = '动画';
      badge.setAttribute('aria-label', '动态贴图预览');
      button.append(badge);
    }
    button.addEventListener('click', () => {
      if (state.selectionMode) setSelected(item.key, !state.selected.has(item.key));
      else openItem(item);
    });
    button.addEventListener('pointerenter', () => showTooltip(item, button));
    button.addEventListener('pointerleave', delayHideTooltip);
    button.addEventListener('focus', () => showTooltip(item, button));
    button.addEventListener('blur', hideTooltip);
    card.addEventListener('pointerdown', (event) => startSelection(event, item, card, check));
    renderedCards.set(item.key, { card, check, button });
    card.append(check, button);
    container.append(card);
  }
}

function updateEditing() {
  const allowed = state.authenticated;
  for (const selector of ['#detail-name', '#detail-description', '#detail-prompt']) $(selector).readOnly = !allowed;
  $('#image-input').disabled = !allowed;
  $('#save').disabled = !allowed;
  $('#unlock').hidden = allowed;
}

function openItem(item) {
  hideTooltip();
  state.current = item;
  $('#detail-title').textContent = item.name;
  $('#detail-id').textContent = item.variant ? `${item.id} · ${item.variant}` : item.id;
  detailImage.alt = item.name;
  displayItemImage(item);
  $('#detail-name').value = item.name;
  $('#detail-description').value = item.description;
  $('#detail-prompt').value = item.imagePrompt;
  $('#image-input').value = '';
  $('#detail-notice').textContent = '';
  updateEditing();
  itemDialog.showModal();
}

function selectedFields() {
  return { name: $('#export-name').checked, description: $('#export-description').checked, imagePrompt: $('#export-prompt').checked };
}

function exportItems(items) {
  const fields = selectedFields();
  if (!Object.values(fields).some(Boolean)) { $('#notice').textContent = '请至少选择一个导出字段。'; return; }
  if (!items.length) { $('#notice').textContent = '请先勾选物品。'; return; }
  const records = items.map((item) => {
    const record = { id: item.id, variant: item.variant };
    if (fields.name) record.name = item.name;
    if (fields.description) record.description = item.description;
    if (fields.imagePrompt) record.imagePrompt = item.imagePrompt;
    return record;
  });
  downloadJson({ items: records }, 'yumis-items.json');
  $('#notice').textContent = `已导出 ${items.length} 件物品。`;
}

function downloadJson(value, filename) {
  const blob = new Blob([JSON.stringify(value, null, 2) + '\n'], { type: 'application/json;charset=utf-8' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
}

function requestAuthentication(action) {
  afterAuthentication = action;
  $('#password').value = '';
  $('#auth-notice').textContent = '';
  authDialog.showModal();
  $('#password').focus();
}

function changeValue(label, value, image) {
  const container = document.createElement('div');
  container.className = 'change-value';
  const heading = document.createElement('span');
  heading.textContent = label;
  container.append(heading);
  if (image) {
    const link = document.createElement('a');
    link.href = value;
    link.target = '_blank';
    link.rel = 'noopener';
    const picture = document.createElement('img');
    picture.src = value;
    picture.alt = label;
    link.append(picture);
    container.append(link);
  } else {
    const text = document.createElement('pre');
    text.textContent = value || '（空）';
    container.append(text);
  }
  return container;
}

function renderChanges() {
  const list = $('#changes-list');
  list.replaceChildren();
  $('#changes-empty').hidden = state.changes.length !== 0;
  $('#export-changes').disabled = state.changes.length === 0;
  $('#delete-changes').disabled = state.changes.length === 0;
  const labels = { name: '名称', description: '介绍文本', imagePrompt: '生图文本', image: '贴图' };
  for (const record of state.changes) {
    const article = document.createElement('article');
    article.className = 'change-record';
    const heading = document.createElement('div');
    heading.className = 'change-heading';
    const title = document.createElement('h3');
    title.textContent = record.itemName;
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'secondary change-delete';
    remove.textContent = '删除';
    remove.setAttribute('aria-label', `删除 ${record.itemName} 的这条记录`);
    remove.addEventListener('click', () => deleteChangeRecords([record.id]));
    heading.append(title, remove);
    const meta = document.createElement('p');
    meta.className = 'change-meta';
    const time = new Date(record.timestamp).toLocaleString('zh-CN');
    meta.textContent = `${record.itemId}${record.variant ? ` · ${record.variant}` : ''} · ${record.baseline ? '较早改动合并补录' : time}`;
    article.append(heading, meta);
    for (const [field, values] of Object.entries(record.changes)) {
      const section = document.createElement('section');
      section.className = 'change-field';
      const label = document.createElement('h4');
      label.textContent = labels[field] || field;
      const pair = document.createElement('div');
      pair.className = 'change-values';
      pair.append(changeValue('修改前', values.before, field === 'image'), changeValue('修改后', values.after, field === 'image'));
      section.append(label, pair);
      article.append(section);
    }
    list.append(article);
  }
}

async function refreshChanges() {
  const result = await api('changes');
  state.changes = result.changes;
  renderChanges();
}

async function showChanges() {
  if (!state.authenticated) { requestAuthentication(showChanges); return; }
  $('#changes-list').replaceChildren();
  $('#changes-empty').hidden = true;
  $('#changes-notice').textContent = '';
  $('#export-changes').disabled = true;
  $('#delete-changes').disabled = true;
  changesDialog.showModal();
  try { await refreshChanges(); }
  catch (error) {
    if (error.status === 401) {
      state.authenticated = false;
      updateEditing();
      changesDialog.close();
      requestAuthentication(showChanges);
    } else $('#changes-notice').textContent = error.message;
  }
}

async function deleteChangeRecords(ids) {
  if (!ids.length) return;
  $('#changes-notice').textContent = '';
  try {
    for (let offset = 0; offset < ids.length; offset += 10000) {
      await api('changes/delete', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ids: ids.slice(offset, offset + 10000) }) });
    }
    await refreshChanges();
  } catch (error) {
    if (error.status === 401) {
      state.authenticated = false;
      updateEditing();
      changesDialog.close();
      requestAuthentication(showChanges);
    } else $('#changes-notice').textContent = error.message;
  }
}

async function fileBase64(file) {
  const bytes = new Uint8Array(await file.arrayBuffer());
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

$('#search').addEventListener('input', renderItems);
$('#select-visible').addEventListener('click', () => { for (const item of filteredItems()) setSelected(item.key, true); });
$('#clear-visible').addEventListener('click', () => { for (const item of filteredItems()) setSelected(item.key, false); });
$('#selection-mode').addEventListener('click', () => {
  state.selectionMode = !state.selectionMode;
  $('#selection-mode').setAttribute('aria-pressed', String(state.selectionMode));
  $('#selection-mode').textContent = state.selectionMode ? '退出选择模式' : '选择模式';
  renderItems();
});
$('#export-selected').addEventListener('click', () => exportItems(state.items.filter((item) => state.selected.has(item.key))));
$('#export-all').addEventListener('click', () => exportItems(state.items));
$('#open-changes').addEventListener('click', showChanges);
$('#export-changes').addEventListener('click', () => {
  const records = state.changes.map((record) => {
    const changes = { ...record.changes };
    if (changes.image) changes.image = { before: new URL(changes.image.before, location.href).href, after: new URL(changes.image.after, location.href).href };
    return { ...record, changes };
  });
  downloadJson({ changes: records }, 'yumis-item-changes.json');
});
$('#delete-changes').addEventListener('click', () => {
  if (state.changes.length && confirm(`删除当前 ${state.changes.length} 条记录？物品本身不会被修改。`)) deleteChangeRecords(state.changes.map((record) => record.id));
});
$('#unlock').addEventListener('click', () => requestAuthentication(() => $('#detail-name').focus()));
document.querySelectorAll('[data-close]').forEach((button) => button.addEventListener('click', () => document.getElementById(button.dataset.close).close()));
tooltip.addEventListener('pointerenter', () => clearTimeout(tooltipTimer));
tooltip.addEventListener('pointerleave', delayHideTooltip);
addEventListener('scroll', (event) => { if (event.target !== tooltip) hideTooltip(); }, true);
addEventListener('resize', hideTooltip);
addEventListener('pointermove', moveSelection);
addEventListener('pointerup', finishSelection);
addEventListener('pointercancel', finishSelection);
addEventListener('blur', finishSelection);
document.addEventListener('click', (event) => {
  if (!suppressPointerClick) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  suppressPointerClick = false;
}, true);

$('#auth-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const password = $('#password').value;
  try {
    await api('verify', { method: 'POST' }, password);
    state.authenticated = true;
    const action = afterAuthentication;
    afterAuthentication = null;
    $('#password').value = '';
    authDialog.close();
    updateEditing();
    if (action) action();
  } catch (error) { $('#auth-notice').textContent = error.message; }
});
authDialog.addEventListener('close', () => { afterAuthentication = null; });

$('#image-input').addEventListener('change', () => {
  const file = $('#image-input').files[0];
  if (state.preview) URL.revokeObjectURL(state.preview);
  state.preview = null;
  if (file) { state.preview = URL.createObjectURL(file); if (state.current) displayItemImage(state.current, state.preview); }
  else if (state.current) displayItemImage(state.current);
});
itemDialog.addEventListener('close', () => {
  stopFrameAnimation(detailAnimation);
  if (state.preview) URL.revokeObjectURL(state.preview);
  state.preview = null;
  state.current = null;
});
itemDialog.addEventListener('click', (event) => { if (event.target === itemDialog) itemDialog.close(); });
changesDialog.addEventListener('click', (event) => { if (event.target === changesDialog) changesDialog.close(); });

$('#item-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!state.current || !state.authenticated) return;
  const current = state.current;
  const file = $('#image-input').files[0];
  if (file && (file.size > 8 * 1024 * 1024 || (file.type !== 'image/png' && !file.name.toLowerCase().endsWith('.png')))) { $('#detail-notice').textContent = '请选择不超过 8 MB 的 PNG 图片。'; return; }
  const body = { name: $('#detail-name').value, description: $('#detail-description').value, imagePrompt: $('#detail-prompt').value };
  $('#save').disabled = true;
  $('#detail-notice').textContent = '';
  try {
    if (file) body.imageBase64 = await fileBase64(file);
    const updated = await api(`items/${encodeURIComponent(current.key)}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    Object.assign(current, updated);
    renderItems();
    if (state.current === current && itemDialog.open) itemDialog.close();
    $('#notice').textContent = '修改已保存。';
  } catch (error) {
    if (error.status === 401) { state.authenticated = false; updateEditing(); }
    $('#detail-notice').textContent = error.message;
  } finally { if (state.current === current) $('#save').disabled = !state.authenticated; }
});

async function start() {
  try {
    const [catalog, session, animations] = await Promise.all([api('catalog'), api('session'), api('animations')]);
    state.authenticated = session.authenticated;
    state.categories = catalog.categories;
    state.animations = animations.items || {};
    state.items = catalog.items.map((item) => ({ ...item, animation: state.animations[item.key] || null }));
    renderCategories();
    renderItems();
  } catch (error) { $('#notice').textContent = `无法加载物品清单：${error.message}`; }
}
start();
