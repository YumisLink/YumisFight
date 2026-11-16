# 物品清单网站

此工具只维护网站自己的物品快照、文本改稿和上传图，不写回游戏资源。游戏物品的真实来源仍是各所属模块及其资源制作源；以后应用改稿时，按导出的物品 ID 和变体核对，并分别处理共用贴图。

## 数据采集

采集和验证须遵守根目录 `AGENTS.md` 对本任务测试和实机的授权要求。使用已经初始化、规则同步完成的隔离客户端，设为简体中文，关闭高级物品提示并松开 Shift。启动参数中启用既有 `ydnf.verify`，并设置 `-Dydnf.itemCatalogModSha256=<隔离客户端所用模组JAR的SHA-256>`。向其 `verification/control.json` 写入新的控制 ID 与 `{"action":"itemCatalog"}`；结果为同目录的 `item-catalog-raw.json`。隔离客户端和采集过程的日志、控制文件和原始输出留在 `temp`，不要上传。

在仓库根目录，用本机 Python 和 `requirements.txt` 中的 Pillow，把原始结果转换为网站正式数据。`--output` 必须指向不存在的目录；命令会拒绝覆盖既有数据。客户端 JAR 路径以本机环境为准，不包含在网站源码中。

```text
python tools/item-catalog/build_catalog.py --input <隔离客户端的原始结果> --mod-jar <采集客户端使用的模组JAR> --minecraft-client-jar <本机客户端JAR> --output tools/item-catalog/seed
```

采集异常、无法判断的模型、缺失贴图或无法解析的动画都会阻止生成。需要先核对并解决问题，不能把未完成的清单当作完整数据部署。生成的 `seed/catalog.json` 和 `seed/icons` 是网站正式数据；服务器公开浏览不需要原始客户端 JAR 或 `temp` 下的文件。

## 网站与部署

工作区中新注册且已核实为二维的物品，可作为仅新增条目追加到快照。服务重启时校验旧物品 ID、变体和创造栏分类不变，再只插入新项并保留已编辑文本与上传贴图。快照不支持删除、重命名或重分类迁移。

`server.mjs` 需要 Node.js 24 的内置 SQLite，网页文件在 `public/`；启动或设置口令时必须用 `CATALOG_DATA` 指定专用数据目录，本机运行应放在仓库 `temp` 中。首次部署前将正式 `seed/` 与网站源码放入 `/srv/yumis-items/app/`，单独建立 `/srv/yumis-items/data/` 并赋予服务用户写权限。通过标准输入向 `server.mjs --set-password` 提供编辑口令；该命令只创建加盐摘要文件，已有口令文件不会被覆盖。口令不要放入命令参数、仓库、网页文件或日志。

`deploy/yumis-items.service` 是独立服务模板；`deploy/nginx-http.conf` 通过 **34871** 端口提供 HTTP，`deploy/nginx-location.conf` 提供物品网站的路径配置。应用本身仍只监听本机的 18753 端口。上线时先核对端口和现有配置，保留原下载服务的认证；检查配置无误后再重载宝塔 nginx。不要把此站点的匿名读取配置扩大到原下载路径。HTTP 访问时编辑口令和内容不会加密。SQLite 数据库和上传图片位于 `/srv/yumis-items/data/`，替换程序文件时应保留；程序仅允许追加保留旧 ID、变体与分类的新增条目。

浏览器导出的 JSON 包含物品 ID、变体标识及所选文本。编辑者可查看、导出或删除修改记录；记录保存文本和贴图的修改前后内容，导出的贴图链接可供 Agent 取用。删除记录不会撤销当前物品修改。较早的现有改稿会在首次启用记录时与原始快照比对并合并补录，无法还原更早的逐次修改。网站不会自动更新游戏或同步未来版本。

网页物品编辑与修改记录接口见 `AGENT_API.md`；部署后也可在网站中打开 Agent API 说明。

编辑口令验证后，浏览器以 HttpOnly Cookie 保存一年的编辑会话；服务器仅保存随机会话令牌的摘要，不在网页中持久保存明文口令。清除网站数据或会话过期后需重新输入口令。

游戏 `.png.mcmeta` 动画预览由 `build_animation_previews.py` 从物品 PNG 与帧配置生成 APNG 和逐帧检查用竖向帧条，输出在 `public/animations/`。网站卡片和详情依据帧配置在 Canvas 中逐帧绘制原贴图；普通图标编辑仍保存 PNG。
