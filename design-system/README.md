# Lody Mobile Design System

iOS（`../ios`，SwiftUI）与 Android（`../android`，Compose）共享的设计语言、token 与图标。

打开 `generated/preview.html` 可以直接在浏览器里审阅全部 token（light / dark 对照、示例页面、字号、图标）。在真机或模拟器上对比两端效果，见 [`examples/`](examples/README.md) 中的 iOS / Android 示例 App。

## 设计原则

1. **内容优先，不做拟物。** 面向文字密集、高频操作的工作流（参考 Linear / Notion）：纯色底、细分割线，层级靠字重、颜色和留白表达。阴影只用于少数自定义浮层（菜单、toast），内容区保持扁平。
2. **系统外壳交给原生，内容层由我们定义。** 导航栏、Tab bar、Sheet、菜单、Alert、开关、日期选择器、转场与返回手势都用平台组件（iOS 26 的 Liquid Glass、Material 3），只通过 token 调整它们的字体、强调色和圆角。列表行、状态图标、消息、代码和 diff、输入框（composer）、空状态由我们实现，两端像素级一致。
3. **只用语义 token。** App 代码只引用语义名（`bgBase`、`textSecondary`），不直接引用色板。需要新颜色时先加语义 token。
4. **颜色不是唯一信号。** 状态同时用图形区分（`stateIdle` 虚线圈、`stateRunning` 进度圈、`stateWaiting` 暂停圈……），对比度由 `scripts/check.mjs` 卡住底线。
5. **尊重系统缩放。** 字号跟随 iOS Dynamic Type 和 Android 字体缩放；暗色模式跟随系统。Android 不启用 Dynamic Color。

## Token 体系

| 类别 | 文件 | 内容 |
|---|---|---|
| 色板（原始值） | `palette.json` | 只输入品牌色相（来自 Web）和各模式的规则，按 Linear 式算法在 OKLCH 中生成 12 档色阶 + 按钮实色档 |
| 语义色 | `color.json` | 背景层级、填充（按下 / 选中 / 禁用）、文字、图标、边框、强调色（tint / 实色）、危险 / 警告 / 成功 / 信息、会话状态、10 色分类标签、代码与语法高亮、diff、选区、遮罩、阴影色 |
| 字体 | `typography.json` | Inter + JetBrains Mono，16 档文字样式（display 到 codeSmall），字距按 Inter 公式推导 |
| 尺寸 | `dimension.json` | 间距、圆角、图标尺寸、描边、控件 / 行 / 头像尺寸、布局、窗口断点 |
| 数值 | `dimension.json` | 透明度（按下、弱化）；禁用态用专用颜色，不用透明度 |
| 动效 | `dimension.json` | 时长、缓动曲线、弹簧（SwiftUI duration/bounce，Compose 自动换算 stiffness/damping） |
| 阴影 | `elevation.json` | sm / md / lg 三级，颜色引用语义色，暗色自动加深 |
| 图标 | `icons/manifest.json` | 语义名 -> Lucide |

### 复用桌面端的边界

移动端只从桌面 / Web 带入**品牌层**，交互和材质层按移动端准则（iOS HIG、Material 3）重新定义：

| 复用 | 不复用（按移动端准则） |
|---|---|
| 品牌色相：强调蓝、危险、成功、警告、PR 合并 | 中性灰的具体取值（按 iOS 背景 / 文字层级生成，而不是桌面的 hover / selected） |
| 中性色零饱和的气质 | 深色模式配色：同色相提亮，不用桌面的 Vesper 主题 |
| Inter、JetBrains Mono 字体 | 按钮配色规则：主操作用强调色实色填充（平台惯例） |
| Lucide 图标 | 输入框凹槽、半透明托盘等拟物材质；桌面阴影体系 |
| | 禁用态（用专用颜色）、按下态（iOS 高亮 / Android ripple） |
| | 字号、控件高度、圆角、间距、时长等尺度 |

新增 token 时先判断它属于品牌层还是平台层：品牌层与 Web 保持同值，平台层以移动端准则为准。

### 色板如何生成

生成方式参考 Linear 的主题引擎：只输入少数值，其余全部由算法推导（`scripts/lib/color.mjs`）。

