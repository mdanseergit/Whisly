import os
import shutil
from PIL import Image
import numpy as np

src_dir = r"C:\Users\danse\OneDrive\document\Whisly\audit\splash_preview"
dst_dir = r"C:\Users\danse\.gemini\antigravity\brain\4fa1a3f6-d72e-4901-819c-642b6e2407a2\splash_preview"
os.makedirs(dst_dir, exist_ok=True)

for f in os.listdir(src_dir):
    shutil.copy2(os.path.join(src_dir, f), os.path.join(dst_dir, f))
print(f"Copied {len(os.listdir(src_dir))} files to {dst_dir}")

# Contrast Analysis on Light Theme:
# Let's inspect the contrast between the tile surface and the light background #FFF9F5:
# Light background: #FFF9F5 -> RGB [255, 249, 245]
# Relative luminance: L = 0.2126 * R + 0.7152 * G + 0.0722 * B
def rel_lum(rgb):
    # sRGB to linear
    rgb_lin = [c / 255.0 for c in rgb]
    rgb_lin = [(c / 12.92) if c <= 0.04045 else (((c + 0.055) / 1.055) ** 2.4) for c in rgb_lin]
    return 0.2126 * rgb_lin[0] + 0.7152 * rgb_lin[1] + 0.0722 * rgb_lin[2]

l_bg = rel_lum([255, 249, 245])

# Sample tile colors from 03_full_on_light_bg.png:
im = Image.open(os.path.join(src_dir, "03_full_on_light_bg.png"))
arr = np.array(im)

# Tile top surface: y=200, x=540
tile_top = arr[200, 540]
l_tile_top = rel_lum(tile_top)

# Tile center (above ribbon): y=350, x=540
tile_center = arr[350, 540]
l_tile_center = rel_lum(tile_center)

# Tile bottom (subtle gradient/shadow): y=850, x=540
tile_bottom = arr[850, 540]
l_tile_bottom = rel_lum(tile_bottom)

# Tile rim highlight: y=120, x=540
tile_rim = arr[120, 540]
l_tile_rim = rel_lum(tile_rim)

def contrast_ratio(l1, l2):
    bright = max(l1, l2)
    dark = min(l1, l2)
    return (bright + 0.05) / (dark + 0.05)

print("\n--- Contrast Analysis on Light Background (#FFF9F5) ---")
print(f"Background RGB: [255, 249, 245], Lum: {l_bg:.4f}")
print(f"Tile Top RGB: {tile_top.tolist()}, Lum: {l_tile_top:.4f}, Contrast Ratio: {contrast_ratio(l_bg, l_tile_top):.2f}:1")
print(f"Tile Center RGB: {tile_center.tolist()}, Lum: {l_tile_center:.4f}, Contrast Ratio: {contrast_ratio(l_bg, l_tile_center):.2f}:1")
print(f"Tile Bottom RGB: {tile_bottom.tolist()}, Lum: {l_tile_bottom:.4f}, Contrast Ratio: {contrast_ratio(l_bg, l_tile_bottom):.2f}:1")
print(f"Tile Rim RGB: {tile_rim.tolist()}, Lum: {l_tile_rim:.4f}, Contrast Ratio: {contrast_ratio(l_bg, l_tile_rim):.2f}:1")


