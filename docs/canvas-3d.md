# Canvas 3D scene primitives

`depthStack`, `parallax`, `extrusion`, `perspectiveQuad`, `perspectiveCard`, and `cube` are
whitelisted native Canvas elements. They are projected from 3D vertices, with depth-sorted,
light-shaded cube faces and perspective-mapped card labels. No generated code, WebGL, device
clock, or random frame state is involved. The preview and exporter both call `SceneRenderer`.

Element `depth` is -1..1 (positive is closer); any element can occupy a depth plane. The camera
can interpolate `dollyFrom`/`dollyTo` (-1..1 in short-side units), `orbitFrom`/`orbitTo`, and
`tiltFrom`/`tiltTo` (-75..75 degrees), alongside the existing pan/zoom. Drawing is sorted by
resolved depth, so moving the camera produces parallax. Old scenes default to zero and retain
their former look.

Spatial elements accept `rotationX`, `rotationY` (degree offsets from the default three-quarter
view), `extrusion`, `specular`, and `rimLight` (0..1). Existing `shadow` enables a soft radial
shadow. `rotationX`, `rotationY`, and `depth` can be animated with the usual `anims` entries.
`depthStack`/`parallax` use `count` (2..8) and optional `labels`; their per-layer motion and the
specular sweep are deterministic from the scene-relative timestamp and element seed.
