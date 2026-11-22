import os
from PIL import Image, ImageDraw
import numpy as np

workspace = r"C:\Users\danse\OneDrive\document\Whisly"
out_dir = os.path.join(workspace, "audit", "splash_tint_candidates")

# Simulate launcher icon on light wallpaper #FFFFFF
# Standard launcher icon is 108x108 dp (e.g. 432x432 px at xxxhdpi)
# Masked by a squircle or circle of 72dp (288 px)
icon_size = 432
mask_diam = 288
mask_radius = mask_diam // 2
center = icon_size // 2

# Background is #FFF9F5
icon_bg = Image.new("RGBA", (icon_size, icon_size), (255, 249, 245, 255))
# Foreground ribbon: we scale the ribbon or tile
master_path = os.path.join(workspace, "scratch", "cleaned_full_master.png")
im_master = Image.open(master_path)
arr_master = np.array(im_master)
half = 540
cx_i, cy_i = 627, 615
cropped_master = arr_master[cy_i - half : cy_i + half, cx_i - half : cx_i + half]
tile_rgba = Image.fromarray(cropped_master, mode='RGBA')

# 66dp foreground safe zone inside 108dp canvas = 66/108 * 432 = 264 px
fg_size = 264
fg_scaled = tile_rgba.resize((fg_size, fg_size), Image.Resampling.LANCZOS)
pos = (icon_size - fg_size) // 2
icon_bg.paste(fg_scaled, (pos, pos), fg_scaled)

# Create circular mask for adaptive icon
mask = Image.new("L", (icon_size, icon_size), 0)
draw_mask = ImageDraw.Draw(mask)
draw_mask.ellipse([center - mask_radius, center - mask_radius, center + mask_radius, center + mask_radius], fill=255)

# Place on white wallpaper
wallpaper = Image.new("RGB", (icon_size + 80, icon_size + 80), (255, 255, 255))
# Paste adaptive icon with shadow
wallpaper.paste(icon_bg, (40, 40), mask)

# Save
launcher_sim_path = os.path.join(out_dir, "09_launcher_icon_light_wallpaper.png")
wallpaper.save(launcher_sim_path)
print("Saved launcher simulation to:", launcher_sim_path)


