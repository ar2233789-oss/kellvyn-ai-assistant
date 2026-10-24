"""Headless Blender production-asset pipeline for the Super Lab.
2D = camera/flat layout; 3D = editable scene + GLB; 4D = 3D animation over time,
with keyframes and multiple diagnostic frames (not a fourth spatial dimension).
"""
from __future__ import annotations
import argparse
import json
import math
import os
import sys
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
def rgb(hex_color: str):
    s=hex_color.lstrip("#")
    return tuple(int(s[i:i+2],16)/255.0 for i in (0,2,4))
def material(name, color, metallic=0.0, roughness=0.35, emission=0.0):
    mat=bpy.data.materials.new(name)
    mat.diffuse_color=(*rgb(color),1.0)
    mat.use_nodes=True
    bsdf=mat.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        bsdf.inputs["Base Color"].default_value=(*rgb(color),1.0)
        bsdf.inputs["Metallic"].default_value=metallic
        bsdf.inputs["Roughness"].default_value=roughness
        if "Emission Color" in bsdf.inputs:
            bsdf.inputs["Emission Color"].default_value=(*rgb(color),1.0)
            bsdf.inputs["Emission Strength"].default_value=emission
    return mat
def point_camera(obj, target=(0,0,0)):
    direction=Vector(target)-obj.location
    obj.rotation_euler=direction.to_track_quat("-Z","Y").to_euler()
def add_area(name, location, power, color, size, target=(0,0,0)):
    data=bpy.data.lights.new(name,"AREA")
    data.energy=power
    data.color=rgb(color)
    data.shape="DISK"
    data.size=size
    obj=bpy.data.objects.new(name,data)
    bpy.context.collection.objects.link(obj)
    obj.location=location
    point_camera(obj,target)
    return obj
