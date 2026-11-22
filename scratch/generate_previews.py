import os
from PIL import Image, ImageDraw, ImageFont
import numpy as np
from scipy import ndimage

workspace = r"C:\Users\danse\OneDrive\document\Whisly"
src_path = os.path.join(workspace, "whisly logo.png")
im = Image.open(src_path)
arr = np.array(im, dtype=np.float64)
h, w = arr.shape[:2]

# --- STEP 1: ISOLATE TILE AND BUILD SHAPE-BASED ALPHA ---
max_c = np.max(arr, axis=2)
bg_seed = max_c <= 6
lbl, num = ndimage.label(bg_seed)
border_lbls = set(lbl[0, :]).union(set(lbl[-1, :])).union(set(lbl[:, 0])).union(set(lbl[:, -1]))
if 0 in border_lbls:
    border_lbls.remove(0)
outside_bg = np.isin(lbl, list(border_lbls))

tile_solid = ndimage.binary_fill_holes(~outside_bg)

# Core where alpha is guaranteed 1.0 (unaffected by border)
core = ndimage.binary_erosion(tile_solid, iterations=4)
edge_zone = tile_solid & ~core

# Nearest core pixel for foreground color reference
_, nearest_indices = ndimage.distance_transform_edt(~core, return_indices=True)
core_r = arr[..., 0][nearest_indices[0], nearest_indices[1]]
core_g = arr[..., 1][nearest_indices[0], nearest_indices[1]]
core_b = arr[..., 2][nearest_indices[0], nearest_indices[1]]
fg_ref = np.dstack([core_r, core_g, core_b])

lum_obs = 0.299 * arr[..., 0] + 0.587 * arr[..., 1] + 0.114 * arr[..., 2]
lum_fg = 0.299 * fg_ref[..., 0] + 0.587 * fg_ref[..., 1] + 0.114 * fg_ref[..., 2]
lum_fg = np.maximum(lum_fg, 120.0)

alpha = np.zeros((h, w), dtype=np.float64)
alpha[core] = 1.0

# Subpixel smooth antialiasing in the transition zone
alpha_edge = lum_obs / lum_fg
alpha[edge_zone] = np.clip(alpha_edge[edge_zone], 0.0, 1.0)

# De-matte / un-premultiply against black
dematted = np.copy(arr)
for c in range(3):
    dematted[edge_zone, c] = np.where(
        alpha[edge_zone] > 0.05,
        np.clip(arr[edge_zone, c] / alpha[edge_zone], 0.0, 255.0),
        fg_ref[edge_zone, c]
    )

# Fidelity check: core pixels must be 100% bit-exact original
diff_core = np.max(np.abs(dematted[core] - arr[core]))
assert diff_core == 0.0, f"Core fidelity failure: {diff_core}"

# Tile bounding box and center
y_coords, x_coords = np.where(tile_solid)
y_min, y_max = y_coords.min(), y_coords.max()
x_min, x_max = x_coords.min(), x_coords.max()
tile_w = x_max - x_min + 1
tile_h = y_max - y_min + 1
cx = (x_min + x_max) / 2.0
cy = (y_min + y_max) / 2.0

print(f"Tile bbox: x=[{x_min}, {x_max}] (w={tile_w}), y=[{y_min}, {y_max}] (h={tile_h})")
print(f"Center: cx={cx:.2f}, cy={cy:.2f}")

# --- STEP 2: CROP TO TRUE SQUARE CENTERED ON TILE ---
# We take a square crop of size 1080 x 1080 centered at (cx, cy):
# Half-size: 540 px.
crop_size = 1080
half = crop_size // 2 # 540
cx_i = int(round(cx))
cy_i = int(round(cy))

x0_c = cx_i - half
x1_c = x0_c + crop_size
y0_c = cy_i - half
y1_c = y0_c + crop_size

print(f"Square crop: x=[{x0_c}, {x1_c}), y=[{y0_c}, {y1_c}) (size: {crop_size}x{crop_size})")

cropped_dematted = dematted[y0_c:y1_c, x0_c:x1_c]
cropped_alpha = alpha[y0_c:y1_c, x0_c:x1_c]
cropped_arr = arr[y0_c:y1_c, x0_c:x1_c]

cropped_rgba = np.dstack([cropped_dematted, (cropped_alpha * 255.0)]).astype(np.uint8)
master_tile_image = Image.fromarray(cropped_rgba, mode='RGBA')

out_dir = os.path.join(workspace, "audit", "splash_preview")
os.makedirs(out_dir, exist_ok=True)

# 1. Alpha Mask Preview
alpha_gray = (cropped_alpha * 255.0).astype(np.uint8)
Image.fromarray(alpha_gray, mode='L').save(os.path.join(out_dir, "01_alpha_mask.png"))

