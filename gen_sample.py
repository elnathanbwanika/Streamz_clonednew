import json, textwrap

with open('movies.json') as f: movies = json.load(f)
with open('series.json') as f: series = json.load(f)
with open('channels.json') as f: channels = json.load(f)

out = ["package com.linkstreamz.app", "", "// Bundled sample data shown until the first successful server fetch.", ""]

# SAMPLE_MOVIES
out.append("internal val SAMPLE_MOVIES = listOf(")
for m in movies:
    vj = f'"{m["vj"]}"' if m.get("vj") else "null"
    tr = "true" if m.get("translated") else "false"
    out.append(f'    Movie("{m["id"]}", "{m["title"]}", "{m["genre"]}", {m["year"]}, {vj}, {tr}, "{m["thumb"]}", "{m["video"]}", "{m["dur"]}"),')
out.append(")")
out.append("")

# SAMPLE_SERIES
out.append("internal val SAMPLE_SERIES = listOf(")
for s in series:
    vj = f'"{s["vj"]}"' if s.get("vj") else "null"
    tr = "true" if s.get("translated") else "false"
    out.append(f'    Series("{s["id"]}", "{s["title"]}", "{s["genre"]}", {s["year"]}, {vj}, {tr}, "{s["thumb"]}", listOf(')
    for se in s["seasons"]:
        out.append(f'        Season({se["number"]}, listOf(')
        for ep in se["episodes"]:
            out.append(f'            Episode({ep["n"]}, "{ep["title"]}", "{ep["thumb"]}", "{ep["video"]}", "{ep["dur"]}"),')
        out.append("        )),")
    out.append("    )),")
out.append(")")
out.append("")

# SAMPLE_CHANNELS
out.append("internal val SAMPLE_CHANNELS = listOf(")
for c in channels:
    out.append(f'    Channel("{c["id"]}", "{c["title"]}", "{c["cat"]}", "{c["country"]}", "{c["initials"]}", "{c["c1"]}", "{c["c2"]}", "{c["program"]}", "{c["video"]}"),')
out.append(")")

with open('app/src/main/java/com/linkstreamz/app/SampleData.kt', 'w') as f:
    f.write('\n'.join(out) + '\n')
print("done")
