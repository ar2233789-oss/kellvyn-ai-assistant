"""Procedural visual-production pipeline for Blender (2D / 3D / 3D-over-time).

Generates editable .blend scenes, rendered previews, optional GLB exports, and a
JSONL progress stream. All Blender-specific APIs are feature-detected where possible.
"""
from __future__ import annotations
import argparse
import json
import math
import os
import sys
import time
from pathlib import Path

import bpy
from mathutils import Vector

PALETTES = {
    "emerald": ("#38E6BC", "#102522", "#EAFBF6"),
    "ocean": ("#4C9AFF", "#101C32", "#E9F3FF"),
    "violet": ("#A78BFA", "#211632", "#F4EEFF"),
    "sunset": ("#F08A4B", "#311A16", "#FFF0DF"),
    "graphite": ("#C9CED6", "#191C22", "#FAFBFF"),
}
LIGHT_RIGS = {
    "studio": [("KEY", (4, -4, 7), 1200, 5.0), ("FILL", (-4, -1, 4), 650, 4.0), ("RIM", (1, 5, 5), 950, 3.0)],
    "dramatic": [("KEY", (4, -4, 7), 1800, 2.2), ("FILL", (-4, -1, 4), 120, 5.0), ("RIM", (1, 5, 5), 1500, 1.5)],
    "soft": [("KEY", (3, -4, 6), 900, 7.0), ("FILL", (-4, -1, 4), 800, 7.0), ("RIM", (1, 5, 5), 450, 5.0)],
    "sunset": [("KEY", (4, -4, 7), 1500, 4.0), ("FILL", (-4, -1, 4), 350, 6.0), ("RIM", (1, 5, 5), 1250, 2.5)],
}


def emit(path: Path, stage: str, progress: int, message: str, **extra):
    event = {"time_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "stage": stage,
             "progress_percent": max(0, min(100, int(progress))), "message": message, **extra}
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as out:
        out.write(json.dumps(event, ensure_ascii=False) + "\n")
        out.flush()
    print("WORKBENCH_PROGRESS " + json.dumps(event, ensure_ascii=False), flush=True)


