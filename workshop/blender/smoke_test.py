"""Smoke test for the Kellvyn 3D/animation workshop.

Creates a tiny animated GLB to verify that Blender can author and export a
real 3D asset in CI. This is a tooling check, not a production asset.
"""
import json
import os
import sys

import bpy


def arguments_after_separator():
    if "--" not in sys.argv:
        return []
    return sys.argv[sys.argv.index("--") + 1 :]


def main():
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default="out")
    args = parser.parse_args(arguments_after_separator())
    os.makedirs(args.output_dir, exist_ok=True)

    # Start from an empty, deterministic scene.
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.frame_start = 1
    scene.frame_end = 48
    scene.render.fps = 24

    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0.0, 0.0, 0.0))
    cube = bpy.context.object
    cube.name = "Kellvyn_Animation_Smoke_Test"
    cube.keyframe_insert(data_path="location", frame=1)
    cube.location = (1.5, 0.0, 0.0)
    cube.keyframe_insert(data_path="location", frame=48)

    bpy.ops.object.select_all(action="DESELECT")
    cube.select_set(True)
    bpy.context.view_layer.objects.active = cube

    output_path = os.path.abspath(
        os.path.join(args.output_dir, "kellvyn-animation-smoke.glb")
    )
    bpy.ops.export_scene.gltf(
        filepath=output_path,
        export_format="GLB",
        export_animations=True,
        use_selection=True,
    )
    if not os.path.isfile(output_path) or os.path.getsize(output_path) < 1024:
        raise RuntimeError("GLB export did not produce a valid-sized file")

    manifest = {
        "status": "passed",
        "asset": os.path.basename(output_path),
        "format": "glb",
        "scene": "one animated cube",
        "frame_start": scene.frame_start,
        "frame_end": scene.frame_end,
        "fps": scene.render.fps,
        "purpose": "CI smoke test only; not the production UI or a production asset",
    }
    with open(os.path.join(args.output_dir, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == "__main__":
    main()
