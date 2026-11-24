"""Regenerates every DhruvAR icon/logo from docs/branding/app-icon-source.webp.

Run from the repo root:   python docs/branding/make-icons.py
Needs Pillow + numpy (pip install pillow numpy). Safe to re-run; it overwrites the generated files.
"""
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "docs/branding/app-icon-source.webp"
RES = ROOT / "app/src/main/res"
WEB = ROOT / "web/public"

src = Image.open(SRC).convert("RGB")

# 1. Measure the white border: the art is every pixel that is not near-white.
a = np.asarray(src).astype(int)
ys, xs = np.where(a.sum(2) < 720)
x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
side = min(x1 - x0, y1 - y0)
art = src.crop((x0, y0, x0 + side, y0 + side))          # square art, border removed
print(f"art box x={x0}..{x1} y={y0}..{y1} -> {side}px square")

def rounded(img, radius_frac=0.22):
    img = img.convert("RGBA")
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, *img.size), img.width * radius_frac, fill=255)
    img.putalpha(m)
    return img

def circle(img):
    img = img.convert("RGBA")
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).ellipse((0, 0, *img.size), fill=255)
    img.putalpha(m)
    return img

def save(img, path, **kw):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, **kw)

# 2. Legacy launcher icons (rounded square + circle) at every density.
for d, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    big = art.resize((px, px), Image.LANCZOS)
    save(rounded(big), RES / f"mipmap-{d}/ic_launcher.webp", quality=95)
    save(circle(big), RES / f"mipmap-{d}/ic_launcher_round.webp", quality=95)

# 3. Adaptive icon: 432px layers (108dp * 4). Background = blurred terrain, foreground = the
#    pin/grid/AR centre, feathered at the edges, kept well inside the 66dp safe zone.
CANVAS = 432
inner = art.crop((int(side*.20), int(side*.10), int(side*.80), int(side*.17)))  # pure terrain strip (no pin/grid/text, no white corners)
bg = inner.resize((CANVAS, CANVAS), Image.LANCZOS).filter(ImageFilter.GaussianBlur(22))
save(bg, RES / "drawable-nodpi/ic_launcher_background.webp", quality=92)

cs = int(side * 940 / 1079)                             # crop containing pin, grid, "AR" (not the white corners)
ox, oy = int(side * 70 / 1079), int(side * 100 / 1079)
centre = art.crop((ox, oy, ox + cs, oy + cs)).resize((296, 296), Image.LANCZOS).convert("RGBA")
feather = Image.new("L", centre.size, 0)
ImageDraw.Draw(feather).rounded_rectangle((14, 14, 282, 282), 40, fill=255)
centre.putalpha(feather.filter(ImageFilter.GaussianBlur(10)))
fg = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
fg.paste(centre, ((CANVAS - 296) // 2,) * 2, centre)
save(fg, RES / "drawable-nodpi/ic_launcher_foreground.webp", lossless=True)

# 4. Product logo inside the Android app (sign-in screen) and store icon.
save(rounded(art.resize((512, 512), Image.LANCZOS)), RES / "drawable-nodpi/logo_dhruvar.webp", quality=92)
save(rounded(art.resize((512, 512), Image.LANCZOS)), ROOT / "docs/branding/app-icon-512.png")

# 5. Dashboard logos + favicon.
for px in (128, 256):
    save(rounded(art.resize((px, px), Image.LANCZOS)), WEB / f"dhruvar-logo-{px}.png", optimize=True)
fav = rounded(art.resize((256, 256), Image.LANCZOS))
save(fav.resize((64, 64), Image.LANCZOS), WEB / "favicon.png", optimize=True)
print("done")

# 6. Army logo (landscape flag, ~4:3). Looks for docs/branding/army-logo.(png|webp|jpg|jpeg), finds the
#    red rectangle (drops the white margin and thin dark border), keeps the aspect ratio, writes 360px wide.
#    If no file exists, a neutral grey PLACEHOLDER box is written instead (never an invented emblem).
from PIL import ImageFont
found = next((p for ext in ("png", "webp", "jpg", "jpeg") if (p := ROOT / f"docs/branding/army-logo.{ext}").exists()), None)
if found:
    im = Image.open(found).convert("RGB")
    b = np.asarray(im).astype(int)
    ys, xs = np.where((b[..., 0] > 120) & (b[..., 1] < 70) & (b[..., 2] < 70))
    INSET = 4                                            # drop anti-aliased edge pixels
    lg = im.crop((xs.min() + INSET, ys.min() + INSET, xs.max() + 1 - INSET, ys.max() + 1 - INSET))
    lg = lg.resize((360, round(360 * lg.height / lg.width)), Image.LANCZOS)
    print("using real army logo:", found.name, "->", lg.size)
else:
    lg = Image.new("RGBA", (360, 270), (0, 0, 0, 0))
    d = ImageDraw.Draw(lg)
    d.rounded_rectangle((4, 4, 355, 265), 30, fill=(226, 228, 222, 255), outline=(140, 146, 130, 255), width=6)
    f = ImageFont.load_default(size=56)
    for i, word in enumerate(("ARMY", "LOGO")):
        d.text((180, 95 + i * 62), word, fill=(110, 116, 100, 255), font=f, anchor="mm")
    print("army logo missing -> wrote neutral placeholder")
save(lg, RES / "drawable-nodpi/army_logo.png", optimize=True)
save(lg, WEB / "army-logo.png", optimize=True)
