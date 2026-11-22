import os

workspace = r"C:\Users\danse\OneDrive\document\Whisly\whisly\app\src\main\java"

for root, dirs, files in os.walk(workspace):
    for f in files:
        if f.endswith(".kt"):
            p = os.path.join(root, f)
            with open(p, "r", encoding="utf-8", errors="ignore") as file:
                if "object WhislyTokens" in file.read():
                    print("WhislyTokens in:", p)