def create(args):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene=bpy.context.scene
    accent_hex,bg_hex,light_hex=PALETTES[args.palette]
    accent=material("Palette · Accent",accent_hex,0.42,0.24,0.18)
    deep=material("Palette · Base",bg_hex,0.15,0.48)
    highlight=material("Palette · Highlight",light_hex,0.04,0.30,0.05)
    scene.render.engine="BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items.keys() else "BLENDER_EEVEE"
    scene.render.resolution_x=960
    scene.render.resolution_y=600
    scene.render.resolution_percentage=100
    scene.render.image_settings.file_format="PNG"
    scene.render.film_transparent=False
    scene.render.filepath=os.path.join(args.output_dir,"preview.png")
    scene.render.image_settings.color_mode="RGBA"
    scene.render.resolution_percentage=75
    scene.render.image_settings.color_depth="8"
    scene.render.use_file_extension=True
    scene.view_settings.view_transform="Standard"
    scene.view_settings.look="Medium High Contrast" if "Medium High Contrast" in bpy.types.ColorManagedViewSettings.bl_rna.properties["look"].enum_items.keys() else "None"
    world=bpy.data.worlds.new("Super Lab World")
    world.use_nodes=True
    world.node_tree.nodes["Background"].inputs["Color"].default_value=(*rgb(bg_hex),1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value=0.28
    scene.world=world

    # Ground plane + subject give consistent visible shadows and material contrast.
    bpy.ops.mesh.primitive_plane_add(size=22, location=(0,0,-1.25))
    ground=bpy.context.object; ground.name="Ground · shadow catcher"; ground.data.materials.append(deep)
    bpy.ops.mesh.primitive_cube_add(size=1.9, location=(0,0,0))
    subject=bpy.context.object; subject.name="Kellvyn Studio Subject"; subject.data.materials.append(accent)
    bevel=subject.modifiers.new("Soft studio edges","BEVEL"); bevel.width=0.18; bevel.segments=4
    subject.modifiers.new("Weighted corner normals","WEIGHTED_NORMAL")
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, radius=0.42, location=(1.25,0.6,0.22))
    orb=bpy.context.object; orb.name="Highlight orb"; orb.data.materials.append(highlight)
    bpy.ops.mesh.primitive_torus_add(major_radius=1.8, minor_radius=0.025, major_segments=72, minor_segments=8, location=(0,0,-0.15))
    ring=bpy.context.object; ring.name="Accent orbit"; ring.data.materials.append(highlight)
    ring.rotation_euler=(math.radians(68),math.radians(12),math.radians(-20))

    if args.mode=="2d":
        # Flat, top-down graphic scene suitable for UI/asset mockups.
        subject.scale=(2.8,1.25,0.14); subject.location=(0,0,-0.3)
        orb.location=(1.65,0.1,-0.05)
        ring.location=(0,0,-0.08)
        ring.rotation_euler=(0,0,math.radians(18))
        scene.camera=None
        bpy.ops.object.camera_add(location=(0,0,14))
        camera=bpy.context.object; camera.data.type="ORTHO"; camera.data.ortho_scale=9.6
        point_camera(camera)
    else:
        bpy.ops.object.camera_add(location=(6.8,-8.5,6.2))
        camera=bpy.context.object; camera.data.lens=46
        point_camera(camera)
    scene.camera=camera
    add_area("Key · softbox",(4,-4,7),1050,light_hex,5)
    add_area("Fill · softbox",(-4,-1,4),580,accent_hex,4)
    add_area("Rim · edge light",(1,5,5),850,light_hex,3)
    scene.render.image_settings.file_format="PNG"
    scene.render.resolution_percentage=75
    scene.render.filepath=os.path.join(args.output_dir,"preview.png")
    scene.camera.data.lens=50 if args.mode!="2d" else scene.camera.data.lens
    scene.frame_start=1; scene.frame_end=48; scene.render.fps=24
    if args.mode=="4d":
        for frame, loc, rot in [
            (1,(0,0,0),(0,0,0)),
            (24,(0.45,0.12,0.25),(0.4,0.6,math.pi/2)),
            (48,(0,0,0),(0,0,math.pi)),
        ]:
            subject.location=loc; subject.rotation_euler=rot
            subject.keyframe_insert(data_path="location",frame=frame)
            subject.keyframe_insert(data_path="rotation_euler",frame=frame)
        for fcurve in subject.animation_data.action.fcurves:
            for key in fcurve.keyframe_points:
                key.interpolation="BEZIER"
    os.makedirs(args.output_dir,exist_ok=True)
    scene.render.filepath=os.path.join(args.output_dir,"preview.png")
    scene.frame_set(1)
    bpy.ops.render.render(write_still=True)
    if args.mode=="4d":
        scene.frame_set(24)
        scene.render.filepath=os.path.join(args.output_dir,"frame-024.png")
        bpy.ops.render.render(write_still=True)
        scene.frame_set(48)
        scene.render.filepath=os.path.join(args.output_dir,"frame-048.png")
        bpy.ops.render.render(write_still=True)
    scene.render.filepath=os.path.join(args.output_dir,"preview.png")
    blend_path=os.path.join(args.output_dir,"scene.blend")
    bpy.ops.wm.save_as_mainfile(filepath=blend_path)
    glb_path=None
    if args.mode in ("3d","4d"):
        try:
            bpy.ops.export_scene.gltf(filepath=os.path.join(args.output_dir,"scene.glb"), export_format="GLB", export_animations=(args.mode=="4d"), use_selection=False)
            glb_path="scene.glb"
        except Exception as exc:
            raise RuntimeError("GLB export is required for the 3D/4D studio outputs: "+str(exc)) from exc
    manifest={
        "schema":1,"status":"rendered","mode":args.mode,"palette":args.palette,
        "paletteHex":{"accent":accent_hex,"background":bg_hex,"highlight":light_hex},
        "lightRig":["Key · softbox","Fill · softbox","Rim · edge light"],
        "shadowPlane":True,"animation":"keyframed over frames 1,24,48" if args.mode=="4d" else "static",
        "dimensionNote":"4D means 3D content evolving over time; it is not four spatial dimensions.",
        "outputs":["preview.png","scene.blend"]+([glb_path] if glb_path else [])+(["frame-024.png","frame-048.png"] if args.mode=="4d" else [])
    }
    Path(args.output_dir,"studio-manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(manifest,ensure_ascii=False))
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--mode",choices=["2d","3d","4d"],required=True)
    parser.add_argument("--palette",choices=sorted(PALETTES),default="emerald")
    parser.add_argument("--output-dir",required=True)
    args=parser.parse_args(sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else [])
    create(args)
if __name__=="__main__":
    main()
