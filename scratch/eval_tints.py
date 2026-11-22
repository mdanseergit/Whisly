import os
from PIL import Image, ImageDraw, ImageFont
import numpy as np

workspace = r"C:\Users\danse\OneDrive\document\Whisly"
src_tile_path = os.path.join(workspace, "audit", "splash_preview", "02_cleaned_checkerboard.png")
# We already have master_tile_image or we can load cleaned_full_master
# Let's load the cleaned master tile RGBA
from PIL import Image
# Load master from scratch or re-create
master_path = os.path.join(workspace, "scratch", "cleaned_full_master.png")
if not os.path.exists(master_path):
    print("Master not found, reloading...")

im_master = Image.open(master_path)
arr_master = np.array(im_master)

# True square crop of master at (cx=627, cy=615) of size 1080x1080
cx_i, cy_i = 627, 615
half = 540
x0, x1 = cx_i - half, cx_i + half
y0, y1 = cy_i - half, cy_i + half
cropped_master = arr_master[y0:y1, x0:x1]
tile_rgba = Image.fromarray(cropped_master, mode='RGBA')

# Target tile size for phone splash (at 1080 wide screen, 288dp canvas -> 132dp tile is ~495px on a 1080px wide screen)
# In Android dp: on a typical 1080x2400 screen (xxhdpi 400-440dpi, ~2.6-2.75x or 411dp wide):
# 132dp on 411dp screen is 132 * (1080/411) = 346 px.
# But system splash icon canvas is 288dp. 132dp tile inside 288dp canvas is 132/288 = 45.8% of splash container.
# Let's render phone splash screens at 1080 x 2400 (standard Android phone resolution)
# System splash places the 288dp icon exactly in the center of the screen!
# 288dp on 1080px wide (411dp) screen = 756 px.
# Tile (132dp) = 346 px.
splash_phone_w = 1080
splash_phone_h = 2400
tile_phone_size = int(round(132.0 * (1080.0 / 411.0))) # 346 px
tile_scaled = tile_rgba.resize((tile_phone_size, tile_phone_size), Image.Resampling.LANCZOS)

# Colors to evaluate
candidates = [
    ("#F8F2EC", [248, 242, 236], "whisly_cream_bg (#F8F2EC)"),
    ("#F5ECE4", [245, 236, 228], "Warm Tint 1 (#F5ECE4)"),
    ("#F3E9E0", [243, 233, 224], "Warm Tint 2 (#F3E9E0)"),
    ("#E8DDD5", [232, 221, 213], "Upper Limit (#E8DDD5)")
]

def rel_lum(rgb):
    lin = [c / 255.0 for c in rgb]
    lin = [(c / 12.92) if c <= 0.04045 else (((c + 0.055) / 1.055) ** 2.4) for c in lin]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]

def contrast_ratio(l1, l2):
    b, d = max(l1, l2), min(l1, l2)
    return (b + 0.05) / (d + 0.05)

# Tile key colors:
tile_rim_rgb = [252, 252, 253]
tile_top_rgb = [251, 251, 251]
tile_center_rgb = [250, 249, 250]
tile_edge_trans_rgb = [240, 234, 233]

l_rim = rel_lum(tile_rim_rgb)
l_top = rel_lum(tile_top_rgb)
l_center = rel_lum(tile_center_rgb)
l_trans = rel_lum(tile_edge_trans_rgb)

out_dir = os.path.join(workspace, "audit", "splash_tint_candidates")
os.makedirs(out_dir, exist_ok=True)

print("=== COLOR CANDIDATE MEASUREMENTS ===")
rendered_phone_images = []

for hex_code, rgb, label in candidates:
    l_bg = rel_lum(rgb)
    cr_rim = contrast_ratio(l_bg, l_rim)
    cr_top = contrast_ratio(l_bg, l_top)
    cr_center = contrast_ratio(l_bg, l_center)
    cr_trans = contrast_ratio(l_bg, l_trans)
    
    print(f"\nCandidate: {label}")
    print(f"  Hex: {hex_code}, RGB: {rgb}, Luminance: {l_bg:.4f}")
    print(f"  Contrast vs Tile Rim ({tile_rim_rgb}): {cr_rim:.3f}:1")
    print(f"  Contrast vs Tile Top ({tile_top_rgb}): {cr_top:.3f}:1")
    print(f"  Contrast vs Tile Center ({tile_center_rgb}): {cr_center:.3f}:1")
    print(f"  Contrast vs Edge Transition ({tile_edge_trans_rgb}): {cr_trans:.3f}:1")
    
    # Render phone splash image
    phone_img = Image.new("RGBA", (splash_phone_w, splash_phone_h), tuple(rgb) + (255,))
    pos_x = (splash_phone_w - tile_phone_size) // 2
    pos_y = (splash_phone_h - tile_phone_size) // 2
    phone_img.paste(tile_scaled, (pos_x, pos_y), tile_scaled)
    
    # Save individual phone screenshot
    clean_name = hex_code.replace("#", "")
    phone_path = os.path.join(out_dir, f"splash_{clean_name}.png")
    phone_img.save(phone_path)
    rendered_phone_images.append((hex_code, label, cr_rim, phone_img))

# Build Side-by-Side Comparison Image (Scaled down to 4 columns)
# Each column: 540 x 1200
col_w = 400
col_h = 888
sbs = Image.new("RGB", (col_w * 4 + 30, col_h + 100), (255, 255, 255))
draw_sbs = ImageDraw.Draw(sbs)

for idx, (hex_code, label, cr, p_img) in enumerate(rendered_phone_images):
    thumb = p_img.resize((col_w, col_h), Image.Resampling.LANCZOS)
    x_off = idx * col_w + (idx + 1) * 6
    sbs.paste(thumb, (x_off, 80))
    # Draw border around each phone preview
    draw_sbs.rectangle([x_off, 80, x_off + col_w, 80 + col_h], outline=(200, 200, 200), width=2)
    # Add text banner
    draw_sbs.text((x_off + 10, 15), f"{hex_code}", fill=(30, 30, 30))
    draw_sbs.text((x_off + 10, 40), f"CR: {cr:.2f}:1", fill=(80, 80, 80))

sbs_path = os.path.join(out_dir, "00_side_by_side_comparison.png")
sbs.save(sbs_path)
print(f"\nSaved side-by-side comparison to: {sbs_path}")

# Copy to brain artifact directory as well
dst_dir = r"C:\Users\danse\.gemini\antigravity\brain\4fa1a3f6-d72e-4901-819c-642b6e2407a2\splash_tint_candidates"
os.makedirs(dst_dir, exist_ok=True)
import shutil
for f in os.listdir(out_dir):
    shutil.copy2(os.path.join(out_dir, f), os.path.join(dst_dir, f))
print(f"Copied {len(os.listdir(out_dir))} files to {dst_dir}")


