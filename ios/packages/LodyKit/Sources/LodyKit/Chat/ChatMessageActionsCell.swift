import UIKit

/// Stable, visible touch targets below each message. Copy feedback belongs to the tapped row.
final class ChatMessageActionsCell: UICollectionViewCell {
  let copyButton = UIButton(type: .system)
  let editButton = UIButton(type: .system)
  let detailsButton = UIButton(type: .system)
  let forkButton = UIButton(type: .system)
  let actionButton = UIButton(type: .system)
  private let label = UILabel()
  private let actions = UIStackView()
  private var identity = ""
  private var user = false
  var entryID: String { identity }
  var canEdit: (() -> Bool)?
  var onCopy: (() -> Void)?

  override init(frame: CGRect) {
    super.init(frame: frame)
    label.font = .preferredFont(forTextStyle: .footnote)
    label.textColor = .secondaryLabel
    label.lineBreakMode = .byTruncatingTail
    contentView.addSubview(label)
    actions.axis = .horizontal
    actions.spacing = 0
    for (button, symbol, key) in [
      (copyButton, "doc.on.doc", "native.chat.message.copy"),
      (editButton, "pencil", "native.chat.editAction"),
      (detailsButton, "info.circle", "message.details.title"),
      (forkButton, "arrow.triangle.branch", "message.fork.title"),
      (actionButton, "ellipsis", "native.chat.message.actions"),
    ] {
      button.setImage(UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 15)), for: .normal)
      button.tintColor = .secondaryLabel
      button.accessibilityLabel = String(localized: String.LocalizationValue(key), bundle: .lody)
      button.widthAnchor.constraint(equalToConstant: 44).isActive = true
      button.heightAnchor.constraint(equalToConstant: 44).isActive = true
      actions.addArrangedSubview(button)
    }
    actionButton.showsMenuAsPrimaryAction = true
    copyButton.addAction(UIAction { [weak self] _ in
      guard let self else { return }
      self.onCopy?()
      let id = self.identity
      self.copyButton.setImage(UIImage(systemName: "checkmark", withConfiguration: UIImage.SymbolConfiguration(pointSize: 15)), for: .normal)
      Task { [weak self] in
        try? await Task.sleep(for: .seconds(1.6))
        guard let self, self.identity == id else { return }
        self.copyButton.setImage(UIImage(systemName: "doc.on.doc", withConfiguration: UIImage.SymbolConfiguration(pointSize: 15)), for: .normal)
      }
    }, for: .touchUpInside)
    contentView.addSubview(actions)
  }
  required init?(coder: NSCoder) { fatalError() }
  func configure(_ row: ChatRow, copyable: Bool) {
    identity = row.entryID
    user = row.kind == "userActions"
    label.text = row.text
    label.isHidden = user
    copyButton.isEnabled = copyable
    copyButton.setImage(UIImage(systemName: "doc.on.doc", withConfiguration: UIImage.SymbolConfiguration(pointSize: 15)), for: .normal)
    detailsButton.isHidden = user
    forkButton.isHidden = user
    actionButton.isHidden = user
    updateEdit()
    for (button, suffix) in [(copyButton,"copy"), (editButton,"edit"), (detailsButton,"details"), (forkButton,"fork"), (actionButton,"actions")] {
      button.accessibilityIdentifier = row.id + ":" + suffix
    }
    setNeedsLayout()
  }
  func updateEdit() { editButton.isHidden = !user || canEdit?() != true; setNeedsLayout() }
  override func layoutSubviews() {
    super.layoutSubviews()
    let width = CGFloat(actions.arrangedSubviews.filter { !$0.isHidden }.count) * 44
    label.frame = CGRect(x: 0, y: 0, width: bounds.width, height: 24)
    actions.frame = CGRect(x: user ? max(0, bounds.width - width - ChatCell.avatarSlot) : 0, y: user ? 0 : 24, width: width, height: 44)
  }
}