def rgb(hex_color):
    text = hex_color.lstrip("#")
    return tuple(int(text[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


def material(name, color, metallic=0.0, roughness=0.35, emission=0.0):
    mat = bpy.data.materials.new(name)
    mat.diffuse_color = (*rgb(color), 1.0)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        bsdf.inputs["Base Color"].default_value = (*rgb(color), 1.0)
        bsdf.inputs["Metallic"].default_value = metallic
        bsdf.inputs["Roughness"].default_value = roughness
        if "Emission Color" in bsdf.inputs:
            bsdf.inputs["Emission Color"].default_value = (*rgb(color), 1.0)
            bsdf.inputs["Emission Strength"].default_value = emission
    return mat


def point_at(obj, target=(0, 0, 0)):
    direction = Vector(target) - obj.location
    obj.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def add_area(name, location, power, color, size, target=(0, 0, 0)):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = power
    data.color = rgb(color)
    data.shape = "DISK"
    data.size = size
    if hasattr(data, "use_shadow"):
        data.use_shadow = True
    obj = bpy.data.objects.new(name, data)
    bpy.context.collection.objects.link(obj)
    obj.location = location
    point_at(obj, target)
    # Legacy Blender versions expose contact_shadows; newer builds may not.
    if hasattr(data, "use_contact_shadow"):
        data.use_contact_shadow = True
    return obj


def add_key_light(light_type, location, power, color, *, key_size=5.0, spot_angle=55.0, sun_angle=8.0, target=(0, 0, 0)):
    if light_type == "AREA":
        return add_area("KEY · area softbox", location, power, color, key_size, target)
    kind = "SUN" if light_type == "SUN" else "SPOT"
    data = bpy.data.lights.new(f"KEY · {kind.lower()}", kind)
    data.energy = power
    data.color = rgb(color)
    if hasattr(data, "use_shadow"):
        data.use_shadow = True
    if kind == "SUN" and hasattr(data, "angle"):
        data.angle = math.radians(sun_angle)
    if kind == "SPOT":
        if hasattr(data, "spot_size"):
            data.spot_size = math.radians(spot_angle)
        if hasattr(data, "spot_blend"):
            data.spot_blend = 0.32
        if hasattr(data, "shadow_soft_size"):
            data.shadow_soft_size = 0.12
    obj = bpy.data.objects.new(data.name, data)
    bpy.context.collection.objects.link(obj)
    obj.location = location
    point_at(obj, target)
    return obj


def add_optional_accents(color):
    data = bpy.data.lights.new("Accent · point practical", "POINT")
    data.energy = 85
    data.color = rgb(color)
    data.shadow_soft_size = 0.45
    obj = bpy.data.objects.new(data.name, data)
    bpy.context.collection.objects.link(obj)
    obj.location = (2.2, 1.2, 1.1)
    return obj


def setup_engine(scene, args):
    quality_samples = {"low": 16, "medium": 48, "high": 128}[args.shadow_quality]
    if args.engine == "cycles":
        scene.render.engine = "CYCLES"
        scene.cycles.samples = max(args.samples, quality_samples) if args.shadow_quality == "high" else min(args.samples, quality_samples)
        scene.cycles.use_denoising = True
    else:
        engines = bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items.keys()
        scene.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in engines else "BLENDER_EEVEE"
        # Feature-detect version-specific EEVEE shadow controls.
        if hasattr(scene, "eevee") and hasattr(scene.eevee, "taa_render_samples"):
            scene.eevee.taa_render_samples = max(args.samples, quality_samples) if args.shadow_quality == "high" else min(args.samples, quality_samples)
        if hasattr(scene, "eevee") and hasattr(scene.eevee, "use_soft_shadows"):
            scene.eevee.use_soft_shadows = args.shadow_quality != "low"
    scene.render.resolution_x = 1280
    scene.render.resolution_y = 800
    scene.render.resolution_percentage = args.resolution
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.color_depth = "8"
    scene.render.film_transparent = False
    scene.render.use_file_extension = True
    scene.view_settings.view_transform = "Standard"
    if hasattr(scene.view_settings, "look"):
        options = scene.view_settings.bl_rna.properties["look"].enum_items.keys()
        scene.view_settings.look = "Medium High Contrast" if "Medium High Contrast" in options else "None"
    scene.render.image_settings.compression = 20


def build_scene(args, output: Path, progress: Path):
    emit(progress, "initializing", 3, "Resetting scene and loading production settings")
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    accent_hex, bg_hex, highlight_hex = PALETTES[args.palette]
    accent = material("Workbench · Accent PBR", accent_hex, 0.42, 0.24, 0.10)
    deep = material("Workbench · Background PBR", bg_hex, 0.12, 0.48)
    highlight = material("Workbench · Highlight PBR", highlight_hex, 0.04, 0.28, 0.03)
    setup_engine(scene, args)

    world = bpy.data.worlds.new("Workbench · World")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (*rgb(bg_hex), 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = args.world_strength
    scene.world = world

    emit(progress, "geometry", 18, "Building editable geometry, ground and materials")
    bpy.ops.mesh.primitive_plane_add(size=24, location=(0, 0, -1.25))
    ground = bpy.context.object
    ground.name = "Ground · shadow receiver"
    ground.data.materials.append(deep)
    bpy.ops.mesh.primitive_cube_add(size=1.9, location=(0, 0, 0))
    subject = bpy.context.object
    subject.name = "Workbench · Primary subject"
    subject.data.materials.append(accent)
    bevel = subject.modifiers.new("Soft studio edges", "BEVEL")
    bevel.width = 0.18
    bevel.segments = 5
    subject.modifiers.new("Weighted corner normals", "WEIGHTED_NORMAL")
    bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=24, radius=0.42, location=(1.25, 0.6, 0.22))
    orb = bpy.context.object
    orb.name = "Highlight orb"
    orb.data.materials.append(highlight)
    bpy.ops.mesh.primitive_torus_add(major_radius=1.8, minor_radius=0.025, major_segments=96, minor_segments=12, location=(0, 0, -0.15))
    ring = bpy.context.object
    ring.name = "Accent orbit"
    ring.data.materials.append(highlight)
    ring.rotation_euler = (math.radians(68), math.radians(12), math.radians(-20))

    if args.mode == "2d":
        subject.scale = (2.8, 1.25, 0.14)
        subject.location = (0, 0, -0.3)
        orb.location = (1.65, 0.1, -0.05)
        ring.location = (0, 0, -0.08)
        ring.rotation_euler = (0, 0, math.radians(18))
        bpy.ops.object.camera_add(location=(0, 0, 14))
        camera = bpy.context.object
        camera.data.type = "ORTHO"
        camera.data.ortho_scale = 9.6
        point_at(camera)
    else:
        bpy.ops.object.camera_add(location=(6.8, -8.5, 6.2))
        camera = bpy.context.object
        camera.data.lens = 48
        point_at(camera)
    scene.camera = camera
    if args.dof and args.mode != "2d":
        focus = bpy.data.objects.new("Camera · focus target", None)
        bpy.context.collection.objects.link(focus)
        focus.location = (0, 0, 0)
        camera.data.dof.use_dof = True
        camera.data.dof.focus_object = focus
        camera.data.dof.aperture_fstop = 5.6

    emit(progress, "lighting", 35, f"Configuring {args.lighting} rig, soft shadows and world illumination")
    key_fill_rim = LIGHT_RIGS[args.lighting]
    for role, location, energy, size in key_fill_rim:
        color = highlight_hex if role != "FILL" else accent_hex
        if role == "KEY":
            add_key_light(args.key_light_type, location, energy * args.key_energy_scale, color,
                          key_size=args.key_size, spot_angle=args.spot_angle, sun_angle=args.sun_angle)
        else:
            add_area(f"{role} · area softbox", location, energy, color, size)
    add_optional_accents(accent_hex)
    scene.render.filepath = str(output / "preview.png")
    scene.frame_start, scene.frame_end, scene.render.fps = 1, 48, 24
    if args.shadow_quality == "high":
        scene.render.resolution_percentage = max(args.resolution, 100)
    if args.mode == "4d":
        for frame, loc, rot in [(1, (0, 0, 0), (0, 0, 0)), (24, (0.45, 0.12, 0.25), (0.4, 0.6, math.pi / 2)), (48, (0, 0, 0), (0, 0, math.pi))]:
            subject.location = loc
            subject.rotation_euler = rot
            subject.keyframe_insert(data_path="location", frame=frame)
            subject.keyframe_insert(data_path="rotation_euler", frame=frame)
        action = subject.animation_data.action if subject.animation_data else None
        fcurves = getattr(action, "fcurves", None) if action else None
        if fcurves is not None:
            for fcurve in fcurves:
                for key in fcurve.keyframe_points:
                    key.interpolation = "BEZIER"
    return scene, subject


def create(args):
    output = Path(args.output_dir).expanduser().resolve()
    output.mkdir(parents=True, exist_ok=True)
    progress = Path(args.progress_file).expanduser().resolve() if args.progress_file else output / "progress.jsonl"
    if progress.exists():
        progress.unlink()
    scene, subject = build_scene(args, output, progress)
    emit(progress, "preview_render", 52, "Rendering current scene preview")
    scene.frame_set(1)
    scene.render.filepath = str(output / "preview.png")
    bpy.ops.render.render(write_still=True)
    if args.mode == "4d":
        for frame, pct in ((24, 68), (48, 76)):
            emit(progress, "animation_frame", pct, f"Rendering diagnostic animation frame {frame}", frame=frame)
            scene.frame_set(frame)
            scene.render.filepath = str(output / f"frame-{frame:03d}.png")
            bpy.ops.render.render(write_still=True)
    emit(progress, "saving_scene", 84, "Saving editable Blender project")
    scene.frame_set(1)
    scene.render.filepath = str(output / "preview.png")
    bpy.ops.wm.save_as_mainfile(filepath=str(output / "scene.blend"))
    glb_name = None
    if args.mode in ("3d", "4d"):
        emit(progress, "exporting", 92, "Exporting GLB scene and animation")
        bpy.ops.export_scene.gltf(filepath=str(output / "scene.glb"), export_format="GLB", export_animations=(args.mode == "4d"), use_selection=False)
        glb_name = "scene.glb"
    files = ["preview.png", "scene.blend"] + ([glb_name] if glb_name else []) + (["frame-024.png", "frame-048.png"] if args.mode == "4d" else [])
    manifest = {
        "schema": "kellvyn-workbench-creative/v2", "status": "RENDERED", "mode": args.mode,
        "palette": args.palette, "engine": args.engine, "samples": args.samples,
        "lighting": args.lighting, "key_light_type": args.key_light_type,
        "key_energy_scale": args.key_energy_scale, "key_size": args.key_size,
        "spot_angle_degrees": args.spot_angle, "sun_angle_degrees": args.sun_angle,
        "shadow_quality": args.shadow_quality, "shadow_render_samples": ({"low": 16, "medium": 48, "high": 128}[args.shadow_quality]),
        "world_strength": args.world_strength, "dof": args.dof,
        "light_rig": [f"Key {args.key_light_type}", "Fill area", "Rim area", "Accent point"],
        "shadow_receiver": True, "objects": [obj.name for obj in scene.objects],
        "outputs": files, "blender_version": bpy.app.version_string,
        "dimension_note": "4D means 3D animation over time; not a fourth spatial dimension.",
    }
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    emit(progress, "complete", 100, "Creative assets rendered and manifested", outputs=files)
    print(json.dumps(manifest, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["2d", "3d", "4d"], required=True)
    parser.add_argument("--palette", choices=sorted(PALETTES), default="emerald")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--progress-file", default="")
    parser.add_argument("--lighting", choices=sorted(LIGHT_RIGS), default="studio")
    parser.add_argument("--key-light-type", choices=["AREA", "SUN", "SPOT"], default="AREA")
    parser.add_argument("--key-energy-scale", type=float, default=1.0)
    parser.add_argument("--key-size", type=float, default=5.0)
    parser.add_argument("--spot-angle", type=float, default=55.0)
    parser.add_argument("--sun-angle", type=float, default=8.0)
    parser.add_argument("--engine", choices=["eevee", "cycles"], default="eevee")
    parser.add_argument("--samples", type=int, default=32)
    parser.add_argument("--shadow-quality", choices=["low", "medium", "high"], default="medium")
    parser.add_argument("--resolution", type=int, default=75)
    parser.add_argument("--world-strength", type=float, default=0.28)
    parser.add_argument("--dof", action="store_true")
    args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else [])
    if not 1 <= args.samples <= 4096:
        parser.error("--samples must be 1..4096")
    if not 25 <= args.resolution <= 200:
        parser.error("--resolution must be 25..200")
    if not 0.0 <= args.world_strength <= 10.0:
        parser.error("--world-strength must be 0..10")
    if not 0.1 <= args.key_energy_scale <= 5.0 or not 0.1 <= args.key_size <= 20.0:
        parser.error("key energy scale must be 0.1..5 and key size 0.1..20")
    if not 5.0 <= args.spot_angle <= 170.0 or not 0.1 <= args.sun_angle <= 30.0:
        parser.error("spot angle must be 5..170 degrees and sun angle 0.1..30 degrees")
    create(args)


if __name__ == "__main__":
    main()
