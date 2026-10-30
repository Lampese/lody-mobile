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
| 色板（原始值） | `palette.json` | 每个模式只给 Web 主题的关键色（背景、文字、强调、危险、成功、警告、PR 合并），按 Linear 式算法在 OKLCH 中生成 12 档色阶 |
| 语义色 | `color.json` | 背景层级、填充（按下 / 选中 / 禁用）、墨色（主按钮）、文字、图标、边框、强调色、危险 / 警告 / 成功 / 信息、会话状态、10 色分类标签、代码与语法高亮、diff、选区、遮罩、阴影色 |
| 字体 | `typography.json` | Inter + JetBrains Mono，16 档文字样式（display 到 codeSmall），字距按 Inter 公式推导 |
| 尺寸 | `dimension.json` | 间距、圆角、图标尺寸、描边、控件 / 行 / 头像尺寸、布局、窗口断点 |
| 数值 | `dimension.json` | 透明度（禁用、按下、弱化） |
| 动效 | `dimension.json` | 时长、缓动曲线、弹簧（SwiftUI duration/bounce，Compose 自动换算 stiffness/damping） |
| 阴影 | `elevation.json` | sm / md / lg 三级，颜色引用语义色，暗色自动加深 |
| 图标 | `icons/manifest.json` | 语义名 -> Lucide |

### 色板如何生成

生成方式参考 Linear 的主题引擎：每个模式只输入少数关键色，其余全部由算法推导（`scripts/lib/color.mjs`）。关键色原样取自 Web 端 token（`lody-oss/packages/ui/src/tokens/colors.stylex.ts`），保证移动端和 Web 端是同一个主题：

| 关键色 | 浅色（Lody Light） | 深色（Vesper） |
|---|---|---|
| background / foreground | `hsl(0 0% 100%)` / `hsl(0 0% 11%)` | `hsl(0 0% 6.3%)` / `hsl(0 0% 100%)` |
| accent / onAccent | `hsl(220 82% 65%)` / 白 | `hsl(27 100% 80%)` / 黑 |
| danger / onDanger | `hsl(356 72% 47%)` / 白 | `hsl(0 100% 75%)` / 黑 |
| success / warning | `hsl(151 55% 36%)` / `hsl(32 90% 48%)` | `hsl(151 60% 52%)` / `hsl(43 96% 56%)` |
| merged | `hsl(261 69% 59%)` | `hsl(262 89% 71%)` |

- 每个色阶 12 档，角色参考 Radix：1 背景 · 2 次级背景 · 3 浅底 · 4 按下 · 5 选中 · 6–8 边框 · **9 实色 = 关键色原值** · 10 实色按下 · 11 文字 · 12 高对比文字。
- 中性色阶在背景和文字之间按 `neutralCurve` 插值，曲线拟合了 Web 的 secondaryBackground / hover / selected / tertiaryLabel；深色模式用 `contrast` 指数（0.78）整体提亮，两套 Web 主题都能对上。
- 彩色色阶的第 11 档用二分法求解，保证在自己的第 3 档浅底上达到 4.6:1。
- 分类标签色（红橙黄绿青蓝靛紫粉）只有色相是固定的，亮度和彩度跟随模式，所以会自然融入主题。
- 换主题 / 改品牌色：只改 `palette.json` 的 `keys`，跑 build + check。

### 颜色角色（与 Web 规则一致）

- **墨色（`ink.*`）填充主按钮和发送按钮**；强调色不做按钮底色，只用于焦点、链接、开关 / 勾选、进度、选区和"运行中"状态。
- 成功 / 警告只用于标记和浅底，不填充可按的控件；危险既表示破坏性操作，也表示失败结果。
- 禁用态 = 整个控件 45% 透明度，不单独定义颜色。
- 输入框底色（`bg.sunken`）和托盘（`fill.secondary`）用半透明，放在任何表面上都是同一层级差。

### 语义色写法

```jsonc
"default": { "$value": "{accent.9}" },                                   // 两个模式同一档位
"sunken":  { "$value": { "light": "{key.foreground/5}", "dark": "{black/28}" } } // 分模式 + 透明度
```

对比度检查会先把半透明背景合成到 `bg.base` 上再计算。

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
| `color: ink.default` | `LodyColor.inkDefault` | `LodyTheme.colors.inkDefault` |
| `opacity: disabled` | `LodyOpacity.disabled` | `LodyOpacity.disabled` |
| `motion: duration.base` | `LodyMotion.base`、`LodyMotion.standard()` | `LodyMotion.baseMillis`、`LodyMotion.standard` |
| `motion: spring.snappy` | `withAnimation(LodySpring.snappy)` | `LodySpring.snappy<Float>()` |
| `elevation: md` | `.lodyElevation(.md)` | `Modifier.dropShadow(shape, LodyElevations.md.shadow())` |

## 接入

### iOS（`ios/`）

1. 将 `generated/ios/LodyDesignSystem/*.swift`、`runtime/ios/*.swift`、`generated/ios/LodyIcons.xcassets`、`fonts/font/*.ttf` 加入 App target（XcodeGen 中以目录引用的方式添加，保持与本目录同步）。
2. 启动时调用：

```swift
LodyFonts.register()
LodyAppearance.apply()
```

3. 文本使用 `Text("…").lodyTextStyle(.body).foregroundStyle(LodyColor.textPrimary)`；图标使用 `LodyIcon.inbox.image.resizable().frame(width: LodyIconSize.md, height: LodyIconSize.md)`。

### Android（`android/`）

1. `app/build.gradle.kts` 中把本目录接入源码与资源（路径相对于 `android/app`；AGP 9 下请确认 source set API）：

```kotlin
val lodyDesignSystem = file("../../design-system")
android {
    sourceSets["main"].java.srcDirs(
        lodyDesignSystem.resolve("generated/android"),
        lodyDesignSystem.resolve("runtime/android"),
    )
    sourceSets["main"].res.srcDir(lodyDesignSystem.resolve("fonts"))
}
dependencies {
    implementation(libs.compose.material3)   // 目前只有 debugImplementation
    implementation(libs.compose.foundation)
}
```

2. 生成代码使用 `app.lody.android.R`（`namespace = "app.lody.android"`），包名为 `app.lody.android.designsystem`；修改方式见 `scripts/lib/emit-kotlin.mjs` 顶部常量。
3. 根部包一层 `LodyTheme { … }`；图标用 `Icon(LodyIcons.Inbox, null, Modifier.size(LodyIconSize.md), tint = LodyTheme.colors.iconSecondary)`。

## 已知限制 / 下一步

- 当前环境没有 Xcode 与 Android SDK，生成的 Swift / Kotlin 尚未在两端编译过，首次接入时需要验证。
- 尚未包含代码语法高亮配色：计划从桌面端 `--syntax-*` 派生。
- 下一步：在两端各做一个组件 gallery 页面（`ListRow`、`SectionHeader`、`StatusIcon`、`Chip`、`Composer`、`EmptyState`），并用截图并排对比两端。
