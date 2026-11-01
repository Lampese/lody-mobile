import UIKit

/// Only root-to-root tab changes use this animator; session pushes keep the shell animator.
@MainActor
final class HomeTabSlideTransition: NSObject, UIViewControllerAnimatedTransitioning {
  private let direction: CGFloat
  private let duration: TimeInterval

  init(direction: CGFloat, duration: TimeInterval) {
    self.direction = direction
    self.duration = duration
  }

  func transitionDuration(using transitionContext: UIViewControllerContextTransitioning?) -> TimeInterval { duration }

  func animateTransition(using context: UIViewControllerContextTransitioning) {
    guard let from = context.view(forKey: .from), let to = context.view(forKey: .to),
          let destination = context.viewController(forKey: .to) else {
      context.completeTransition(false)
      return
    }
    let container = context.containerView
    to.frame = context.finalFrame(for: destination)
    let distance = container.bounds.width * direction
    to.transform = CGAffineTransform(translationX: distance, y: 0)
    container.addSubview(to)
    UIView.animate(withDuration: duration, delay: 0, options: [.curveEaseInOut, .beginFromCurrentState]) {
      from.transform = CGAffineTransform(translationX: -distance, y: 0)
      to.transform = .identity
    } completion: { _ in
      from.transform = .identity
      to.transform = .identity
      if context.transitionWasCancelled { to.removeFromSuperview() }
      context.completeTransition(!context.transitionWasCancelled)
    }
  }
}

/// Forward shell navigation ownership while adding tab-only transitions and bar visibility.
@MainActor
final class HomeTabNavigationDelegate: NSObject, UINavigationControllerDelegate {
  weak var forwarded: (any UINavigationControllerDelegate)?
  var index: (UIViewController) -> Int? = { _ in nil }
  var willShow: (UIViewController, Bool) -> Void = { _, _ in }
  var didShow: (UIViewController) -> Void = { _ in }
  var duration: TimeInterval = 0

  func navigationController(_ navigationController: UINavigationController, willShow viewController: UIViewController, animated: Bool) {
    forwarded?.navigationController?(navigationController, willShow: viewController, animated: animated)
    willShow(viewController, animated)
  }

  func navigationController(_ navigationController: UINavigationController, didShow viewController: UIViewController, animated: Bool) {
    forwarded?.navigationController?(navigationController, didShow: viewController, animated: animated)
    didShow(viewController)
  }

  func navigationController(_ navigationController: UINavigationController, animationControllerFor operation: UINavigationController.Operation,
                            from fromVC: UIViewController, to toVC: UIViewController) -> (any UIViewControllerAnimatedTransitioning)? {
    guard let from = index(fromVC), let to = index(toVC), from != to,
          !UIAccessibility.isReduceMotionEnabled else {
      return forwarded?.navigationController?(navigationController, animationControllerFor: operation, from: fromVC, to: toVC)
    }
    let rtl = navigationController.view.effectiveUserInterfaceLayoutDirection == .rightToLeft
    let direction: CGFloat = (to > from) != rtl ? 1 : -1
    return HomeTabSlideTransition(direction: direction, duration: duration)
  }
}
