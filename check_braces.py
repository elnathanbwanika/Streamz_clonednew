import sys

def check(file_path):
    with open(file_path) as f:
        lines = f.readlines()
        
    stack = []
    for i, line in enumerate(lines):
        for j, char in enumerate(line):
            if char == '{':
                stack.append((i+1, j+1))
            elif char == '}':
                if stack:
                    stack.pop()
                else:
                    print(f"Unmatched }} at line {i+1}, col {j+1}")
                    
    for (i, j) in stack:
        print(f"Unmatched {{ at line {i}, col {j}")

check("app/src/main/java/com/linkstreamz/app/PlayerScreen.kt")
