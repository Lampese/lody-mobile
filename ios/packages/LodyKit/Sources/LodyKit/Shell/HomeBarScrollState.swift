import Foundation

/// Hysteresis keeps the compact bar stable until the list actually reaches its top.
struct HomeBarScrollState {
  private(set) var collapsed = false

  mutating func update(offset: Double, scrolling: Bool, threshold: Double) {
    if offset <= 1 { collapsed = false }
    else if scrolling && offset > threshold { collapsed = true }
  }
}
