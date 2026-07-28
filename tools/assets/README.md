# Canonical design-mask assets

`h9_system_icon_forbidden_mask_1920x720.png` is the canonical system-icon
collision mask used by `build_forum_design_template.py`.

It was extracted from the owner's red 1280×480 screen markup dated
2026-07-28 and scaled to the application's 1920×720 coordinate system by the
exact factor 1.5. Only the six large connected markup regions were retained;
the red illumination of the source dashboard scales was excluded. Small holes
inside painted regions were filled without expanding their outer silhouette.

The file is binary:

- white: no skin graphics or dynamic values;
- black: this mask imposes no layout restriction.

Do not approximate the mask with rectangles or enlarge it for safety margins.