# 2. Checkerboard Background Preview
chk_size = 20
chk = np.zeros((crop_size, crop_size, 3), dtype=np.uint8)
for i in range(0, crop_size, chk_size):
    for j in range(0, crop_size, chk_size):
        if (i // chk_size + j // chk_size) % 2 == 0:
            chk[i:i+chk_size, j:j+chk_size] = [204, 204, 204]
        else:
            chk[i:i+chk_size, j:j+chk_size] = [255, 255, 255]

a_norm = cropped_alpha[..., None]
comp_chk = (cropped_dematted * a_norm + chk.astype(np.float64) * (1.0 - a_norm)).astype(np.uint8)
Image.fromarray(comp_chk).save(os.path.join(out_dir, "02_cleaned_checkerboard.png"))

# 3 & 4. 400% Zoom Crops on Light (#FFF9F5) and Dark (#1A1412) Backgrounds
# Focus on the left edge where tile rim meets ribbon (e.g. y around center 540, x around left edge 50..250)
bg_light = np.array([255, 249, 245], dtype=np.float64)
comp_light = (cropped_dematted * a_norm + bg_light * (1.0 - a_norm)).astype(np.uint8)
Image.fromarray(comp_light).save(os.path.join(out_dir, "03_full_on_light_bg.png"))

bg_dark = np.array([26, 20, 18], dtype=np.float64)
comp_dark = (cropped_dematted * a_norm + bg_dark * (1.0 - a_norm)).astype(np.uint8)
Image.fromarray(comp_dark).save(os.path.join(out_dir, "04_full_on_dark_bg.png"))

# Zoom region: Left edge of tile + start of ribbon (y: 450..650, x: 20..220 in cropped coordinates)
# In cropped coords, tile left edge is around x = 124 - x0_c = 124 - (628 - 540) = 124 - 88 = 36.
# Ribbon left is at x = 235 - 88 = 147.
# So a window x: [20, 220], y: [450, 650] (200x200 px) captures the tile edge, rim highlight, tile surface, and ribbon entry!
zoom_box = (20, 450, 220, 650)
crop_light_zoom = Image.fromarray(comp_light).crop(zoom_box).resize((800, 800), Image.Resampling.NEAREST)
crop_light_zoom.save(os.path.join(out_dir, "05_zoom_400_light.png"))

crop_dark_zoom = Image.fromarray(comp_dark).crop(zoom_box).resize((800, 800), Image.Resampling.NEAREST)
crop_dark_zoom.save(os.path.join(out_dir, "06_zoom_400_dark.png"))

# 5. Android 12+ Splash Canvas (288dp) with 192dp Circle Mask Overlay
# At xxxhdpi, canvas is 1152x1152 px (4x). Tile side 132dp = 528 px.
canvas_size = 1152
tile_target_size = 528
scaled_tile = master_tile_image.resize((tile_target_size, tile_target_size), Image.Resampling.LANCZOS)

splash_canvas_light = Image.new("RGBA", (canvas_size, canvas_size), (255, 249, 245, 255))
offset = (canvas_size - tile_target_size) // 2 # (1152 - 528) // 2 = 312
splash_canvas_light.paste(scaled_tile, (offset, offset), scaled_tile)

# Draw circle mask overlay (diameter 192dp = 768 px at 4x)
draw = ImageDraw.Draw(splash_canvas_light)
center = canvas_size // 2 # 576
r_mask = 384 # 192dp * 4 / 2 = 384 px
draw.ellipse([center - r_mask, center - r_mask, center + r_mask, center + r_mask], outline=(0, 150, 255, 255), width=4)
# Draw tile bounding box outline
draw.rectangle([offset, offset, offset + tile_target_size, offset + tile_target_size], outline=(255, 120, 0, 180), width=2)
splash_canvas_light.save(os.path.join(out_dir, "07_splash_circle_mask_overlay.png"))

# Also on dark theme
splash_canvas_dark = Image.new("RGBA", (canvas_size, canvas_size), (26, 20, 18, 255))
splash_canvas_dark.paste(scaled_tile, (offset, offset), scaled_tile)
draw_dark = ImageDraw.Draw(splash_canvas_dark)
draw_dark.ellipse([center - r_mask, center - r_mask, center + r_mask, center + r_mask], outline=(0, 200, 255, 255), width=4)
draw_dark.rectangle([offset, offset, offset + tile_target_size, offset + tile_target_size], outline=(255, 120, 0, 180), width=2)
splash_canvas_dark.save(os.path.join(out_dir, "08_splash_dark_overlay.png"))

print("All preview images successfully generated in:", out_dir)


