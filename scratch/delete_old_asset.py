import os

workspace = r"C:\Users\danse\OneDrive\document\Whisly\whisly"
matches = []

for root, dirs, files in os.walk(workspace):
    if "build" in root or ".gradle" in root:
        continue
    for f in files:
        p = os.path.join(root, f)
        try:
            with open(p, "r", encoding="utf-8", errors="ignore") as file:
                content = file.read()
                if "whisly_logo_transparent" in content:
                    matches.append((os.path.relpath(p, workspace), f))
        except Exception:
            pass

print(f"Remaining references to whisly_logo_transparent: {len(matches)}")
for rel, f in matches:
    print(f"  {rel}")

old_file = os.path.join(workspace, r"app\src\main\res\drawable\whisly_logo_transparent.png")
if os.path.exists(old_file):
    os.remove(old_file)
    print("Deleted whisly_logo_transparent.png successfully.")
else:
    print("whisly_logo_transparent.png already deleted.")

