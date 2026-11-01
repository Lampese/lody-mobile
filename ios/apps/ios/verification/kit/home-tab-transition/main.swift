import UIKit

@MainActor
final class Context: NSObject, UIViewControllerContextTransitioning {
  let containerView = UIView(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
  let from = UIViewController()
  let to = UIViewController()
  var isAnimated = true
  var isInteractive = false
  var transitionWasCancelled = false
  var presentationStyle: UIModalPresentationStyle = .none
  var targetTransform: CGAffineTransform = .identity
  var completed: Bool?
  override init() { super.init(); from.view.frame = containerView.bounds; containerView.addSubview(from.view) }
  func updateInteractiveTransition(_ percentComplete: CGFloat) {}
  func finishInteractiveTransition() {}
  func cancelInteractiveTransition() {}
  func pauseInteractiveTransition() {}
  func completeTransition(_ didComplete: Bool) { completed = didComplete }
  func viewController(forKey key: UITransitionContextViewControllerKey) -> UIViewController? { key == .from ? from : to }
  func view(forKey key: UITransitionContextViewKey) -> UIView? { key == .from ? from.view : to.view }
  func initialFrame(for vc: UIViewController) -> CGRect { containerView.bounds }
  func finalFrame(for vc: UIViewController) -> CGRect { containerView.bounds }
}

// Drive the real UIKit animator, including a cancelled transition. Neither page may retain
// a translated frame/alpha: cached roots must be usable on the next selection.
for direction: CGFloat in [-1, 1] {
  for cancelled in [false, true] {
    let context = Context()
    context.transitionWasCancelled = cancelled
    let animator = HomeTabSlideTransition(direction: direction, duration: 0)
    animator.animateTransition(using: context)
    let deadline = Date().addingTimeInterval(2)
    while context.completed == nil && Date() < deadline { RunLoop.main.run(until: Date().addingTimeInterval(0.01)) }
    precondition(context.completed == !cancelled)
    precondition(context.from.view.transform == .identity && context.to.view.transform == .identity)
    precondition(context.to.view.frame == context.containerView.bounds)
    precondition(cancelled ? context.to.view.superview == nil : context.to.view.superview === context.containerView)
  }
}
print("PASS: tab transitions finish or cancel cleanly in both directions; cached pages retain their geometry")
