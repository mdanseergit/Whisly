import os

workspace = r"C:\Users\danse\OneDrive\document\Whisly\whisly\app\src\test"

for root, dirs, files in os.walk(workspace):
    for f in files:
        if f.endswith(".kt"):
            p = os.path.join(root, f)
            with open(p, "r", encoding="utf-8", errors="ignore") as file:
                if "startInTranslateDirectly" in file.read():
                    print("Found in test:", f)

