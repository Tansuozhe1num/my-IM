# EasyChat API Curl

基础地址来自 `application.properties`:

```bash
BASE_URL="http://localhost:5050/api"
```

说明:

- 当前控制器里的接口都使用了 `@RequestMapping`，未限制具体 HTTP 方法，下面统一按 `POST` 示例给出。
- 需要登录态的接口要在请求头里带 `token`。
- 注册和登录前都要先调用一次验证码接口，拿到 `checkCodeKey`，并使用验证码图片里展示的结果作为 `checkCode`。

## 1. 获取验证码

```bash
curl -X POST "$BASE_URL/account/checkcode"
```

返回中重点关注:

- `data.checkCodeKey`
- `data.checkCode`（base64 图片）

## 2. 注册

```bash
curl -X POST "$BASE_URL/account/register" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "checkcodekey=替换为上一步返回的checkCodeKey" \
  --data-urlencode "email=test@example.com" \
  --data-urlencode "nickname=testuser" \
  --data-urlencode "password=123456" \
  --data-urlencode "checkCode=替换为验证码计算结果"
```

参数:

- `checkcodekey`: 验证码 key
- `email`: 邮箱
- `nickname`: 昵称
- `password`: 密码
- `checkCode`: 验证码结果

## 3. 登录

```bash
curl -X POST "$BASE_URL/account/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "checkcodekey=替换为上一步返回的checkCodeKey" \
  --data-urlencode "email=test@example.com" \
  --data-urlencode "password=123456" \
  --data-urlencode "checkCode=替换为验证码计算结果"
```

返回中重点关注:

- `data.token`

## 4. 获取系统设置

```bash
curl -X POST "$BASE_URL/account/getSysSetting" \
  -H "token: 替换为登录返回的token"
```

## 5. 新增或保存群组

新建群组时可以不传 `groupId`，更新群组时传已有的 `groupId`。

```bash
curl -X POST "$BASE_URL/group/savegroup" \
  -H "token: 替换为登录返回的token" \
  -F "groupId=" \
  -F "groupName=测试群" \
  -F "groupNotice=这是一个测试群公告" \
  -F "joinType=1" \
  -F "avaterfile=@D:/temp/avatar.png" \
  -F "avaterCover=@D:/temp/avatar-cover.png"
```

参数:

- `groupId`: 群 ID，可空
- `groupName`: 群名称，必填
- `groupNotice`: 群公告，可空
- `joinType`: 入群方式，必填
- `avaterfile`: 群头像文件，可空
- `avaterCover`: 群头像封面，可空

## 一次看全量接口

```bash
curl -X POST "$BASE_URL/account/checkcode"

curl -X POST "$BASE_URL/account/register" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "checkcodekey=CHECK_CODE_KEY" \
  --data-urlencode "email=test@example.com" \
  --data-urlencode "nickname=testuser" \
  --data-urlencode "password=123456" \
  --data-urlencode "checkCode=CHECK_CODE"

curl -X POST "$BASE_URL/account/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "checkcodekey=CHECK_CODE_KEY" \
  --data-urlencode "email=test@example.com" \
  --data-urlencode "password=123456" \
  --data-urlencode "checkCode=CHECK_CODE"

curl -X POST "$BASE_URL/account/getSysSetting" \
  -H "token: TOKEN"

curl -X POST "$BASE_URL/group/savegroup" \
  -H "token: TOKEN" \
  -F "groupId=" \
  -F "groupName=测试群" \
  -F "groupNotice=这是一个测试群公告" \
  -F "joinType=1" \
  -F "avaterfile=@D:/temp/avatar.png" \
  -F "avaterCover=@D:/temp/avatar-cover.png"
```