- **品牌色相**（`brand`）：取自 Web 的 `colors.stylex.ts` 浅色主题，强调 `hsl(220 82% 65%)`、危险 `hsl(356 72% 47%)`、成功 `hsl(151 55% 36%)`、警告 `hsl(32 90% 48%)`、合并 `hsl(261 69% 59%)`。
- **浅色模式**：品牌色原样作为第 9 档。
- **深色模式**：保持色相，提亮亮度（`keyAdjust`），和 iOS 系统色在深色下的适配方式一致；例如强调色 `#5D8DEF` → `#7CA8FF`。
- **中性色阶**：每个模式给定背景、文字两端和一条曲线（`neutral.curve`），曲线对齐 iOS 的系统灰层级。深色背景为纯黑，与 iOS 原生导航栏、Tab bar 融为一体。
- 每个色阶 12 档，角色参考 Radix：1 背景 · 2 次级背景 · 3 浅底 · 4 按下 · 5 选中 · 6–8 边框 · 9 关键色 · 10 关键色按下 · 11 文字 · 12 高对比文字。第 11 档用二分法求解，保证在第 3 档浅底上 ≥ 4.6:1。
- 彩色色阶额外生成 `solid` / `solidPressed`：关键色压暗到刚好能承载白字 4.5:1，用于填充按钮（浅色强调色本身配白字只有 3.3:1）。
- 分类标签色只固定色相，亮度和彩度跟随模式。
- 改品牌色：只改 `brand`；改模式风格：只改 `modes`，然后 build + check。

### 颜色角色

- **强调色**：`accent.default` 是平台 tint（开关、勾选、进度、选区、工具栏操作、运行中状态）；`accent.solid` 填充主按钮和发送按钮，配 `text.onAccent`。
- **危险**：`danger.solid` 填充破坏性按钮；`danger.default` 用于破坏性菜单项的文字和图标。
- **成功 / 警告 / 信息**：用于标记和浅底提示。
- **按下**：iOS 用 `fill.pressed` 高亮，Android 用系统 ripple。
- **禁用**：`fill.disabled` + `text.disabled`，不用整体透明度。
- **浅底类颜色一律半透明**：状态浅底（`*.subtle`）、标签底、diff 底、代码块底、次级填充、输入框和骨架屏。中性用黑 / 白叠加，彩色用对应色的第 9 档叠加，和 iOS 系统填充色的做法一致。这样它们放在列表、分组背景和卡片上都可见。不要把它们改回某个实色档：实色在深色卡片（`#1C1C1C`）或浅色分组背景（`#F2F2F2`）上会和表面同色。

### 语义色写法

```jsonc
"default": { "$value": "{accent.9}" },                                   // 两个模式同一档位
"scrim":   { "$value": { "light": "{black/40}", "dark": "{black/60}" } }    // 分模式 + 透明度
```

`check.mjs` 的两条无障碍规则（`tokens/contrast.json`）：

- **对比度**：半透明背景会分别合成到 `bg.base`、`bg.grouped`、`bg.raised` 上，取最差的结果判断。
- **表面分离度**：浅底类颜色合成到这三种表面上，OKLCH 亮度差都必须 ≥ 0.03，保证不会和所在表面融为一体。

为满足前者，生成器求解第 11 档文字色时，以较深的一档为参照（浅色第 5 档、深色第 6 档），近似"半透明浅底叠在最深表面上"的情况。

## 两端一致性边界

| 两端必须一致（token + 共享组件） | 跟随平台 |
|---|---|
| 颜色、字体、字号与行高、间距、圆角 | 导航栏、Tab bar 的材质与形态 |
| 列表行高、内边距、信息排布 | 转场动画、返回手势、触感反馈 |
| 图标（Lucide，统一 1.75 描边） | Sheet、菜单、Alert、选择器 |
| 状态图形、标签、头像、composer | 点按反馈（iOS 高亮 / Android ripple） |
| 消息、markdown、代码与 diff 渲染 | 系统权限弹窗、分享面板 |

## 目录

