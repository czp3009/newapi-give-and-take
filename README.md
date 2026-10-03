# newapi-give-and-take

Reward users for contributing channels to a new-api instance. Users receive balance based on the usage of their
contributed channels and can spend it through new-api.

When run, the program reads the channel list from new-api and adds balance to associated users based on channel usage.

## Usage

### Configure the connection

Run the executable from your preferred working directory:

```bash
./newapi-give-and-take.kexe
```

On Windows, use PowerShell:

```powershell
.\newapi-give-and-take.exe
```

On first run, missing `./config.json` and `./state.json` files are created automatically. If connection settings are
missing, the program reports them. Edit `config.json` with your new-api URL and an **admin access token**, then run
again:

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

HTTP URLs such as `http://localhost:3000` are also supported.

The program maintains `state.json` automatically. Keep it between runs so rewards are calculated from the last recorded
consumption. Run one instance at a time.

### Associate channels with users

In new-api, put `user:1` on its own line in a channel's remark to reward user `1`. Other remarks can go on separate
lines:

```text
user:1
Other notes about this channel
```

Set `tracking.field` to `tag` to use channel tags instead of remarks. You can change `tracking.pattern`, but it must
match a complete line and capture a positive user ID in a named group called `userId`. Lines are not trimmed. Escape
regex backslashes in JSON, for example `\\d` for `\d`.

**The first matching line wins.** Later matches are ignored, including when the first match captures an invalid user ID.
Channels without a valid match receive no rewards. Only one tracking rule is supported.

Reward behavior:

- The first time a channel is tracked, its current consumption becomes the starting point; earlier usage is not
  rewarded.
- Later increases in consumption add balance to the associated user. Refunds or other decreases reduce that balance.
- Changing the associated user starts tracking from current consumption without rewarding earlier usage to either user.
- Removing an association stops tracking when observed by a run. Restoring it after removal starts fresh without rewards
  for the untracked period.

### Command-line options and environment variables

Use `--help` or `-h` to list options. Select different config/state files with `--config` / `-c` and `--state` / `-s`,
including their full file names:

```bash
./newapi-give-and-take.kexe --config "/home/user/rewards/settings.json" --state "/home/user/rewards/baselines.json"
```

On Windows:

```powershell
.\newapi-give-and-take.exe --config "C:\rewards\settings.json" --state "C:\rewards\baselines.json"
```

Relative paths use the **current working directory**, not the executable's directory. File paths have no
environment-variable overrides.

Each setting uses the first non-empty value in this order: **command-line argument > environment variable >
configuration file**. Overrides do not change the config file. If every source is empty or missing, the program reports
the missing setting.

| Command-line argument  | Environment variable   | Configuration file key |
|------------------------|------------------------|------------------------|
| `--newApi.baseUrl`     | `NEW_API_BASE_URL`     | `newApi.baseUrl`       |
| `--newApi.accessToken` | `NEW_API_ACCESS_TOKEN` | `newApi.accessToken`   |
| `--tracking.field`     | `TRACKING_FIELD`       | `tracking.field`       |
| `--tracking.pattern`   | `TRACKING_PATTERN`     | `tracking.pattern`     |

For example, set connection details through environment variables and select tags on the command line:

```bash
export NEW_API_BASE_URL="http://localhost:3000"
export NEW_API_ACCESS_TOKEN="YOUR_ADMIN_TOKEN"
./newapi-give-and-take.kexe --tracking.field tag
```

On Windows:

```powershell
$env:NEW_API_BASE_URL = "http://localhost:3000"
$env:NEW_API_ACCESS_TOKEN = "YOUR_ADMIN_TOKEN"
.\newapi-give-and-take.exe --tracking.field tag
```

On Linux/macOS, environment values should use UTF-8. On Windows, use ASCII command-line values and paths, including the
working directory. Supply non-ASCII settings through config contents or environment variables.

### Proxy

Set `HTTPS_PROXY` for an HTTPS new-api address or `HTTP_PROXY` for an HTTP address. Each variable accepts an HTTP proxy
URL; empty values are ignored.

```bash
export HTTPS_PROXY="http://127.0.0.1:7890"
export HTTP_PROXY="http://127.0.0.1:7890"
./newapi-give-and-take.kexe
```

On Windows:

```powershell
$env:HTTPS_PROXY = "http://127.0.0.1:7890"
$env:HTTP_PROXY = "http://127.0.0.1:7890"
.\newapi-give-and-take.exe
```

## Build from source

Install **JDK 25** and run the Gradle wrapper from the repository root. Build on the same OS and architecture as the
target executable:

| Platform    | Build command                               | Executable                                                       |
|-------------|---------------------------------------------|------------------------------------------------------------------|
| Linux x64   | `./gradlew linkDebugExecutableLinuxX64`     | `build/bin/linuxX64/debugExecutable/newapi-give-and-take.kexe`   |
| macOS ARM64 | `./gradlew linkDebugExecutableMacosArm64`   | `build/bin/macosArm64/debugExecutable/newapi-give-and-take.kexe` |
| Windows x64 | `.\gradlew.bat linkDebugExecutableMingwX64` | `build/bin/mingwX64/debugExecutable/newapi-give-and-take.exe`    |

For example, build and run on Linux x64:

```bash
./gradlew linkDebugExecutableLinuxX64
./build/bin/linuxX64/debugExecutable/newapi-give-and-take.kexe
```

On Windows x64:

```powershell
.\gradlew.bat linkDebugExecutableMingwX64
.\build\bin\mingwX64\debugExecutable\newapi-give-and-take.exe
```
