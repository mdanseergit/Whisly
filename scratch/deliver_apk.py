import os
import shutil
import hashlib

apk_src = r"C:\Users\danse\AppData\Local\Temp\whisly_build\app\outputs\apk\debug\app-debug.apk"
workspace = r"C:\Users\danse\OneDrive\document\Whisly"
deliv_dir = os.path.join(workspace, "deliverables")
os.makedirs(deliv_dir, exist_ok=True)

apk_dst1 = os.path.join(deliv_dir, "whisly-v1.0.1-splash-fixed-debug.apk")
apk_dst2 = os.path.join(deliv_dir, "whisly-1.0.0-debug-preview.apk")
apk_root = os.path.join(workspace, "Whisly-v1.0.1-debug.apk")

shutil.copy2(apk_src, apk_dst1)
shutil.copy2(apk_src, apk_dst2)
shutil.copy2(apk_src, apk_root)

with open(apk_dst1, "rb") as f:
    sha = hashlib.sha256(f.read()).hexdigest()

sz = os.path.getsize(apk_dst1)
print(f"Delivered APK: {apk_dst1}")
print(f"Root APK: {apk_root}")
print(f"Size: {sz / (1024*1024):.2f} MB ({sz:,} bytes)")
print(f"SHA-256: {sha}")
