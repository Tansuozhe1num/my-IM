# EasyChat Web

独立的 React + Vite 客户端，面向 `easychat-java` 的 REST API 和 WebSocket 服务。

## 网页开发模式

```bash
npm install
npm run web
```

默认请求地址：

- REST：`http://localhost:5050/api`
- WebSocket：`ws://localhost:5051/ws`

可以用环境变量覆盖：

```bash
VITE_API_BASE=http://localhost:5050/api VITE_WS_URL=ws://localhost:5051/ws npm run web
```

## 桌面客户端

桌面版使用 Electron，支持从菜单 `EasyChat -> 新建独立窗口` 或快捷键 `Ctrl+Shift+N` 多开。每个窗口有独立的登录存储分区，可以同时登录不同账号。

```bash
npm install
npm run dev       # 启动 Vite 并打开桌面客户端
npm run dist      # 构建 Windows 安装包和 portable 版本
```

项目已在 `.npmrc` 中配置 Electron 镜像。如果此前安装失败导致 `node_modules/electron/path.txt` 不存在，请在 PowerShell 项目目录执行：

```powershell
Remove-Item -Recurse -Force node_modules/electron
npm install
npm run dev
```

如果仍然无法下载，可显式指定镜像后安装：

```powershell
$env:ELECTRON_MIRROR="https://npmmirror.com/mirrors/electron/"
npx install-electron --no
npm run dev
```

也可以给不同客户端进程指定独立配置名：

```bash
electron . --profile=account-a
electron . --profile=account-b
```

生产构建会从本地 `dist` 加载页面，接口仍默认连接 `http://localhost:5050/api`，WebSocket 连接 `ws://localhost:5051/ws`。

## 已对接接口

- `/account/checkcode`、`/account/login`、`/account/register`
- `/user/getUserInfo`
- `/contact/loadContact`、`/contact/loadApply`、`/contact/searchfriends`
- `/contact/applyAdd`、`/contact/solveApply`
- `/group/loadmygroup`
- `/chatSessionUser/loadDataList`
- `/chatMessage/loadDataList`

## 需要后端补充或调整

1. **浏览器 WebSocket 鉴权**：客户端使用 `wss://host/ws?token=...` 连接，后端 Netty 已从握手 URI 的 query 参数读取并校验 token。
2. **WebSocket 消息发送协议**：客户端发送好友私聊 JSON（`messageType: 2`、`sessionId`、`contactId`、`contactType: 0`、`messageContent`、`clientMessageId`）。服务端会校验登录用户、好友关系和会话归属，保存消息并更新会话预览，再向发送方回执、向接收方投递。`clientMessageId` 仅用于前端去重，不写入消息表。
3. **初始化推送协议**：`WsInitData` 已定义，但服务端尚未看到发送初始化消息的逻辑。建议连接成功后推送 `messageType: 0`，包含会话、历史消息和 `applyCount`。
4. **文件上传**：当前客户端只展示附件按钮。若要支持图片、语音和文件，需要上传接口（返回可访问 URL、文件类型、大小和文件名），以及消息发送/下载地址。
5. **会话写入与已读**：好友通过申请时，服务端会创建双方会话、保存申请留言并通过 WebSocket 通知在线客户端；普通私聊消息也会原子更新消息和会话预览。已读、删除会话仍需单独的业务接口。

接口返回统一按 `ResponseVO { status, code, info, data }` 处理，登录 token 暂存于浏览器 `localStorage`。
