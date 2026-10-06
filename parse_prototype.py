import re
import json

with open('prototype.html', 'r', encoding='utf-8') as f:
    html = f.read()

# We need to extract the JS object literals from the prototype.html
# Let's try to extract the arrays: movies, series, channels
def extract_js_array(var_name):
    pattern = rf'const {var_name}\s*=\s*(\[.*?\]);\n'
    match = re.search(pattern, html, re.DOTALL)
    if match:
        arr_str = match.group(1)
        # very basic json conversion: replace single quotes with double quotes
        # remove trailing commas, fix unquoted keys
        # We can just write it to a .js file and evaluate it with node to output JSON
        return arr_str
    return None

import subprocess

js_code = "const fs = require('fs');\n"
js_code += "const vjs = { 'A': 'VJ A', 'B': 'VJ B' };\n" # mock missing vars
js_code += "const videoBucket = 'https://storage.googleapis.com/gtv-videos-bucket/sample/';\n"
js_code += "const pools = {\n"
js_code += "  long: [\n"
js_code += "    videoBucket + 'BigBuckBunny.mp4',\n"
js_code += "    videoBucket + 'ElephantsDream.mp4',\n"
js_code += "    videoBucket + 'Sintel.mp4',\n"
js_code += "    videoBucket + 'TearsOfSteel.mp4',\n"
js_code += "    videoBucket + 'SubaruOutbackOnStreetAndDirt.mp4',\n"
js_code += "    videoBucket + 'VolkswagenGTIReview.mp4',\n"
js_code += "  ],\n"
js_code += "  short: [\n"
js_code += "    'https://mdn.github.io/learning-area/html/multimedia-and-embedding/video-and-audio-content/rabbit320.mp4',\n"
js_code += "    'https://media.w3.org/2010/05/sintel/trailer.mp4',\n"
js_code += "    videoBucket + 'ForBiggerBlazes.mp4',\n"
js_code += "    videoBucket + 'ForBiggerEscapes.mp4',\n"
js_code += "    videoBucket + 'ForBiggerFun.mp4',\n"
js_code += "    videoBucket + 'ForBiggerJoyrides.mp4',\n"
js_code += "    videoBucket + 'ForBiggerMeltdowns.mp4',\n"
js_code += "  ]\n"
js_code += "};\n"
js_code += "let longIndex = 0; function getLongVideo() { const v = pools.long[longIndex]; longIndex = (longIndex + 1) % pools.long.length; return v; }\n"
js_code += "let shortIndex = 0; function getShortVideo() { const v = pools.short[shortIndex]; shortIndex = (shortIndex + 1) % pools.short.length; return v; }\n"

movies_str = extract_js_array('movies')
if movies_str:
    js_code += f"const movies = {movies_str};\n"
    js_code += "fs.writeFileSync('movies.json', JSON.stringify(movies, null, 2));\n"

series_str = extract_js_array('series')
if series_str:
    js_code += f"const series = {series_str};\n"
    js_code += "fs.writeFileSync('series.json', JSON.stringify(series, null, 2));\n"

channels_str = extract_js_array('channels')
if channels_str:
    js_code += f"const channels = {channels_str};\n"
    js_code += "fs.writeFileSync('channels.json', JSON.stringify(channels, null, 2));\n"

with open('dump.js', 'w') as f:
    f.write(js_code)
