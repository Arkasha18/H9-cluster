# Sculpted Titanium artwork

`approved_design.svg` is the approved sketch with embedded ImageGen materials.
It is an authoring source, not a runtime image containing sample telemetry.

Run `node tools/assets/sculpted_titanium/export.mjs` with `sharp` available to
export the skin assets. The exporter removes all 79 text elements, the sample
gear, Wi-Fi and steering glyphs, gauge indicators and sample thermal bars.
The refinement requested after the real-car review centres both values and
captions inside their housings. Android draws all typography with its bundled
sans-serif font, fitting long values inside the fields. No desktop font binary
is redistributed.

The perimeter retains the approved sculpted material. The centre gear saddle
and the straight bridge use clean vector gradients instead of stretched crops.
Design-specific alpha feathers the inner shoulders into the navigation view.
`refined_artwork.svg` is reproducibly generated for inspection, with no text.

The export validates the lower-left reservations and the wide empty central
corridor. It copies the calibrated map base without modification and records
the input/output hashes in `export_validation.json`. At runtime the header's
black background eases into the existing side alpha over y=40..79, ending
before the fully clear map aperture. The lower-left background is unchanged.
The main renderer never loads or clips with the system mask.
