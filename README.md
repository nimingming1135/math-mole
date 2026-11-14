# 口算打地鼠

面向一年级儿童的 20 以内加减法**打地鼠**答题 App（Kotlin 原生 Android）。
马卡龙配色、动物从专属小窝里钻出来、**全部语音均为离线预制音频**（无 TTS 依赖、无网络、无版权风险），无广告、无多余页面、无敏感权限、无第三方依赖。

---

## 📦 APK 下载（安装包位置）

| 方式 | 位置 |
|---|---|
| **直接下载（推荐）** | [Releases · math-mole-v1.0.0.apk](https://github.com/nimingming1135/math-mole/releases/download/v1.0.0/math-mole-v1.0.0.apk) |
| **本机构建产物** | `app/build/outputs/apk/debug/app-debug.apk`（约 6.3 MB） |
| **自行构建** | 见下方「构建与运行」 |

> 要求：Android 8.0（API 26）及以上。把 APK 传到手机后点击安装即可（需允许"未知来源"）。

---

## 功能特性

- **打地鼠式答题**：顶部大圆角大字号题目卡，底部 6 个**土洞/小窝**，动物从洞里钻出、举着数字牌；其中 1 个是正确答案，其余为贴近答案的干扰项，动物位置每次随机洗牌。
- **专属小窝**：6 种场景与动物一一对应 —— 🐱 小猫住软垫猫窝、🐷 小猪住泥土洞、🐶 小狗住木屋狗窝、🐵 小猴住树洞、🐰 小兔住草地洞、🐻 小熊住山岩洞；换题洗牌时小窝自动跟随动物。
- **点击反馈**：点答案时**锤子砸下**；答对 → 该洞变绿 + 动物被敲进洞里 + 随机表扬语音（12 条中英混合：答对了 / 你真棒 / 太厉害啦 / very good / excellent …），表扬播完自动出下一题；答错 → 警告音 + 动物眩晕摇晃，题目保留可继续作答。
- **自动读题**：题目出现即朗读（如「7 加 2」），读完才开始计时。
- **答题倒计时**：默认 15 秒，最后 3 秒「嘟嘟」提示；超时判负并播「答题失败」，记入错题集后自动进入下一题。倒计时**完全独立**，点击/答错/动效都不会让它暂停或停滞。
- **设置**（右上角齿轮）：答题倒计时 5~60 秒可调、语音播报开关、进入难度选择。
- **每日目标**：每次会话 30 道题（顶部显示进度）。
- **奖杯栏**：答对 10/20/30 题分别点亮铜/银/金奖杯。
- **错题巩固**：自动收录答错的题（含超时），30 题完成后进入错题专项练习，全部答对后弹出完成提示；错题仅存内存，退出即清空。
- **难度关卡**：六档（10/20/30/50/80/100 以内），初始仅解锁 10 以内，通关前一档自动解锁下一档，进度持久化。
- **平滑过渡**：每档前 10 题固定在上一档范围，第 11~15 题平滑过渡，第 16 题起满档；减法永不出现负数。

---

## 环境要求

- Android Studio Ladybug（2024.2）或更新版本（内置 JDK 17）
- 真机或模拟器：Android 8.0（API 26）及以上
- Gradle / Android SDK / Kotlin 首次同步自动下载（项目自带 Gradle Wrapper 8.9）

## 构建与运行

1. **File → Open**，选择本项目根目录（含 `settings.gradle.kts` 的目录）
2. 等待 Gradle 同步完成（首次自动下载 Gradle 8.9 与 compileSdk 35 组件）
3. 点击 **Run ▶** 运行到设备；或用命令行：

```bash
./gradlew :app:assembleDebug        # Windows: gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> `local.properties` 由 Android Studio 按本机 SDK 自动生成，不纳入版本管理。

## 运行单元测试

题目生成、游戏状态、读题音频拼接均为纯逻辑，可脱离设备测试：

```bash
./gradlew :app:testDebugUnitTest     # 共 13 项
```

## 项目结构

```
app/src/main/
├─ java/com/alex/mathmole/
│  ├─ MainActivity.kt            容器：Fragment 切换、全屏沉浸、初始化音频/设置
│  ├─ model/                     Level 难度档位、Question 题目数据
│  ├─ logic/
│  │  ├─ QuestionGenerator.kt    题目与 6 选项生成（纯函数，含过渡规则）
│  │  ├─ QuestionVoice.kt        读题音频拼接规则 + 音频预加载清单
│  │  ├─ GameCenter.kt           全局状态：题号/答对/连对/奖杯/错题本/阶段/解锁
│  │  ├─ SoundManager.kt         SoundPool 播放器：时长表精确排期、看门狗式兜底
│  │  └─ CountdownController.kt  倒计时：绝对时间基准，完全独立于交互
│  ├─ data/
│  │  ├─ LevelProgressStore.kt   SharedPreferences 持久化解锁进度
│  │  └─ SettingsStore.kt        倒计时秒数与语音开关
│  └─ ui/
│     ├─ AnswerFragment.kt       答题页（状态机：读题/锤击/答对/答错/超时）
│     ├─ LevelSelectFragment.kt  难度关卡选择页
│     └─ StarFallHelper.kt       金色星星飘落特效
└─ res/
   ├─ raw/                       126 个预制音频（数字/运算符/表扬/提示/音效）
   ├─ drawable/                  动物、小窝、锤子、土洞等矢量与形状资源
   └─ layout/                    activity / fragment_answer / dialog_settings / fragment_level_select
```

## 设计约束说明

- **依赖**：仅 4 个 AndroidX 基础库（core-ktx / appcompat / constraintlayout / fragment-ktx）+ 测试用 JUnit；不引入第三方库、不引入 Material 组件库。
- **权限**：Manifest **无任何权限声明**（音频、SharedPreferences 均不需要权限）。
- **音频**：126 个 WAV 全部离线预制（中文数字与话术由 Windows SAPI 合成，音效程序合成），运行时零网络、零 TTS 依赖。
- **数据**：错题与答题计数仅存内存，退出即清空；持久化的只有「最高已解锁档位」与「设置项」。
- **页面**：仅答题页与难度选择页两个界面（错题巩固复用答题页），无广告、无开屏页。
