import ChatKitCore
import Foundation

let json = """
[{"id":"reply","role":"assistant","status":"running","finished":false,"items":[
{"itemId":"intro","type":"thought","text":"先检查"},
{"itemId":"tool","type":"tool_call","title":"读取文件","status":"completed"},
{"itemId":"answer","type":"text","text":"最终答案"}]}]
"""
// Opening a running conversation can deliver an empty history before its first snapshot.
var openingHistory = ChatStream()
openingHistory.receive([], animate: true)
let openingEntries = try JSONDecoder().decode([ChatEntry].self, from: Data(json.utf8))
openingHistory.receive(openingEntries, animate: true, initialSnapshot: true, at: 0)
assert(!openingHistory.hasPending, "First visible history must not replay after an empty snapshot")
assert(openingHistory.presentation[0].items[2].text == "最终答案", "Initial history is complete before a second frame")
var openingGrowth = openingEntries
openingGrowth[0].items[2].text = "最终答案继续输出"
openingHistory.receive(openingGrowth, animate: true, at: 0.1)
assert(openingHistory.hasPending, "New text after initial history must still animate")
openingHistory.finish()
assert(openingHistory.presentation[0].items[2].text == "最终答案继续输出")

print("PASS: initial history is complete; subsequent deltas still animate")