```
tokens/            唯一数据源（DTCG 风格 JSON）
  palette.json       原始色板（灰阶 + 生成式色系），仅被 color.json 引用
  color.json         语义色，每个都有 light / dark
  typography.json    字体族与字号体系
  dimension.json     间距、圆角、尺寸、断点、透明度、动效
  elevation.json     阴影层级
  contrast.json      对比度底线（check 会校验）
icons/manifest.json  语义图标名 -> Lucide 图标名
icons/lucide/        内置的 Lucide 原始 SVG（sync-icons 拉取）
fonts/font/          Inter + JetBrains Mono（Android res 目录结构，iOS 共用）
licenses/            OFL / ISC 许可证
scripts/             生成与校验脚本（Node 22，无依赖）
generated/           生成结果，勿手改（提交到仓库）
runtime/             手写的平台胶水代码（主题、文字样式修饰符、字体注册）
```

## 常用命令

```sh
node design-system/scripts/build.mjs        # tokens/icons -> generated/（离线）
node design-system/scripts/check.mjs        # 对比度、字体文件、generated 是否最新
node design-system/scripts/sync-icons.mjs   # 修改 manifest 后拉取 Lucide SVG（联网）
node design-system/scripts/fetch-fonts.mjs  # 重新拉取字体（联网）
```

修改 token 的流程：编辑 `tokens/*.json` → `build.mjs` → `check.mjs` → 在 `generated/preview.html` 中检查 → 两端编译。

## 命名对照

| Token | iOS | Android |
|---|---|---|
| `color: bg.base` | `LodyColor.bgBase` / `LodyUIColor.bgBase` | `LodyTheme.colors.bgBase` |
| `textStyle: body` | `.lodyTextStyle(.body)` | `LodyTheme.typography.body` |
| `spacing: lg` | `LodySpacing.lg` | `LodySpacing.lg` |
| `icon: inbox` | `LodyIcon.inbox.image` | `LodyIcons.Inbox` |
| `color: label.red.fg` | `LodyColor.labelRedFg` | `LodyTheme.colors.labelRedFg` |
| `color: accent.solid` | `LodyColor.accentSolid` | `LodyTheme.colors.accentSolid` |
| `opacity: pressed` | `LodyOpacity.pressed` | `LodyOpacity.pressed` |
| `motion: duration.base` | `LodyMotion.base`、`LodyMotion.standard()` | `LodyMotion.baseMillis`、`LodyMotion.standard` |
| `motion: spring.snappy` | `withAnimation(LodySpring.snappy)` | `LodySpring.snappy<Float>()` |
| `elevation: md` | `.lodyElevation(.md)` | `Modifier.dropShadow(shape, LodyElevations.md.shadow())` |

## 接入

两个 App 都直接引用本目录，修改 token 后跑 `build.mjs` 即对两端生效。

### iOS（`ios/`）

本目录是 SwiftPM 包 `LodyDesignSystem`（根目录 `Package.swift`）；图标和字体从包自己的 `Bundle.module` 加载。

- LodyKit 在 `ios/packages/LodyKit/Package.swift` 中依赖 `../../../design-system`；`LodyApplication.launch` 调用 `LodyFonts.register()`。
- 已接入：iPhone 首页与项目页的对话列表 `Sessions/ChatListView.swift`。iPad 侧边栏和其他页面仍为旧样式，逐步迁移。
- 示例工程 `examples/ios/project.yml` 以同样方式引用本包。

```swift
import LodyDesignSystem
Text("…").lodyTextStyle(.body).foregroundStyle(LodyColor.textPrimary)
LodyIcon.inbox.image.resizable().frame(width: LodyIconSize.md, height: LodyIconSize.md)
```

### Android（`android/`）

`android/app/build.gradle.kts` 把 `generated/android`、`runtime/android` 加入源码目录，把 `fonts/` 加入资源目录；生成代码使用 `app.lody.android.R`（`namespace = "app.lody.android"`），包名 `app.lody.android.designsystem`。

- 已接入：启动页 `home/HomeActivity`（登录 + 对话列表 `home/ChatListScreen`）。
- 根部包一层 `LodyTheme { … }`；图标用 `Icon(LodyIcons.Inbox, null, Modifier.size(LodyIconSize.md), tint = LodyTheme.colors.iconSecondary)`。

## 已知限制 / 下一步

- 两端接入代码尚未编译和运行验证，首次构建时需要检查。
- iOS 只有 iPhone 对话列表使用设计系统；iPad 侧边栏、会话页等仍是旧样式，需要逐页迁移。
- Android 当前阶段只读：对话列表不提供新建、行内菜单等写操作，点击会话暂无会话页。
