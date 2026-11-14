# 口算打地鼠

面向一年级儿童的 20 以内加减法打地鼠答题 App（Kotlin 原生 Android）。
柔和马卡龙配色、可爱动物按钮、中文语音播报，无广告、无多余页面、无敏感权限、无第三方依赖。

## 功能特性

- **打地鼠式答题**：顶部大圆角大字号展示随机题目，底部 6 个动物大按钮（小猫/小猪/小狗/小猴/小兔/小熊）等距排列，其中 1 个是正确答案，其余为随机干扰项（干扰项优先贴近正确答案，动物位置每次随机洗牌）。
- **答对反馈**：按钮缩放淡出"打地鼠"动画，动物图标同步晃脑袋、摇尾巴，系统 TTS 播报中文「答对了」，播完自动加载新题；连续答对 3 题额外触发金色星星飘落。
- **答错反馈**：动物图标耷拉耳朵，语音播报「回答错误，请再想想」并弹出文字提示，保留当前题目；播报期间屏蔽重复点击。
- **每日目标**：每次会话默认完成 30 道题（顶部显示进度）。
- **奖杯栏**：顶部累计奖杯，答对 10/20/30 题分别点亮铜/银/金奖杯。
- **错题巩固**：自动收录首次答错的题，30 题完成后弹出错题集专项练习页（同样的动物答题样式），全部答对后弹出完成提示；错题仅存本地内存，退出 App 自动清空。
- **难度关卡**：右上角设置入口，六档难度（10/20/30/50/80/100 以内），初始仅解锁 10 以内；完成前一档 30 题目标且错题巩固通关后自动解锁下一档。手动选难度时，题目生成、奖杯计数、错题收录均按对应档位适配。
- **平滑过渡**：每档前 10 题固定在上一档范围内（20 以内档即"前 10 题 10 以内"），第 11~15 题平滑过渡，第 16 题起满档位范围；减法永不出现负数。
- **一键静音**：右上角随时开关语音；TTS 初始化失败时静默降级为纯文字提示。

## 环境要求

- Android Studio Ladybug（2024.2）或更新版本（内置 JDK 17）
- 无需手动安装 Gradle / Android SDK / Kotlin（首次同步自动下载）
- 真机或模拟器：Android 8.0（API 26）及以上

## 构建与运行

1. File → Open，选择本项目根目录（含 `settings.gradle.kts` 的目录）
2. 等待 Gradle 同步完成（首次会自动下载 Gradle 8.9 与 compileSdk 35 组件）
   - 若提示 gradle wrapper jar 缺失：点击 Android Studio 的提示让其自动补齐即可（本项目按文本协议交付，二进制 wrapper jar 未随文件提供）
3. 点击 Run ▶ 运行到设备

## 运行单元测试

题目生成与游戏状态为纯逻辑，可直接跑本地 JUnit 测试（无需设备）：

```
Android Studio 中：右键 app/src/test → Run Tests
或命令行：./gradlew :app:testDebugUnitTest（Windows 用 gradlew.bat）
```

## 项目结构

```
app/src/main/java/com/alex/mathmole/
├─ MainActivity.kt          容器：初始化 TTS/解锁进度，Fragment 切换
├─ model/                   Level 难度档位、Question 题目数据
├─ logic/
│  ├─ QuestionGenerator.kt  题目与 6 选项生成（纯函数，含过渡规则）
│  ├─ GameCenter.kt         全局状态单例：题号/答对/连对/奖杯/错题本/阶段
│  └─ TtsManager.kt         系统 TTS：播报/静音/失败降级/播完回调
├─ data/LevelProgressStore.kt  SharedPreferences 持久化解锁进度
└─ ui/
   ├─ AnswerFragment.kt     答题页（主流程 / 错题巩固双模式复用）
   ├─ LevelSelectFragment.kt 难度关卡选择页
   └─ StarFallHelper.kt     金色星星飘落特效
```

## 设计约束说明

- **依赖**：仅 4 个 AndroidX 基础库（core-ktx / appcompat / constraintlayout / fragment-ktx）+ 测试用 JUnit；不引入任何第三方库、不引入 Material 组件库
- **权限**：Manifest 无任何权限声明（TTS 与 SharedPreferences 均不需要权限）
- **数据**：错题与答题计数仅存内存，退出即清空；唯一持久化的是"最高已解锁档位"（SharedPreferences）
- **页面**：仅答题页与难度选择页两个界面（错题巩固复用答题页），无广告、无开屏页
