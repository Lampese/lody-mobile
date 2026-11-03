import Foundation
let toolsJSON = """
[{"id":"tools-live","role":"assistant","status":"running","finished":false,"items":[
{"itemId":"cmd1","type":"tool_call","kind":"execute","title":"first\nsecond","status":"completed"},
{"itemId":"cmd2","type":"tool_call","kind":"execute","title":"second","status":"in_progress"},
{"itemId":"text","type":"text","text":"Update"},
{"itemId":"read","type":"tool_call","kind":"read","path":"a.swift","status":"in_progress"}]}]
"""
let toolsTranscript = ChatTranscript(entries: try JSONDecoder().decode([ChatEntry].self, from: Data(toolsJSON.replacingOccurrences(of: "first\nsecond", with: "first second").utf8)))
assert(toolsTranscript.rows().map(\.kind) == ["summary", "text", "summary"], "Live tools collapse without hiding intervening prose")
assert(toolsTranscript.rows(processEntryID: "tools-live", processStartID: "cmd1").map(\.itemID) == ["cmd1", "cmd2"])
assert(toolsTranscript.rows()[2].processStartID == "read", "Each tool group opens its own calls")
print("Live tool grouping passed")

