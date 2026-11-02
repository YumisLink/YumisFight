# Mods 同步器开发说明

本工具独立于 NeoForge 游戏模块，源码在 ModSyncTemp，不改动游戏架构文件、协议、存档、数值或单 JAR 安装方式。

## HTTP 清单协议 v1

固定入口为 `https://119.91.113.235/yumis-mods/current.json`。HTTPS 使用专用公钥固定验证，Basic 凭据仅授予读取；不向外部来源转发。清单最大 2 MiB，按发布号冻结一次同步，不在下载中切换版本。

顶层字段为 schemaVersion、releaseId、publishedAt（ISO 8601）、minecraft、neoForge、files。每个文件含 name、size、sha256、sha1、sha512、sourceStatus、sources；source 为 kind（modrinth/curseforge/server）与 url。名称限制为一个 Windows 安全 JAR 文件名，禁止路径、大小写冲突和保留名。最终以服务器清单的 SHA-256 与长度为准。

有外部来源时不声明 server 副本。服务器对象使用 SHA-256 命名，只为无可靠外部链接的文件上传。发布端比较 PCL 当前 Version 的 Hash/Size，忽略 UpdateFile，不依赖 Project.Platform 推断混合缓存的来源。Modrinth 查询按文件哈希精确匹配，不追最新版本。

## 下载助手

固定官方 PCL 2.13.1.1 SHA-256 为 `30eb93c62fc51ede0898648e67455478a3d5a4f279938ae65aec77b84cb9703f`。原样释放到 PCL 目录的专用 temp 内，避开 Windows 系统 Temp 的 PCL 拒绝运行规则。

程序用全局进程身份（PID、启动时间、可执行路径、PCL 产品元数据）关闭 PCL。正常等待15秒，再展示60秒自动强制结束倒计时；取消停止操作，继续等待重新进入正常等待流程。只结束明确识别的 PCL 本进程，不结束进程树。

临时 mrpack 含唯一实例名、minecraft 依赖、必需客户端文件与官方 CF URL。临时路径用 SHA-256 文件名，最后按正式清单恢复原 Unicode 文件名。同步器等待本次日志整体成功、包被删除、全部文件哈希匹配，并在助手退出后复制到自己的缓存。正式实例的选择不由助手修改。

PCL 临时 LaunchFolders 登记使用单独恢复记录。仅移除本工具加入的目录条目，保留其他目录、登录状态、Java 缓存及 token 刷新。

## 文件事务

替换前完整校验缓存，对现有 JAR 创建并校验备份，持久化 prepared 日志；复核目标快照与游戏占用后写入 applying，再替换文件。整组校验成功后写 committed 并轮换备份。提交中不响应半组取消，恢复后才继续。

启动先恢复遗留事务。applying 恢复原 JAR；committed 只完成备份归档。备份被验证前不修改目标，恢复冲突保留到 recovery-conflicts，不丢弃并发修改。只清理具备本工具归属标记的工作目录，拒绝链接路径和越界递归清理。

## 2026年11月2日本轮验证

代码完成后已在 temp 沙盒验证：18组基础检查通过，覆盖真实HTTPS及Modrinth下载、哈希/凭据/证书/断网、路径与junction、Unicode、替换与回滚、备份轮换、残缺新增JAR和提交后内容损坏的进程中断恢复、合并JSON与隔离识别、PCL当前缓存、WPF窗口渲染。

官方PCL 2.13.1.1真实协作已取得4个仅有CF来源的文件，整体成功日志、包清理、大小与SHA-256匹配，助手正常退出并清理临时实例。整组沙盒验证29个文件首次同步、无变化不轮换备份、恢复空目录备份、缓存再次同步通过。

独立验证进程覆盖正常关闭、PID/启动时间保护、取消、继续等待、强制结束且子进程保留。真实15+60秒无响应倒计时约75.5秒后结束验证进程。最终自包含单EXE实际启动、自动同步和自动打开原PCL通过；原游戏未启动，正式mods未做写入或故障演练。

服务器已启用专用HTTPS，原10086服务仍正常；只读路由无凭据401，secure/incoming及穿越路径404，incoming权限0700 ubuntu:ubuntu。首发清单29项，24项有Modrinth、14项有CF（部分重叠），只上传1份自研JAR。

尚未覆盖其他电脑的干净Windows实测、所有网络故障组合及完整游戏兼容性；运行时已自包含打包。临时验证源码、日志、截图和报告仅在temp，不上传。没有执行游戏测试、Git暂存/提交/推送或PR。
