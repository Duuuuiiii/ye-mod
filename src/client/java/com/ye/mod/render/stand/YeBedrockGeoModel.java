package com.ye.mod.render.stand;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * High-precision Bedrock 1.12.0 GeoJSON 3D Model loader & single-batch renderer
 * built for authentic JoJo Stands (Star Platinum, The World, Silver Chariot, Crazy Diamond, etc.).
 * Supports hierarchical bones, box UVs, per-face UVs, rotated cubes, mirror, inflate, and Meshy poly_mesh!
 */
public final class YeBedrockGeoModel {
    private static final float DEG2RAD = (float) (Math.PI / 180.0);
    private static final Map<String, YeBedrockGeoModel> CACHE = new HashMap<>();

    public static final class Bone {
        public final String name;
        public final boolean hidden;
        public final float ox, oy, oz;
        public final float baseRx, baseRy, baseRz;
        public float animRx, animRy, animRz;
        public final List<ModelPart.Polygon> polygons = new ArrayList<>();
        public final List<Bone> children = new ArrayList<>();

        Bone(String name, float ox, float oy, float oz, float baseRx, float baseRy, float baseRz) {
            this.name = name;
            this.hidden = name.startsWith("hidden.");
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.baseRx = baseRx;
            this.baseRy = baseRy;
            this.baseRz = baseRz;
        }

        public void resetAnim() {
            animRx = 0f;
            animRy = 0f;
            animRz = 0f;
            for (Bone c : children) c.resetAnim();
        }
    }

    private final List<Bone> roots = new ArrayList<>();
    private final Map<String, Bone> byName = new HashMap<>();

    private YeBedrockGeoModel() {}

    public static YeBedrockGeoModel getOrLoad(String geoPath) {
        return CACHE.computeIfAbsent(geoPath, YeBedrockGeoModel::loadInternal);
    }

    public Bone getBone(String name) {
        return byName.get(name);
    }

    public void resetPose() {
        for (Bone r : roots) r.resetAnim();
    }

