# 物品网站 Agent API

用途：读取网页上的物品改稿及修改记录，将用户指定的改动应用到游戏，或按用户要求编辑网页。游戏源码、资源生成源和仓库根目录 `AGENTS.md` 仍是应用到游戏时的依据；不要从介绍文本推断游戏效果。

服务地址：`http://119.91.113.235:34871/yumis-items/`。以下路径相对此地址。HTTP 不加密；口令只通过用户提供的安全输入获取，勿写入源码、命令参数、日志或导出文件。

## 认证

读取物品和图片无需认证；修改物品、读取或删除修改记录需要编辑认证。先将口令的 UTF-8 字节编码为**无填充的 Base64URL**，请求：

```http
POST api/verify
Authorization: Bearer <Base64URL编码的口令>
```

成功返回 `204` 和 `Set-Cookie`。后续请求携带该 Cookie；它是 HttpOnly、SameSite=Strict 的一年期会话。也可在需认证的请求上直接发送上述 `Authorization`。`GET api/session` 返回 `{"authenticated":true/false}`，只检查 Cookie。错误口令返回 `401`，连续失败可能返回 `429`。

## 读取与修改网页物品

1. `GET api/catalog` 返回 `{categories, items}`。每项包含 `key`、`id`、`variant`、`name`、`description`、`imagePrompt`、`image` 和 `categories`。`id` 是游戏注册 ID，`variant` 区分共用 ID 的物品，`key` 是网页 API 的稳定标识。`image` 是相对服务地址的 PNG 路径。
2. `GET api/animations` 返回 `{items}`，以稳定物品 `key` 为键，提供 APNG `src`、供逐帧检查的竖向帧条 `sheet`、编码帧数、源帧数与循环时长。用 `GET <src>` 下载 APNG，或 `GET <sheet>` 下载竖向帧条；文件根据模组 PNG 与 `.mcmeta` 帧配置生成。网页依据帧配置在 Canvas 中逐帧绘制贴图，不移动或滚动帧条；相邻相同画面可能在 APNG 中合并时长。
3. 对要修改的物品，先读取当前值，再向 `PATCH api/items/<key>` 发送 `Content-Type: application/json`：

```json
{
  "name": "当前或修改后的名称",
  "description": "当前或修改后的介绍文本",
  "imagePrompt": "当前或修改后的生图文本",
  "imageBase64": "可选：PNG 文件的标准 Base64，不带 data URL 前缀"
}
```

三个文本字段均必填；保留不改的字段原值。PNG 最大 8 MiB、宽高各不超过 4096 像素。成功返回更新后的物品，并仅在内容实际改变时新增修改记录。接口没有并发冲突检测，写入前需重新读取，避免覆盖他人的改稿。

## 把网页改动应用到游戏

1. `GET api/changes` 返回 `{changes:[...]}`，按最新记录在前。每条有 `id`、`timestamp`、`itemKey`、`itemId`、`variant`、`itemName`、`baseline` 和 `changes`。`changes` 仅包含变动字段：`name`、`description`、`imagePrompt` 或 `image`；每个字段都有 `before` 和 `after`。`baseline: true` 表示较早改稿与原始快照的合并补录，时间为补录时间。
2. 使用 `itemId` 和 `variant` 定位实际物品及所属模块。贴图的 `before` / `after` 是相对服务地址的 PNG 路径；用 `GET` 下载所需图片。核对共享贴图和正式生成源，按用户要求应用；保留工作区中其他任务的改动。
3. 完成并验证对应改动后，向 `POST api/changes/delete` 发送 `Content-Type: application/json` 和 `{"ids":[记录ID,...]}`。响应为 `{"deleted":数量}`。只删除已处理的记录，失败或未处理的记录保留；删除记录不会撤销网站物品的当前值。删除后不再被当前物品或其他记录引用的历史上传图片可能被清理，需先取用。

网站“修改记录 → 导出记录”提供同样的 JSON，且贴图路径已转换为绝对 URL，可直接交给无法登录网站的 Agent。
