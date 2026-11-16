import { createServer } from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { createHash, randomBytes, randomUUID, scryptSync, timingSafeEqual } from 'node:crypto';
import { createReadStream, existsSync, mkdirSync, readFileSync, renameSync, statSync, unlinkSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const dataDir = process.env.CATALOG_DATA;
if (!dataDir) throw new Error('必须设置 CATALOG_DATA 指向专用数据目录');
const seedDir = process.env.CATALOG_SEED || join(here, 'seed');
const credentialFile = join(dataDir, 'editor.json');
const uploadsDir = join(dataDir, 'uploads');
const MAX_IMAGE = 8 * 1024 * 1024;
const MAX_BODY = Math.ceil(MAX_IMAGE * 4 / 3) + 256 * 1024;
const SESSION_SECONDS = 365 * 24 * 60 * 60;
const SESSION_COOKIE = 'yumis_item_editor';
const KEY = /^[a-f0-9]{64}$/;
const IMAGE = /^[a-f0-9-]{36}\.png$/;
const CRC_TABLE = new Uint32Array(256);
for (let i = 0; i < CRC_TABLE.length; i++) {
  let value = i;
  for (let bit = 0; bit < 8; bit++) value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
  CRC_TABLE[i] = value >>> 0;
}

function setPassword() {
  mkdirSync(dataDir, { recursive: true, mode: 0o700 });
  const password = readFileSync(0, 'utf8').replace(/\r?\n$/, '');
  if (!password || Buffer.byteLength(password) > 1024) throw new Error('编辑口令为空或过长');
  const salt = randomBytes(32);
  const hash = scryptSync(password, salt, 64);
  writeFileSync(credentialFile, JSON.stringify({ salt: salt.toString('hex'), hash: hash.toString('hex') }), { mode: 0o600, flag: 'wx' });
}

if (process.argv[2] === '--set-password') {
  setPassword();
} else {
  startServer();
}

function startServer() {
  mkdirSync(dataDir, { recursive: true, mode: 0o700 });
  mkdirSync(uploadsDir, { recursive: true, mode: 0o700 });
  const credential = JSON.parse(readFileSync(credentialFile, 'utf8'));
  const salt = Buffer.from(credential.salt || '', 'hex');
  const expected = Buffer.from(credential.hash || '', 'hex');
  if (salt.length !== 32 || expected.length !== 64) throw new Error('编辑口令配置无效');
  const seedBytes = readFileSync(join(seedDir, 'catalog.json'));
  const seedHash = createHash('sha256').update(seedBytes).digest('hex');
  const seed = JSON.parse(seedBytes);
  if (seed.schema !== 1 || !Array.isArray(seed.items) || !seed.items.length || !Array.isArray(seed.categories) || seed.errors?.length) throw new Error('物品清单不完整');
  const db = new DatabaseSync(join(dataDir, 'catalog.sqlite'));
  db.exec(`PRAGMA journal_mode=WAL;
    CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS items (
      key TEXT PRIMARY KEY, item_id TEXT NOT NULL, variant TEXT NOT NULL,
      categories TEXT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL,
      image_prompt TEXT NOT NULL DEFAULT '', image_override TEXT, position INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS editor_sessions (
      token_hash TEXT PRIMARY KEY, expires_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS editor_changes (
      id INTEGER PRIMARY KEY AUTOINCREMENT, created_at TEXT NOT NULL, item_key TEXT NOT NULL,
      item_id TEXT NOT NULL, variant TEXT NOT NULL, item_name TEXT NOT NULL,
      changes_json TEXT NOT NULL, before_image TEXT, after_image TEXT, baseline INTEGER NOT NULL DEFAULT 0
    );`);
  const currentHash = db.prepare("SELECT value FROM metadata WHERE key='seed_hash'").get();
  if (!currentHash) {
    const insert = db.prepare('INSERT INTO items (key,item_id,variant,categories,name,description,image_prompt,position) VALUES (?,?,?,?,?,?,?,?)');
    db.exec('BEGIN IMMEDIATE');
    try {
      seed.items.forEach((item, position) => {
        if (!KEY.test(item.key) || typeof item.id !== 'string' || typeof item.variant !== 'string' || typeof item.name !== 'string' || !item.name || typeof item.description !== 'string' || !Array.isArray(item.categories) || !existsSync(join(seedDir, 'icons', `${item.key}.png`))) throw new Error(`物品数据不完整：${item.id || position}`);
        insert.run(item.key, item.id, item.variant, JSON.stringify(item.categories), item.name, item.description, '', position);
      });
      db.prepare('INSERT INTO metadata (key,value) VALUES (?,?)').run('seed_hash', seedHash);
      db.prepare('INSERT INTO metadata (key,value) VALUES (?,?)').run('categories', JSON.stringify(seed.categories));
      db.exec('COMMIT');
    } catch (error) { db.exec('ROLLBACK'); throw error; }
  } else if (currentHash.value !== seedHash) {
    const existing = db.prepare('SELECT key,item_id,variant,categories FROM items ORDER BY position').all();
    const seedByKey = new Map(seed.items.map((item) => [item.key, item]));
    const existingKeys = new Set(existing.map((row) => row.key));
    if (seed.items.length < existing.length || existing.some((row, index) => {
      const item = seedByKey.get(row.key);
      return seed.items[index]?.key !== row.key || !item || item.id !== row.item_id || item.variant !== row.variant || JSON.stringify(item.categories) !== row.categories;
    })) throw new Error('已保存的网页数据与物品快照不一致，未覆盖现有改稿');
    const insert = db.prepare('INSERT INTO items (key,item_id,variant,categories,name,description,image_prompt,position) VALUES (?,?,?,?,?,?,?,?)');
    db.exec('BEGIN IMMEDIATE');
    try {
      seed.items.forEach((item, position) => {
        if (existingKeys.has(item.key)) return;
        if (!KEY.test(item.key) || typeof item.id !== 'string' || typeof item.variant !== 'string' || typeof item.name !== 'string' || !item.name || typeof item.description !== 'string' || !Array.isArray(item.categories) || !existsSync(join(seedDir, 'icons', `${item.key}.png`))) throw new Error(`新增物品数据不完整：${item.id || position}`);
        insert.run(item.key, item.id, item.variant, JSON.stringify(item.categories), item.name, item.description, '', position);
      });
      db.prepare("UPDATE metadata SET value=? WHERE key='seed_hash'").run(seedHash);
      db.prepare("UPDATE metadata SET value=? WHERE key='categories'").run(JSON.stringify(seed.categories));
      db.exec('COMMIT');
    } catch (error) { db.exec('ROLLBACK'); throw error; }
  }
  const categories = JSON.parse(db.prepare("SELECT value FROM metadata WHERE key='categories'").get().value);
  const all = db.prepare('SELECT * FROM items ORDER BY position');
  const byKey = db.prepare('SELECT * FROM items WHERE key=?');
  const update = db.prepare('UPDATE items SET name=?,description=?,image_prompt=?,image_override=? WHERE key=?');
  const sessionByHash = db.prepare('SELECT expires_at FROM editor_sessions WHERE token_hash=?');
  const addSession = db.prepare('INSERT INTO editor_sessions (token_hash,expires_at) VALUES (?,?)');
  const deleteExpiredSessions = db.prepare('DELETE FROM editor_sessions WHERE expires_at<=?');
  const addChange = db.prepare('INSERT INTO editor_changes (created_at,item_key,item_id,variant,item_name,changes_json,before_image,after_image,baseline) VALUES (?,?,?,?,?,?,?,?,?)');
  const changes = db.prepare('SELECT * FROM editor_changes ORDER BY id DESC');
  const changeById = db.prepare('SELECT * FROM editor_changes WHERE id=?');
  const deleteChange = db.prepare('DELETE FROM editor_changes WHERE id=?');
  const imageInItems = db.prepare('SELECT 1 FROM items WHERE image_override=? LIMIT 1');
  const imageInChanges = db.prepare('SELECT 1 FROM editor_changes WHERE before_image=? OR after_image=? LIMIT 1');
  const failures = new Map();

  function imageUrl(key, override) {
    return override ? `./image/upload/${override}` : `./image/original/${key}.png`;
  }

  function itemValues(row) {
    return { name: row.name, description: row.description, imagePrompt: row.image_prompt, imageOverride: row.image_override };
  }

  function itemDiff(key, before, after) {
    const diff = {};
    for (const field of ['name', 'description', 'imagePrompt']) {
      if (before[field] !== after[field]) diff[field] = { before: before[field], after: after[field] };
    }
    if (before.imageOverride !== after.imageOverride) {
      diff.image = { before: imageUrl(key, before.imageOverride), after: imageUrl(key, after.imageOverride) };
    }
    return diff;
  }

  function saveChange(row, before, after, baseline = false) {
    const diff = itemDiff(row.key, before, after);
    if (!Object.keys(diff).length) return;
    addChange.run(new Date().toISOString(), row.key, row.item_id, row.variant, after.name, JSON.stringify(diff),
      diff.image ? before.imageOverride : null, diff.image ? after.imageOverride : null, baseline ? 1 : 0);
  }

  const historyInitialized = db.prepare("SELECT value FROM metadata WHERE key='change_history_initialized'").get();
  if (!historyInitialized) {
    const originals = new Map(seed.items.map((item) => [item.key, item]));
    db.exec('BEGIN IMMEDIATE');
    try {
      for (const row of all.all()) {
        const original = originals.get(row.key);
        if (!original) throw new Error(`物品快照缺少条目：${row.item_id}`);
        saveChange(row, { name: original.name, description: original.description, imagePrompt: '', imageOverride: null }, itemValues(row), true);
      }
      db.prepare('INSERT INTO metadata (key,value) VALUES (?,?)').run('change_history_initialized', '1');
      db.exec('COMMIT');
    } catch (error) { db.exec('ROLLBACK'); throw error; }
  }

  function publicChange(row) {
    return {
      id: row.id, timestamp: row.created_at, itemKey: row.item_key, itemId: row.item_id,
      variant: row.variant, itemName: row.item_name, baseline: Boolean(row.baseline), changes: JSON.parse(row.changes_json),
    };
  }

  function cleanupImages(candidates) {
    for (const name of candidates) {
      if (!name || !IMAGE.test(name) || imageInItems.get(name) || imageInChanges.get(name, name)) continue;
      try { unlinkSync(join(uploadsDir, name)); }
      catch (error) { if (error.code !== 'ENOENT') console.error('无法清理已删除记录的贴图：', error.message); }
    }
  }

  function publicItem(row) {
    return {
      key: row.key, id: row.item_id, variant: row.variant, categories: JSON.parse(row.categories),
      name: row.name, description: row.description, imagePrompt: row.image_prompt,
      image: imageUrl(row.key, row.image_override),
    };
  }

  function authenticatedPassword(req) {
    const ip = String(req.headers['x-real-ip'] || req.socket.remoteAddress || '').slice(0, 128);
    const now = Date.now();
    if (failures.size > 10000) for (const [key, value] of failures) if (value.until <= now) failures.delete(key);
    const prior = failures.get(ip);
    if (prior && prior.until > now && prior.count >= 5) return 429;
    const match = /^Bearer ([A-Za-z0-9_-]{1,1400})$/.exec(req.headers.authorization || '');
    let good = false;
    if (match) {
      const raw = Buffer.from(match[1], 'base64url');
      if (raw.length <= 1024) good = timingSafeEqual(scryptSync(raw, salt, 64), expected);
    }
    if (good) { failures.delete(ip); return 0; }
    const record = prior && prior.until > now ? prior : { count: 0, until: now + 15 * 60 * 1000 };
    record.count++;
    failures.set(ip, record);
    return 401;
  }

  function authenticatedSession(req) {
    const cookies = String(req.headers.cookie || '').split(';');
    for (const cookie of cookies) {
      const separator = cookie.indexOf('=');
      if (separator < 0 || cookie.slice(0, separator).trim() !== SESSION_COOKIE) continue;
      const token = cookie.slice(separator + 1).trim();
      if (!/^[A-Za-z0-9_-]{43}$/.test(token)) return false;
      const hash = createHash('sha256').update(token).digest('hex');
      const session = sessionByHash.get(hash);
      return Boolean(session && session.expires_at > Math.floor(Date.now() / 1000));
    }
    return false;
  }

  function issueSession(res) {
    const now = Math.floor(Date.now() / 1000);
    const token = randomBytes(32).toString('base64url');
    deleteExpiredSessions.run(now);
    addSession.run(createHash('sha256').update(token).digest('hex'), now + SESSION_SECONDS);
    res.writeHead(204, {
      'Cache-Control': 'no-store',
      'Set-Cookie': `${SESSION_COOKIE}=${token}; Path=/yumis-items/; Max-Age=${SESSION_SECONDS}; HttpOnly; SameSite=Strict`,
    });
    res.end();
  }

  async function readBody(req) {
    let size = 0;
    const chunks = [];
    for await (const chunk of req) {
      size += chunk.length;
      if (size > MAX_BODY) throw new HttpError(413, '上传内容过大');
      chunks.push(chunk);
    }
    return Buffer.concat(chunks).toString('utf8');
  }

  function imageBytes(value) {
    if (typeof value !== 'string' || value.length > Math.ceil(MAX_IMAGE * 4 / 3) + 4 || !/^[A-Za-z0-9+/]*={0,2}$/.test(value)) throw new HttpError(400, 'PNG 图片数据无效');
    const bytes = Buffer.from(value, 'base64');
    const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
    if (bytes.length < 45 || bytes.length > MAX_IMAGE || !bytes.subarray(0, 8).equals(signature) || bytes.readUInt32BE(8) !== 13 || bytes.toString('ascii', 12, 16) !== 'IHDR') throw new HttpError(400, '请选择有效的 PNG 图片');
    const width = bytes.readUInt32BE(16), height = bytes.readUInt32BE(20);
    if (!width || !height || width > 4096 || height > 4096) throw new HttpError(400, '图片尺寸必须在 4096 × 4096 以内');
    let offset = 8, hasData = false, complete = false;
    while (offset + 12 <= bytes.length) {
      const length = bytes.readUInt32BE(offset);
      if (length > bytes.length - offset - 12) throw new HttpError(400, 'PNG 图片数据不完整');
      const end = offset + 8 + length;
      const kind = bytes.toString('ascii', offset + 4, offset + 8);
      let crc = 0xffffffff;
      for (let index = offset + 4; index < end; index++) crc = CRC_TABLE[(crc ^ bytes[index]) & 255] ^ (crc >>> 8);
      if (((crc ^ 0xffffffff) >>> 0) !== bytes.readUInt32BE(end)) throw new HttpError(400, 'PNG 图片校验失败');
      if (kind === 'IHDR' && offset !== 8) throw new HttpError(400, 'PNG 图片格式无效');
      if (kind === 'IDAT') hasData = true;
      offset = end + 4;
      if (kind === 'IEND') { complete = length === 0 && offset === bytes.length; break; }
    }
    if (!hasData || !complete) throw new HttpError(400, 'PNG 图片数据不完整');
    return bytes;
  }

  function respond(res, status, payload) {
    const bytes = Buffer.from(JSON.stringify(payload));
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': bytes.length, 'Cache-Control': 'no-store' });
    res.end(bytes);
  }

  function file(res, path, type, cache = 'no-cache') {
    if (!existsSync(path) || !statSync(path).isFile()) throw new HttpError(404, '未找到文件');
    res.writeHead(200, { 'Content-Type': type, 'Content-Length': statSync(path).size, 'Cache-Control': cache, 'X-Content-Type-Options': 'nosniff' });
    createReadStream(path).on('error', () => res.destroy()).pipe(res);
  }

  const server = createServer(async (req, res) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('Content-Security-Policy', "default-src 'self'; img-src 'self' blob:; style-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'");
    try {
      const path = new URL(req.url, 'http://localhost').pathname;
      if (!path.startsWith('/yumis-items/')) throw new HttpError(404, '未找到页面');
      const route = path.slice('/yumis-items/'.length);
      if (req.method === 'GET' && route === 'api/catalog') return respond(res, 200, { categories, items: all.all().map(publicItem) });
      if (req.method === 'GET' && route === 'api/animations') return file(res, join(here, 'public', 'animations', 'manifest.json'), 'application/json; charset=utf-8');
      if (req.method === 'GET' && route === 'api/session') return respond(res, 200, { authenticated: authenticatedSession(req) });
      if (req.method === 'GET' && (route === '' || route === 'index.html')) return file(res, join(here, 'public', 'index.html'), 'text/html; charset=utf-8');
      if (req.method === 'GET' && route === 'style.css') return file(res, join(here, 'public', 'style.css'), 'text/css; charset=utf-8');
      if (req.method === 'GET' && route === 'app.js') return file(res, join(here, 'public', 'app.js'), 'text/javascript; charset=utf-8');
      if (req.method === 'GET' && route === 'agent-api.md') return file(res, join(here, 'AGENT_API.md'), 'text/markdown; charset=utf-8');
      const animation = /^animations\/([a-f0-9]{64})(-sheet)?\.png$/.exec(route);
      if (req.method === 'GET' && animation) return file(res, join(here, 'public', 'animations', `${animation[1]}${animation[2] || ''}.png`), 'image/png', 'public, max-age=31536000, immutable');
      const original = /^image\/original\/([a-f0-9]{64})\.png$/.exec(route);
      if (req.method === 'GET' && original && byKey.get(original[1])) return file(res, join(seedDir, 'icons', `${original[1]}.png`), 'image/png', 'public, max-age=86400');
      const upload = /^image\/upload\/([a-f0-9-]{36}\.png)$/.exec(route);
      if (req.method === 'GET' && upload && IMAGE.test(upload[1])) return file(res, join(uploadsDir, upload[1]), 'image/png', 'public, max-age=86400, immutable');
      const itemRoute = /^api\/items\/([a-f0-9]{64})$/.exec(route);
      const historyRead = req.method === 'GET' && route === 'api/changes';
      const historyDelete = req.method === 'POST' && route === 'api/changes/delete';
      if ((req.method === 'POST' && route === 'api/verify') || (req.method === 'PATCH' && itemRoute) || historyRead || historyDelete) {
        const result = route !== 'api/verify' && authenticatedSession(req) ? 0
          : route !== 'api/verify' && !req.headers.authorization ? 401 : authenticatedPassword(req);
        if (result === 429) throw new HttpError(429, '尝试次数过多，请 15 分钟后再试');
        if (result) throw new HttpError(401, '编辑口令不正确');
        if (route === 'api/verify') return issueSession(res);
        if (historyRead) return respond(res, 200, { changes: changes.all().map(publicChange) });
        if (historyDelete) {
          if (!/^application\/json(?:\s*;|$)/i.test(req.headers['content-type'] || '')) throw new HttpError(415, '请求格式无效');
          let request;
          try { request = JSON.parse(await readBody(req)); } catch (error) { if (error instanceof HttpError) throw error; throw new HttpError(400, '请求内容无效'); }
          if (!request || !Array.isArray(request.ids) || request.ids.length > 10000 || !request.ids.every((id) => Number.isSafeInteger(id) && id > 0)) throw new HttpError(400, '修改记录无效');
          const candidates = new Set();
          let deleted = 0;
          db.exec('BEGIN IMMEDIATE');
          try {
            for (const id of new Set(request.ids)) {
              const record = changeById.get(id);
              if (!record) continue;
              if (record.before_image) candidates.add(record.before_image);
              if (record.after_image) candidates.add(record.after_image);
              deleteChange.run(id);
              deleted++;
            }
            db.exec('COMMIT');
          } catch (error) { db.exec('ROLLBACK'); throw error; }
          cleanupImages(candidates);
          return respond(res, 200, { deleted });
        }
        let body;
        try { body = JSON.parse(await readBody(req)); } catch (error) { if (error instanceof HttpError) throw error; throw new HttpError(400, '请求内容无效'); }
        if (!body || typeof body !== 'object' || typeof body.name !== 'string' || !body.name.trim() || body.name.length > 500 || typeof body.description !== 'string' || body.description.length > 30000 || typeof body.imagePrompt !== 'string' || body.imagePrompt.length > 30000) throw new HttpError(400, '名称或文本内容无效');
        const row = byKey.get(itemRoute[1]);
        if (!row) throw new HttpError(404, '物品不存在');
        let newImage = row.image_override;
        if (Object.hasOwn(body, 'imageBase64')) {
          const bytes = imageBytes(body.imageBase64);
          newImage = `${randomUUID()}.png`;
          const pending = join(uploadsDir, `${newImage}.tmp`);
          try { writeFileSync(pending, bytes, { mode: 0o600, flag: 'wx' }); renameSync(pending, join(uploadsDir, newImage)); }
          catch (error) { try { unlinkSync(pending); } catch {} throw error; }
        }
        let transaction = false;
        try {
          db.exec('BEGIN IMMEDIATE');
          transaction = true;
          update.run(body.name, body.description, body.imagePrompt, newImage, row.key);
          saveChange(row, itemValues(row), { name: body.name, description: body.description, imagePrompt: body.imagePrompt, imageOverride: newImage });
          db.exec('COMMIT');
          transaction = false;
        } catch (error) {
          if (transaction) db.exec('ROLLBACK');
          if (newImage !== row.image_override) try { unlinkSync(join(uploadsDir, newImage)); } catch {}
          throw error;
        }
        return respond(res, 200, publicItem(byKey.get(row.key)));
      }
      throw new HttpError(404, '未找到页面');
    } catch (error) {
      if (res.headersSent) return res.destroy();
      const status = error instanceof HttpError ? error.status : 500;
      if (status === 500) console.error('物品网站请求失败：', error.message);
      respond(res, status, { error: status === 500 ? '服务器暂时无法处理请求' : error.message });
    }
  });
  server.headersTimeout = 15000;
  server.requestTimeout = 30000;
  const port = Number(process.env.CATALOG_PORT || '18753');
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('服务端口无效');
  server.listen(port, '127.0.0.1', () => console.log(`物品网站已监听本机端口 ${port}`));
}

class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
