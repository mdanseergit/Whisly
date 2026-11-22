import os
from PIL import Image
import numpy as np

workspace = r"C:\Users\danse\OneDrive\document\Whisly"
res_root = os.path.join(workspace, r"whisly\app\src\main\res")
master_path = os.path.join(workspace, "scratch", "cleaned_full_master.png")

im_master = Image.open(master_path)
arr_master = np.array(im_master)

# Master true square crop: 1080 x 1080 centered at (cx=627, cy=615)
half = 540
cx_i, cy_i = 627, 615
cropped_master = arr_master[cy_i - half : cy_i + half, cx_i - half : cx_i + half]
master_tile = Image.fromarray(cropped_master, mode='RGBA')

# Density configurations:
# mdpi (1x): canvas 288, tile 132
# hdpi (1.5x): canvas 432, tile 198
# xhdpi (2x): canvas 576, tile 264
# xxhdpi (3x): canvas 864, tile 396
# xxxhdpi (4x): canvas 1152, tile 528
densities = [
    ("drawable-mdpi", 288, 132),
    ("drawable-hdpi", 432, 198),
    ("drawable-xhdpi", 576, 264),
    ("drawable-xxhdpi", 864, 396),
    ("drawable-xxxhdpi", 1152, 528),
]

for folder_name, canvas_sz, tile_sz in densities:
    folder_path = os.path.join(res_root, folder_name)
    os.makedirs(folder_path, exist_ok=True)
    
    # 1. Splash logo: canvas_sz x canvas_sz transparent canvas with tile_sz centered
    splash_img = Image.new("RGBA", (canvas_sz, canvas_sz), (0, 0, 0, 0))
    tile_resized = master_tile.resize((tile_sz, tile_sz), Image.Resampling.LANCZOS)
    offset = (canvas_sz - tile_sz) // 2
    splash_img.paste(tile_resized, (offset, offset), tile_resized)
    
    splash_path = os.path.join(folder_path, "whisly_splash_logo.png")
    splash_img.save(splash_path)
    print(f"Saved {splash_path} ({canvas_sz}x{canvas_sz}, tile {tile_sz}x{tile_sz})")
    
    # 2. Tile logo: tight tile image of tile_sz x tile_sz
    tile_path = os.path.join(folder_path, "whisly_tile_logo.png")
    tile_resized.save(tile_path)
    print(f"Saved {tile_path} ({tile_sz}x{tile_sz})")

# Also save default in drawable/
default_splash_path = os.path.join(res_root, "drawable", "whisly_splash_logo.png")
splash_xxxhdpi = Image.open(os.path.join(res_root, "drawable-xxxhdpi", "whisly_splash_logo.png"))
splash_xxxhdpi.save(default_splash_path)

default_tile_path = os.path.join(res_root, "drawable", "whisly_tile_logo.png")
tile_xxxhdpi = Image.open(os.path.join(res_root, "drawable-xxxhdpi", "whisly_tile_logo.png"))
tile_xxxhdpi.save(default_tile_path)

print("Default drawables saved successfully.")

