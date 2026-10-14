"""Derive Android launcher resources from the supplied PNG. Requires Pillow."""
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
source = Image.open(ROOT / "assets/app-icon-source.png").convert("RGBA")
alpha = source.getchannel("A")
# Keep the connected artwork and its antialiased edge; discard isolated export
# speckles and almost transparent black pixels outside the supplied blue tile.
component = alpha.point(lambda value: 255 if value >= 32 else 0)
ImageDraw.floodfill(component, (source.width // 2, source.height // 2), 128)
component = component.point(lambda value: 255 if value == 128 else 0)
component = component.filter(ImageFilter.MaxFilter(5))
source.putalpha(ImageChops.multiply(alpha, component))
source = source.crop(source.getbbox())
side = max(source.size)
artwork = Image.new("RGBA", (side, side))
artwork.alpha_composite(source, ((side - source.width) // 2, (side - source.height) // 2))

for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    destination = RES / f"mipmap-{density}"
    destination.mkdir(parents=True, exist_ok=True)
    artwork.resize((size, size), Image.Resampling.LANCZOS).save(destination / "ic_launcher.png", optimize=True)

# Keep the TV face inside the adaptive icon safe zone for circular/squircle masks.
foreground = Image.new("RGBA", (432, 432))
tile = artwork.resize((336, 336), Image.Resampling.LANCZOS)
foreground.alpha_composite(tile, (48, 48))
destination = RES / "drawable-nodpi"
destination.mkdir(parents=True, exist_ok=True)
foreground.save(destination / "ic_launcher_foreground.png", optimize=True)

preview = Image.new("RGBA", (432, 432), "#dff5ff")
preview.alpha_composite(foreground)
# Android masks the central 72dp of the 108dp layers; match that launcher view.
preview = preview.crop((72, 72, 360, 360)).resize((432, 432), Image.Resampling.LANCZOS)
mask = Image.new("L", preview.size)
ImageDraw.Draw(mask).ellipse((0, 0, 431, 431), fill=255)
preview.putalpha(mask)
(ROOT / "artifacts").mkdir(exist_ok=True)
preview.save(ROOT / "artifacts/app-icon-circle-preview.png")
print("Generated five launcher densities and the adaptive foreground.")
