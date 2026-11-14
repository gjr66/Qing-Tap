# 清弹开发交接 · DSH

> 2026-11-12 更新：当前工作目录已在下述 r49 基线上完成防误触修复，构建为 **2.0-r50 / versionCode 50**。APK、验证结果见 `CHANGELOG.md`，真机复测事项见 `BUGS.md`。下文源码压缩包的 SHA-256 仅对应原始 r49 包。

## 开始前

源码是清弹 Android **2.0 r49「首页优先」**，包名 `local.qingtap.adskip`，最低支持 Android 8.0。把本文件和 `QingTap-2.0-r49-source.zip` 一起保留。解压后，用 DSH 打开包含 `build.ps1` 的目录。多个历史包都显示 2.0，请按构建名称和文件指纹确认基线。

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath .\QingTap-2.0-r49-source.zip
```

源码包的 SHA-256 为 `C11F31821DD1A5AFAE9366F2D88766C652536EAC133778C6D1B7E2281A4AA950`。

## 清弹怎么处理广告

1. **检查窗口。** 无障碍服务收到前台窗口事件，核对应用及开屏、内嵌设置。两类各有处理名单和黑名单。
2. **尝试首页。** 确认从桌面启动且允许处理开屏时，优先检查公开首页入口。核对包名、导出状态和目标组件。入口不可用或请求失败，就继续识别广告。
3. **找关闭控件。** 根据控件 ID、文字和页面结构匹配规则。大麦和腾讯视频有各自的开屏条件，教学记录也能为对应应用提供候选目标。
4. **需要时看截图。** 对符合条件的开屏，用教学截图、颜色灰度特征或可用的视觉识别补充判断。没有可靠的关闭位置就不点。
5. **复核后点击。** 点击前核对当前窗口和目标。可疑页面重复出现达到设定次数时，可以先请用户确认。用户确认不是广告后停止操作。
6. **核对结果。** 根据窗口、控件或画面反馈记录结果。排查时区分首页请求、识别命中、点击执行，以及点击后广告是否还在。

代码位于 `app/src/main/java/local/qingtap/adskip/`。建议从以下文件开始读。

| 要查的环节 | 源码入口 |
| --- | --- |
| 前台事件、识别与点击调度 | `AdSkipService.java` |
| 首页入口判断 | `HomeLaunch.java`、`HomeEntryFinder.java` |
| 控件规则和截图学习 | `AdRules.java`、`TaughtTargets.java`、`LiveScreenshotMatch.java` |
| 批量截图测试 | `AdTestBatch.java`、`ScreenshotReview.java` |
| 开屏与内嵌的名单 | `AppSelection.java`、`AppSelectionActivity.java` |
| 防误触和关闭记录 | `RepeatGuard.java`、`ClickGate.java`、`AdActionLog.java`、`CloseHistoryActivity.java` |

## 本地构建

Windows 上准备 JDK 17+、Android SDK Platform 35 和 Build Tools 35.0.0。从解压后的源码根目录运行，按本机位置填写两个路径。

```powershell
.\build.ps1 -SdkRoot 'C:\路径\Android\Sdk' -JavaHome 'C:\路径\jdk-17'
```

源码包已包含 OCR 模型和 ONNX Runtime Android 依赖。脚本会先运行五组测试，再打包、测试签名并校验 APK。当前输出文件名按 Manifest 版本生成，默认在 `build\<时间戳>\QingTap-2.0-r50.apk`。本地新签名的测试包能否覆盖手机已有的安装，取决于两者的签名是否一致。遇到签名冲突先保留原安装和数据，记录报错。

macOS/Linux 可使用 Python 3 和对应主机的 Android SDK Build Tools：

```sh
python3 build.py --sdk-root /path/to/android-sdk --java-home /path/to/jdk
```

同样运行五组测试并验证签名、ZIP 对齐和内容完整性，输出目录还包含 `build.log`、`build-info.json` 和 APK 的 `.sha256` 文件。

## 已知问题和协作方式

- **志愿汇 5.9.5。** 在 OPPO 上实测先进入首页，约 3 秒后出现 AdGain 全屏广告。清弹未自动关掉，手点 `adgain_interstitial_close_ll` 后回到首页。这项修复暂时搁置。
- **荣耀首页跳转。** 待真机复测。确认无障碍已绑定、目标 App 的开屏处理已启用，再从桌面启动大麦。记录 `damai://home` 的解析结果、清弹是否发出首页请求，以及实际进入哪个页面。
- **并行开发。** 两人从同一份源码开始，各开 Git 分支。提交时写清改动、关联 Bug、构建标识和测试结果。真机结果写明设备、系统、目标 App 版本及失败阶段。
- **本地交接。** 每次交付随源码附上版本和 Bug 记录。版本记录写构建标识、功能、修复和验证结果。Bug 记录写设备、系统、目标 App 版本、复现步骤、实际结果和当前状态。两人用 Git 时，可把这些记录和对应的代码一并提交。
