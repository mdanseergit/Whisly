import os, shutil
src = r"C:\Users\danse\OneDrive\document\Whisly\audit\splash_tint_candidates"
dst = r"C:\Users\danse\.gemini\antigravity\brain\4fa1a3f6-d72e-4901-819c-642b6e2407a2\splash_tint_candidates"
os.makedirs(dst, exist_ok=True)
for f in os.listdir(src):
    shutil.copy2(os.path.join(src, f), os.path.join(dst, f))
print("Copied", len(os.listdir(src)), "files.")

