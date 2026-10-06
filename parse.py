import re
import json

with open('prototype.html', 'r') as f:
    text = f.read()

def extract_obj(name):
    m = re.search(r'const ' + name + r' = (\[.*?\]);\n', text, re.DOTALL)
    if m:
        try:
            # We can't use simple json.loads on js objects due to unquoted keys
            # so I will just dump the string and fix it later. Or wait, the prototype data uses quoted keys or unquoted?
            return m.group(1)
        except Exception:
            pass
    return None

import ast
def convert_js_to_json(js_str):
    import dukpy
    return dukpy.evaljs(js_str)
    
print("ok")
