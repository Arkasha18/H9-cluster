# Demo system-icon preview

ION AURORA's demo adds a separate, non-interactive `DemoSystemIconsView` above
the skin. It shows 24 photo/video-derived symbols in a simultaneous lamp-test
composition. Selector letters and drive-mode captions are not imitated.
The skin itself shows only the actual ratio 1..8 in D; M/P/N/R remain blank.
The lamps do not represent real warnings or the demo vehicle's current faults.

Glyphs are sourced without modifying the original PNGs from
`system_icons_capture/photo_template/`. Individual crops are drawn within the
reserved regions of the current mask03. This is an approximate visual
simulation, not a pixel-exact reproduction of the vehicle's QNX overlay.

The renderer, image assets, and tests belong to the Demo source set. Debug and
Release provide no-op factories, and the Release contract test requires the
demo image assets to be absent. The skin itself still paints nothing under
mask03 apart from the separately authorized cosmic atmosphere and tiny automatic
gear digit described in the main skins README.
