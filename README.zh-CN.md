# newapi-give-and-take

[English](README.md) | [简体中文](README.zh-CN.md)

奖励向 new-api 实例贡献渠道的用户. 用户会根据其贡献渠道的用量获得余额, 并可通过 new-api 消费这些余额.

程序运行时会从 new-api 读取渠道列表, 并根据渠道用量为关联的用户增加余额.

## 使用

### 安装

从 [GitHub Releases](https://github.com/czp3009/newapi-give-and-take/releases) 下载 Linux x64, macOS ARM64 或 Windows
x64 的可执行文件. 在 Linux/macOS 上将其重命名为 `newapi-give-and-take.kexe`, 在 Windows 上重命名为
`newapi-give-and-take.exe`.

在 Linux/macOS 上, 授予执行权限:

```bash
chmod +x newapi-give-and-take.kexe
```

安装 Node.js 和 npm 后, 也可以运行 npm 包:

```bash
npx @czp3009/newapi-give-and-take@latest --help
```

传入的选项与直接运行可执行文件时相同. 配置文件和状态文件仍使用当前工作目录.

### 配置连接

在你惯用的工作目录中运行可执行文件:

```bash
./newapi-give-and-take.kexe
```

在 Windows 上使用 PowerShell:

```powershell
.\newapi-give-and-take.exe
```

首次运行时会自动创建缺失的 `./config.json` 和 `./state.json` 文件. 如果缺少连接配置, 程序会报告它们. 在 `config.json`
中填入你的 new-api URL 和 **管理员访问令牌**, 然后再次运行:

```json
{
  "newApi": {
    "baseUrl": "https://newapi.example.com",
    "accessToken": "YOUR_ADMIN_TOKEN"
  },
  "tracking": {
    "field": "remark",
    "pattern": "user:(?<userId>[1-9][0-9]*)"
  }
}
```

也支持 HTTP URL, 例如 `http://localhost:3000`.

程序会自动维护 `state.json`. 请在多次运行之间保留该文件, 以便从上次记录的消耗量计算奖励. 同一时间只运行一个实例.

### 将渠道关联到用户

在 new-api 中, 将 `user:1` 单独放在渠道备注 (remark) 的一行中, 即可奖励用户 `1`. 其他备注可以放在单独的行中:

```text
user:1
关于该渠道的其他备注
```

将 `tracking.field` 设为 `tag` 可使用渠道标签 (tag) 而不是备注. 你可以修改 `tracking.pattern`, 但它必须匹配完整的一行,
并在名为 `userId` 的命名组中捕获一个正的用户 ID. 行不会被去除首尾空白. 在 JSON 中需要转义正则表达式的反斜杠, 例如用
`\\d` 表示 `\d`.

**第一个匹配的行优先.** 之后的匹配会被忽略, 包括第一个匹配捕获到无效用户 ID 的情况. 没有有效匹配的渠道不会获得奖励.
仅支持一条追踪规则.

奖励行为:

- 首次追踪某个渠道时, 以其当前消耗量作为起点; 之前的用量不会获得奖励.
- 之后消耗量增加时, 会为关联的用户增加余额. 退款或其他原因导致的减少会扣减该余额.
- 更换关联用户后, 从当前消耗量开始追踪, 不会向任何一方奖励之前的用量.
- 关联被移除后, 在某次运行观察到时停止追踪. 移除后恢复关联将重新开始, 未追踪期间的用量不会获得奖励.

### 命令行选项和环境变量

使用 `--help` 或 `-h` 查看选项列表. 使用 `--config` / `-c` 和 `--state` / `-s` 选择不同的配置/状态文件,
路径中需包含完整文件名:

```bash
./newapi-give-and-take.kexe --config "/home/user/rewards/settings.json" --state "/home/user/rewards/baselines.json"
```

在 Windows 上:

```powershell
.\newapi-give-and-take.exe --config "C:\rewards\settings.json" --state "C:\rewards\baselines.json"
```

相对路径基于 **当前工作目录**, 而不是可执行文件所在目录. 文件路径没有对应的环境变量覆盖.

每个设置按以下顺序使用第一个非空值: **命令行参数 > 环境变量 > 配置文件**. 覆盖值不会修改配置文件. 如果所有来源都为空或缺失,
程序会报告缺少该设置.

| 命令行参数             | 环境变量               | 配置文件键           |
|------------------------|------------------------|----------------------|
| `--newApi.baseUrl`     | `NEW_API_BASE_URL`     | `newApi.baseUrl`     |
| `--newApi.accessToken` | `NEW_API_ACCESS_TOKEN` | `newApi.accessToken` |
| `--tracking.field`     | `TRACKING_FIELD`       | `tracking.field`     |
| `--tracking.pattern`   | `TRACKING_PATTERN`     | `tracking.pattern`   |

例如, 通过环境变量设置连接信息, 并在命令行中选择标签:

```bash
export NEW_API_BASE_URL="http://localhost:3000"
export NEW_API_ACCESS_TOKEN="YOUR_ADMIN_TOKEN"
./newapi-give-and-take.kexe --tracking.field tag
```

在 Windows 上:

```powershell
$env:NEW_API_BASE_URL = "http://localhost:3000"
$env:NEW_API_ACCESS_TOKEN = "YOUR_ADMIN_TOKEN"
.\newapi-give-and-take.exe --tracking.field tag
```

在 Linux/macOS 上, 环境变量的值应使用 UTF-8. 对于 Windows 可执行文件, 命令行的值和路径 (包括工作目录) 请使用 ASCII. 非
ASCII 设置请通过配置文件内容或环境变量提供.

### 代理

为 HTTPS 的 new-api 地址设置 `HTTPS_PROXY`, 或为 HTTP 地址设置 `HTTP_PROXY`. 每个变量接受一个 HTTP 代理 URL; 空值会被忽略.

```bash
export HTTPS_PROXY="http://127.0.0.1:7890"
export HTTP_PROXY="http://127.0.0.1:7890"
./newapi-give-and-take.kexe
```

在 Windows 上:

```powershell
$env:HTTPS_PROXY = "http://127.0.0.1:7890"
$env:HTTP_PROXY = "http://127.0.0.1:7890"
.\newapi-give-and-take.exe
```

### Docker

Docker 镜像支持 **linux/amd64**. 所有配置均通过环境变量提供, 并挂载 `/root/newapi-give-and-take/data` 以持久化状态.
**如果不进行该挂载, 删除或重建容器会丢失状态并重置追踪基线.**

单次运行:

```bash
mkdir -p data
docker run --rm \
  --mount "type=bind,source=$(pwd)/data,target=/root/newapi-give-and-take/data" \
  -e NEW_API_BASE_URL="https://newapi.example.com" \
  -e NEW_API_ACCESS_TOKEN="YOUR_ADMIN_TOKEN" \
  -e TRACKING_FIELD=remark \
  -e TRACKING_PATTERN='user:(?<userId>[1-9][0-9]*)' \
  czp3009/newapi-give-and-take:latest
```

若要每五分钟运行一次, 设置 `RUN_MODE=cron` 并提供一个五字段的 `CRON_SCHEDULE`:

```bash
mkdir -p data
docker run -d --name newapi-give-and-take --restart unless-stopped \
  --mount "type=bind,source=$(pwd)/data,target=/root/newapi-give-and-take/data" \
  -e RUN_MODE=cron \
  -e CRON_SCHEDULE="*/5 * * * *" \
  -e NEW_API_BASE_URL="https://newapi.example.com" \
  -e NEW_API_ACCESS_TOKEN="YOUR_ADMIN_TOKEN" \
  -e TRACKING_FIELD=remark \
  -e TRACKING_PATTERN='user:(?<userId>[1-9][0-9]*)' \
  czp3009/newapi-give-and-take:latest
```

## 从源码构建

安装 **JDK 25**, 并在仓库根目录运行 Gradle wrapper. 请在与目标可执行文件相同的操作系统和架构上构建:

| 平台        | 构建命令                                    | 可执行文件                                                       |
|-------------|---------------------------------------------|------------------------------------------------------------------|
| Linux x64   | `./gradlew linkDebugExecutableLinuxX64`     | `build/bin/linuxX64/debugExecutable/newapi-give-and-take.kexe`   |
| macOS ARM64 | `./gradlew linkDebugExecutableMacosArm64`   | `build/bin/macosArm64/debugExecutable/newapi-give-and-take.kexe` |
| Windows x64 | `.\gradlew.bat linkDebugExecutableMingwX64` | `build/bin/mingwX64/debugExecutable/newapi-give-and-take.exe`    |

例如, 在 Linux x64 上构建并运行:

```bash
./gradlew linkDebugExecutableLinuxX64
./build/bin/linuxX64/debugExecutable/newapi-give-and-take.kexe
```

在 Windows x64 上:

```powershell
.\gradlew.bat linkDebugExecutableMingwX64
.\build\bin\mingwX64\debugExecutable\newapi-give-and-take.exe
```

在 Linux x64 上本地构建 Docker 镜像:

```bash
./gradlew linkReleaseExecutableLinuxX64
docker build -t newapi-give-and-take:local .
docker run --rm --network none --entrypoint /usr/local/bin/newapi-give-and-take newapi-give-and-take:local --help
```