    public void setupStandAnim(float headYawDeg, float headPitchDeg, boolean attacking, long nowMs) {
        resetPose();
        float t = (nowMs % 60000L) / 1000.0f;
        float breathe = (float) Math.sin(t * 2.2f) * 0.04f;

        Bone head = byName.get("head");
        if (head != null) {
            head.animRy = headYawDeg * DEG2RAD * 0.55f;
            head.animRx = headPitchDeg * DEG2RAD * 0.45f;
        }
        Bone torsoBend = byName.get("torso_bend");
        if (torsoBend != null) {
            torsoBend.animRx = attacking ? 0.18f : (-0.08f + breathe);
        }

        Bone leftArm = byName.get("left_arm");
        Bone leftBend = byName.get("left_arm_bend");
        Bone rightArm = byName.get("right_arm");
        Bone rightBend = byName.get("right_arm_bend");

        if (attacking) {
            float rush = (float) Math.sin(nowMs / 28.0);
            float rushCos = (float) Math.cos(nowMs / 28.0);
            if (rightArm != null) {
                rightArm.animRx = -1.45f + rush * 0.55f;
                rightArm.animRy = -0.15f + rushCos * 0.20f;
            }
            if (rightBend != null) rightBend.animRx = -0.35f + Math.max(0f, rush) * 0.35f;
            if (leftArm != null) {
                leftArm.animRx = -1.45f - rush * 0.55f;
                leftArm.animRy = 0.15f - rushCos * 0.20f;
            }
            if (leftBend != null) leftBend.animRx = -0.35f + Math.max(0f, -rush) * 0.35f;
        } else {
            // Iconic menacing JoJo floating guard pose
            if (rightArm != null) {
                rightArm.animRx = -0.42f + breathe;
                rightArm.animRz = 0.18f;
                rightArm.animRy = -0.22f;
            }
            if (rightBend != null) rightBend.animRx = -0.78f;
            if (leftArm != null) {
                leftArm.animRx = -0.28f - breathe;
                leftArm.animRz = -0.18f;
                leftArm.animRy = 0.18f;
            }
            if (leftBend != null) leftBend.animRx = -0.58f;
        }

        Bone leftLeg = byName.get("left_leg");
        Bone leftLegBend = byName.get("left_leg_bend");
        Bone rightLeg = byName.get("right_leg");
        Bone rightLegBend = byName.get("right_leg_bend");
        if (leftLeg != null) {
            leftLeg.animRx = -0.18f + breathe * 0.5f;
            leftLeg.animRz = -0.06f;
        }
        if (leftLegBend != null) leftLegBend.animRx = 0.38f;
        if (rightLeg != null) {
            rightLeg.animRx = 0.12f - breathe * 0.5f;
            rightLeg.animRz = 0.06f;
        }
        if (rightLegBend != null) rightLegBend.animRx = 0.22f;
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector collector, Identifier tex, int light, int argb) {
        if (roots.isEmpty()) return;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(tex), (rootPose, vc) -> {
            PoseStack local = new PoseStack();
            local.last().pose().set(rootPose.pose());
            local.last().normal().set(rootPose.normal());
            for (Bone r : roots) {
                emitBoneRecursive(local, vc, r, light, argb);
            }
        });
    }

    private static void emitBoneRecursive(PoseStack ps, VertexConsumer vc, Bone bone, int light, int argb) {
        if (bone.hidden) return;
        ps.pushPose();
        ps.translate(bone.ox / 16.0f, bone.oy / 16.0f, bone.oz / 16.0f);
        float rx = bone.baseRx + bone.animRx;
        float ry = bone.baseRy + bone.animRy;
        float rz = bone.baseRz + bone.animRz;
        if (rz != 0f) ps.mulPose(Axis.ZP.rotation(rz));
        if (ry != 0f) ps.mulPose(Axis.YP.rotation(ry));
        if (rx != 0f) ps.mulPose(Axis.XP.rotation(rx));

        if (!bone.polygons.isEmpty()) {
            PoseStack.Pose p = ps.last();
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;

            for (ModelPart.Polygon poly : bone.polygons) {
                Vector3fc n = poly.normal();
                for (ModelPart.Vertex v : poly.vertices()) {
                    vc.addVertex(p, v.worldX(), v.worldY(), v.worldZ())
                            .setColor(r, g, b, a)
                            .setUv(v.u(), v.v())
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(p, n.x(), n.y(), n.z());
                }
            }
        }

        for (Bone child : bone.children) {
            emitBoneRecursive(ps, vc, child, light, argb);
        }
        ps.popPose();
    }

    private record RawBone(String name, String parent, Vector3f pivot, Vector3f rotation,
                           List<JsonObject> cubes, JsonObject polyMesh) {}

    private static YeBedrockGeoModel loadInternal(String geoPath) {
        YeBedrockGeoModel model = new YeBedrockGeoModel();
        try (InputStream in = openGeoStream(geoPath)) {
            if (in == null) return model;
            JsonObject rootJson = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject geo = rootJson.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            JsonObject desc = geo.getAsJsonObject("description");
            int texW = desc.get("texture_width").getAsInt();
            int texH = desc.get("texture_height").getAsInt();

            Map<String, RawBone> rawMap = new LinkedHashMap<>();
            List<RawBone> extraRotatedBones = new ArrayList<>();
            int rotIdx = 0;

            JsonArray bonesArr = geo.getAsJsonArray("bones");
            for (JsonElement el : bonesArr) {
                JsonObject bObj = el.getAsJsonObject();
                String name = bObj.get("name").getAsString();
                String parent = bObj.has("parent") ? bObj.get("parent").getAsString() : null;
                Vector3f pivot = bObj.has("pivot") ? readVec3(bObj.getAsJsonArray("pivot")) : new Vector3f();
                Vector3f rot = bObj.has("rotation") ? readVec3(bObj.getAsJsonArray("rotation")) : new Vector3f();
                JsonObject polyMesh = bObj.has("poly_mesh") ? bObj.getAsJsonObject("poly_mesh") : null;

                List<JsonObject> directCubes = new ArrayList<>();
                if (bObj.has("cubes")) {
                    for (JsonElement ce : bObj.getAsJsonArray("cubes")) {
                        JsonObject cObj = ce.getAsJsonObject();
                        if (cObj.has("rotation")) {
                            Vector3f cRot = readVec3(cObj.getAsJsonArray("rotation"));
                            if (cRot.lengthSquared() > 1e-6f) {
                                Vector3f cPiv = cObj.has("pivot") ? readVec3(cObj.getAsJsonArray("pivot")) : new Vector3f(pivot);
                                String subName = name + "_rc_" + (rotIdx++);
                                List<JsonObject> single = new ArrayList<>();
                                single.add(cObj);
                                extraRotatedBones.add(new RawBone(subName, name, cPiv, cRot, single, null));
                                continue;
                            }
                        }
                        directCubes.add(cObj);
                    }
                }
                rawMap.put(name, new RawBone(name, parent, pivot, rot, directCubes, polyMesh));
            }
            for (RawBone rb : extraRotatedBones) {
                rawMap.put(rb.name, rb);
            }

            // Instantiate Bone nodes with GeckoModelFormat exact coordinate mapping
            for (RawBone rb : rawMap.values()) {
                Vector3f parentPiv = (rb.parent != null && rawMap.containsKey(rb.parent)) ? rawMap.get(rb.parent).pivot : null;
                float ox = parentPiv != null ? (rb.pivot.x - parentPiv.x) : rb.pivot.x;
                float oy = parentPiv != null ? -(rb.pivot.y - parentPiv.y) : (-rb.pivot.y + 24.0f);
                float oz = parentPiv != null ? (rb.pivot.z - parentPiv.z) : rb.pivot.z;

                Bone bone = new Bone(rb.name, ox, oy, oz,
                        rb.rotation.x * DEG2RAD, rb.rotation.y * DEG2RAD, rb.rotation.z * DEG2RAD);

                for (JsonObject cObj : rb.cubes) {
                    bakeCubePolygons(cObj, rb.pivot, texW, texH, bone.polygons);
                }
                if (rb.polyMesh != null) {
                    bakePolyMesh(rb.polyMesh, rb.pivot, texW, texH, bone.polygons);
                }
                model.byName.put(rb.name, bone);
            }

            // Wire hierarchy
            for (RawBone rb : rawMap.values()) {
                Bone b = model.byName.get(rb.name);
                if (rb.parent != null && model.byName.containsKey(rb.parent)) {
                    model.byName.get(rb.parent).children.add(b);
                } else {
                    model.roots.add(b);
                }
            }
        } catch (Exception ignored) {
        }
        return model;
    }

    private static void bakeCubePolygons(JsonObject cObj, Vector3f bonePivot, int texW, int texH, List<ModelPart.Polygon> out) {
        Vector3f origin = cObj.has("origin") ? readVec3(cObj.getAsJsonArray("origin")) : new Vector3f();
        Vector3f size = cObj.has("size") ? readVec3(cObj.getAsJsonArray("size")) : new Vector3f();
        float grow = cObj.has("inflate") ? cObj.get("inflate").getAsFloat() : 0.0f;
        boolean mirror = cObj.has("mirror") && cObj.get("mirror").getAsBoolean();

        float ox = origin.x - bonePivot.x;
        float oy = -(origin.y - bonePivot.y) - size.y;
        float oz = origin.z - bonePivot.z;

        float x0 = ox - grow, y0 = oy - grow, z0 = oz - grow;
        float x1 = ox + size.x + grow, y1 = oy + size.y + grow, z1 = oz + size.z + grow;

        JsonElement uvEl = cObj.get("uv");
        if (uvEl == null) return;

        if (uvEl.isJsonArray()) {
            if (mirror) {
                float sw = x1; x1 = x0; x0 = sw;
            }
            ModelPart.Vertex v000 = new ModelPart.Vertex(x0, y0, z0, 0f, 0f);
            ModelPart.Vertex v100 = new ModelPart.Vertex(x1, y0, z0, 0f, 8f);
            ModelPart.Vertex v110 = new ModelPart.Vertex(x1, y1, z0, 8f, 8f);
            ModelPart.Vertex v010 = new ModelPart.Vertex(x0, y1, z0, 8f, 0f);
            ModelPart.Vertex v001 = new ModelPart.Vertex(x0, y0, z1, 0f, 0f);
            ModelPart.Vertex v101 = new ModelPart.Vertex(x1, y0, z1, 0f, 8f);
            ModelPart.Vertex v111 = new ModelPart.Vertex(x1, y1, z1, 8f, 8f);
            ModelPart.Vertex v011 = new ModelPart.Vertex(x0, y1, z1, 8f, 0f);

            JsonArray uvArr = uvEl.getAsJsonArray();
            int u = uvArr.get(0).getAsInt();
            int v = uvArr.get(1).getAsInt();
            int sx = (int) size.x, sy = (int) size.y, sz = (int) size.z;

            float f4 = u;
            float f5 = u + sz;
            float f6 = u + sz + sx;
            float f7 = u + sz + sx + sx;
            float f8 = u + sz + sx + sz;
            float f9 = u + sz + sx + sz + sx;
            float f10 = v;
            float f11 = v + sz;
            float f12 = v + sz + sy;

            out.add(new ModelPart.Polygon(new ModelPart.Vertex[]{v101, v001, v000, v100}, f5, f10, f6, f11, texW, texH, mirror, Direction.DOWN));
            out.add(new ModelPart.Polygon(new ModelPart.Vertex[]{v110, v010, v011, v111}, f6, f11, f7, f10, texW, texH, mirror, Direction.UP));
            out.add(new ModelPart.Polygon(new ModelPart.Vertex[]{v000, v001, v011, v010}, f4, f11, f5, f12, texW, texH, mirror, Direction.WEST));
            out.add(new ModelPart.Polygon(new ModelPart.Vertex[]{v100, v000, v010, v110}, f5, f11, f6, f12, texW, texH, mirror, Direction.NORTH));
            out.add(new ModelPart.Polygon(new ModelPart.Vertex[]{v101, v100, v110, v111}, f6, f11, f8, f12, texW, texH, mirror, Direction.EAST));
            out.add(new ModelPart.Polygon(new ModelPart.Vertex[]{v001, v101, v111, v011}, f8, f11, f9, f12, texW, texH, mirror, Direction.SOUTH));
        } else if (uvEl.isJsonObject()) {
            JsonObject facesObj = uvEl.getAsJsonObject();
            ModelPart.Vertex v000 = new ModelPart.Vertex(x0, y0, z0, 0f, 0f);
            ModelPart.Vertex v100 = new ModelPart.Vertex(x1, y0, z0, 0f, 8f);
            ModelPart.Vertex v110 = new ModelPart.Vertex(x1, y1, z0, 8f, 8f);
            ModelPart.Vertex v010 = new ModelPart.Vertex(x0, y1, z0, 8f, 0f);
            ModelPart.Vertex v001 = new ModelPart.Vertex(x0, y0, z1, 0f, 0f);
            ModelPart.Vertex v101 = new ModelPart.Vertex(x1, y0, z1, 0f, 8f);
            ModelPart.Vertex v111 = new ModelPart.Vertex(x1, y1, z1, 8f, 8f);
            ModelPart.Vertex v011 = new ModelPart.Vertex(x0, y1, z1, 8f, 0f);

            Map<Direction, ModelPart.Vertex[]> faceVerts = new EnumMap<>(Direction.class);
            faceVerts.put(Direction.DOWN, new ModelPart.Vertex[]{v101, v001, v000, v100});
            faceVerts.put(Direction.UP, new ModelPart.Vertex[]{v110, v010, v011, v111});
            faceVerts.put(Direction.WEST, new ModelPart.Vertex[]{v000, v001, v011, v010});
            faceVerts.put(Direction.NORTH, new ModelPart.Vertex[]{v100, v000, v010, v110});
            faceVerts.put(Direction.EAST, new ModelPart.Vertex[]{v101, v100, v110, v111});
            faceVerts.put(Direction.SOUTH, new ModelPart.Vertex[]{v001, v101, v111, v011});

            for (Direction dir : Direction.values()) {
                Direction uvKeyDir = dir.getAxis() == Direction.Axis.Z ? dir : dir.getOpposite();
                String key = uvKeyDir.getName();
                if (facesObj.has(key)) {
                    JsonObject fObj = facesObj.getAsJsonObject(key);
                    JsonArray uvPos = fObj.getAsJsonArray("uv");
                    JsonArray uvSz = fObj.getAsJsonArray("uv_size");
                    float u0 = uvPos.get(0).getAsFloat(), v0 = uvPos.get(1).getAsFloat();
                    float du = uvSz.get(0).getAsFloat(), dv = uvSz.get(1).getAsFloat();
                    if (Math.abs(du) < 1e-4f && Math.abs(dv) < 1e-4f) continue;
                    out.add(new ModelPart.Polygon(faceVerts.get(dir), u0, v0, u0 + du, v0 + dv, texW, texH, false, dir));
                }
            }
        }
    }

    private static void bakePolyMesh(JsonObject meshObj, Vector3f bonePivot, int texW, int texH, List<ModelPart.Polygon> out) {
        boolean normUvs = meshObj.has("normalized_uvs") && meshObj.get("normalized_uvs").getAsBoolean();
        JsonArray posArr = meshObj.getAsJsonArray("positions");
        JsonArray uvArr = meshObj.getAsJsonArray("uvs");
        JsonArray polysArr = meshObj.getAsJsonArray("polys");
        if (posArr == null || uvArr == null || polysArr == null) return;

        Vector3f[] positions = new Vector3f[posArr.size()];
        for (int i = 0; i < posArr.size(); i++) positions[i] = readVec3(posArr.get(i).getAsJsonArray());

        float[][] uvs = new float[uvArr.size()][2];
        for (int i = 0; i < uvArr.size(); i++) {
            JsonArray a = uvArr.get(i).getAsJsonArray();
            float u = a.get(0).getAsFloat() * (normUvs ? texW : 1.0f);
            float v = texH - a.get(1).getAsFloat() * (normUvs ? texH : 1.0f);
            uvs[i][0] = u / texW;
            uvs[i][1] = v / texH;
        }

        for (JsonElement faceEl : polysArr) {
            JsonArray face = faceEl.getAsJsonArray();
            if (face.size() < 3) continue;
            ModelPart.Vertex[] verts = new ModelPart.Vertex[4];
            int count = Math.min(4, face.size());
            for (int i = 0; i < count; i++) {
                JsonArray idx = face.get(i).getAsJsonArray();
                Vector3f p = positions[idx.get(0).getAsInt()];
                float[] uv = uvs[idx.get(2).getAsInt()];
                verts[i] = new ModelPart.Vertex(
                        p.x - bonePivot.x,
                        -(p.y - bonePivot.y),
                        p.z - bonePivot.z,
                        uv[0], uv[1]
                );
            }
            if (count == 3) verts[3] = verts[2];
            Vector3f e1 = new Vector3f(verts[1].x() - verts[0].x(), verts[1].y() - verts[0].y(), verts[1].z() - verts[0].z());
            Vector3f e2 = new Vector3f(verts[2].x() - verts[1].x(), verts[2].y() - verts[1].y(), verts[2].z() - verts[1].z());
            Vector3f normal = e1.cross(e2).normalize();
            if (!Float.isFinite(normal.x)) normal.set(0f, -1f, 0f);
            out.add(new ModelPart.Polygon(verts, normal));
        }
    }

    private static Vector3f readVec3(JsonArray arr) {
        return new Vector3f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat());
    }

    private static InputStream openGeoStream(String geoPath) {
        try {
            var opt = Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath("ye-mod", geoPath));
            if (opt.isPresent()) return opt.get().open();
        } catch (Exception ignored) {
        }
        return YeBedrockGeoModel.class.getResourceAsStream("/assets/ye-mod/" + geoPath);
    }
}
