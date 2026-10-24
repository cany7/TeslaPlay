# TeslaPlay app icon

The launcher and CarPlay return shortcut use the user-supplied gray Tesla T image:
`01_0x0-Tesla_T_CoolGrey.png`.
It is a 2048 × 1975 PNG with transparency. Its original download URL and licensing
terms have not been independently verified. Tesla trademarks and this image are
not relicensed by the project's source-code license.

Original SHA-256:
`3049c0219a585538c428ffda4397918dfb0746149cb12ff48f08664780a06315`.

The original bytes are checked in at:

- `shared/src/main/res/drawable-nodpi/tesla_t_cool_grey.png`
The launcher square master `shared/src/main/res/drawable-nodpi/tesla_launcher_square.png`
preserves the original aspect ratio, pads the short edge, and flattens transparency
on white. The adaptive foreground contains a centered 60 × 60dp square in a full 108dp
canvas, rasterized as an 864 × 864 PNG. Padding is baked into the pixels rather
than relying on a layer-list item size, which this phone’s launcher normalizes
away. The central 72dp visible viewport therefore retains white margins. The T is never stretched, traced or cropped. Square and round legacy
exports use this same viewport.

The monochrome foreground uses a separate square PNG with preserved alpha,
so Android themed icons tint the T instead of a solid square. The CarPlay return
resource `common/src/main/res/raw/ic_car_home.png` is a 512 × 512 white PNG with
the same visual margins as the launcher export. Android builds do not depend
on the user's Desktop directory.

`mobile/src/main/AndroidManifest.xml` references the launcher resources in
`shared`. Generate density-specific square/round WebP exports, the 512px PNG
store artwork, and the SVG preview (which embeds the padded square PNG) with:

```sh
npm install --no-save --package-lock=false sharp
node scripts/generate-app-icons.mjs
```

`sharp` is only an export-time dependency, not an Android build dependency.
Generated assets are committed so normal builds do not run this tool.

The former Google Material Symbols `laptop_car` launcher is no longer used.
Its historical Apache license remains in
`docs/licenses/Material-Symbols-Apache-2.0.txt`; the Tesla T is not covered by it.
