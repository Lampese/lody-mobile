// swift-tools-version: 6.0
// iOS consumers (ios/packages/LodyKit, examples/ios) depend on this package; Android
// adds generated/android, runtime/android and fonts/ as source and res dirs instead.
import PackageDescription

let package = Package(
  name: "LodyDesignSystem",
  platforms: [.iOS("26.0")],
  products: [.library(name: "LodyDesignSystem", targets: ["LodyDesignSystem"])],
  targets: [
    .target(
      name: "LodyDesignSystem",
      path: ".",
      exclude: [
        "AGENTS.md", "CLAUDE.md", "README.md", "examples", "generated/android", "generated/preview.html",
        "icons", "licenses", "runtime/android", "scripts", "tokens",
      ],
      sources: ["generated/ios/LodyDesignSystem", "runtime/ios"],
      resources: [.process("generated/ios/LodyIcons.xcassets"), .process("fonts/font")]
    ),
  ]
)
