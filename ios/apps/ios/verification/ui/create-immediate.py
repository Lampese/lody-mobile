"""Run on --ui-verify --ui-verify-home --ui-verify-mentions, never a live account.
The pushed creation form must reveal its local conversation without a server ACK.
"""
import sys
from driver import UI
import catalog

ui = UI(*sys.argv[1:])
ui.element('ui-verify-ready')
ui.axe('tap', '--label', catalog.text('create.title'), '--post-delay', '.5')
ui.type_into('create-session-input', 'Immediate navigation test')
ui.capture('before')
ui.axe('tap', '--id', 'session-send', '--tap-style', 'physical', '--post-delay', '.7')
ui.element('chat-navigation-title')
ui.element('session-input')
items = ui.state()
assert not any(i.get('AXUniqueId') == 'create-session-input' for i in items), 'Creation is still covering the destination'
assert any('Immediate navigation test' in (i.get('AXLabel') or '') for i in items), 'Local first turn is missing'
ui.capture('after')
