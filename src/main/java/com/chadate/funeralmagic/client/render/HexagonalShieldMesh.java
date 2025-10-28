package com.chadate.funeralmagic.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class HexagonalShieldMesh {

       private static final float LINE_WIDTH = 0.025f; // 线条宽度

       private static int subdivisionLevel = 2;

       public static void updateSubdivisionLevel(int level) {
              subdivisionLevel = Math.max(0, Math.min(3, level));
       }

       public static void renderHexagonalShield(VertexConsumer consumer, Matrix4f matrix,
                     double radius, float r, float g, float b,
                     float alpha, float time, Vec3 shieldCenter) {

              GeodesicSphere sphere = new GeodesicSphere((float) radius, subdivisionLevel);

              // 渲染所有边
              for (Edge edge : sphere.edges) {
                     Vec3 start = edge.v1;
                     Vec3 end = edge.v2;

                     // 计算边的中点位置
                     Vec3 midPoint = new Vec3(
                                   (start.x + end.x) * 0.5,
                                   (start.y + end.y) * 0.5,
                                   (start.z + end.z) * 0.5);

                     // 计算能量效果
                     float energyFlow = calculateEnergyFlow(midPoint, time, edge.index / 20, edge.index % 20);
                     float impactInfluence = ShieldImpactEffect.getImpactInfluence(
                                   shieldCenter.add(midPoint), shieldCenter, radius);
                     float flashIntensity = ShieldImpactEffect.getFlashIntensity(
                                   shieldCenter.add(midPoint), shieldCenter, radius);

                     float brightness = 1.0f + energyFlow * 0.5f + impactInfluence * 5.0f + flashIntensity * 8.0f;
                     float lineAlpha = alpha * (0.6f + energyFlow * 0.4f + impactInfluence * 2.0f);

                     // 渲染边
                     renderLine(consumer, matrix, start, end,
                                   r * brightness, g * brightness, b * brightness, lineAlpha);
              }
       }

       /**
        * 测地线球类（基于正二十面体细分）
        */
       private static class GeodesicSphere {
              List<Vec3> vertices = new ArrayList<>();
              List<Edge> edges = new ArrayList<>();

              GeodesicSphere(float radius, int subdivisions) {
                     // 黄金比例
                     float t = (1.0f + Mth.sqrt(5.0f)) / 2.0f;

                     // 初始正二十面体的12个顶点
                     addVertex(normalize(new Vec3(-1, t, 0), radius));
                     addVertex(normalize(new Vec3(1, t, 0), radius));
                     addVertex(normalize(new Vec3(-1, -t, 0), radius));
                     addVertex(normalize(new Vec3(1, -t, 0), radius));

                     addVertex(normalize(new Vec3(0, -1, t), radius));
                     addVertex(normalize(new Vec3(0, 1, t), radius));
                     addVertex(normalize(new Vec3(0, -1, -t), radius));
                     addVertex(normalize(new Vec3(0, 1, -t), radius));

                     addVertex(normalize(new Vec3(t, 0, -1), radius));
                     addVertex(normalize(new Vec3(t, 0, 1), radius));
                     addVertex(normalize(new Vec3(-t, 0, -1), radius));
                     addVertex(normalize(new Vec3(-t, 0, 1), radius));

                     // 20个三角形面
                     List<Triangle> faces = new ArrayList<>();
                     faces.add(new Triangle(0, 11, 5));
                     faces.add(new Triangle(0, 5, 1));
                     faces.add(new Triangle(0, 1, 7));
                     faces.add(new Triangle(0, 7, 10));
                     faces.add(new Triangle(0, 10, 11));

                     faces.add(new Triangle(1, 5, 9));
                     faces.add(new Triangle(5, 11, 4));
                     faces.add(new Triangle(11, 10, 2));
                     faces.add(new Triangle(10, 7, 6));
                     faces.add(new Triangle(7, 1, 8));

                     faces.add(new Triangle(3, 9, 4));
                     faces.add(new Triangle(3, 4, 2));
                     faces.add(new Triangle(3, 2, 6));
                     faces.add(new Triangle(3, 6, 8));
                     faces.add(new Triangle(3, 8, 9));

                     faces.add(new Triangle(4, 9, 5));
                     faces.add(new Triangle(2, 4, 11));
                     faces.add(new Triangle(6, 2, 10));
                     faces.add(new Triangle(8, 6, 7));
                     faces.add(new Triangle(9, 8, 1));

                     // 细分
                     for (int i = 0; i < subdivisions; i++) {
                            List<Triangle> newFaces = new ArrayList<>();
                            for (Triangle tri : faces) {
                                   Vec3 v1 = vertices.get(tri.v1);
                                   Vec3 v2 = vertices.get(tri.v2);
                                   Vec3 v3 = vertices.get(tri.v3);

                                   Vec3 a = normalize(midpoint(v1, v2), radius);
                                   Vec3 b = normalize(midpoint(v2, v3), radius);
                                   Vec3 c = normalize(midpoint(v3, v1), radius);

                                   int ia = addVertex(a);
                                   int ib = addVertex(b);
                                   int ic = addVertex(c);

                                   newFaces.add(new Triangle(tri.v1, ia, ic));
                                   newFaces.add(new Triangle(tri.v2, ib, ia));
                                   newFaces.add(new Triangle(tri.v3, ic, ib));
                                   newFaces.add(new Triangle(ia, ib, ic));
                            }
                            faces = newFaces;
                     }

                     // 生成边
                     int edgeIndex = 0;
                     for (Triangle tri : faces) {
                            edges.add(new Edge(vertices.get(tri.v1), vertices.get(tri.v2), edgeIndex++));
                            edges.add(new Edge(vertices.get(tri.v2), vertices.get(tri.v3), edgeIndex++));
                            edges.add(new Edge(vertices.get(tri.v3), vertices.get(tri.v1), edgeIndex++));
                     }
              }

              private int addVertex(Vec3 v) {
                     for (int i = 0; i < vertices.size(); i++) {
                            Vec3 existing = vertices.get(i);
                            if (existing.distanceTo(v) < 0.0001) {
                                   return i;
                            }
                     }
                     vertices.add(v);
                     return vertices.size() - 1;
              }

              private Vec3 normalize(Vec3 v, float radius) {
                     double length = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
                     return new Vec3(
                                   v.x / length * radius,
                                   v.y / length * radius,
                                   v.z / length * radius);
              }

              private Vec3 midpoint(Vec3 v1, Vec3 v2) {
                     return new Vec3(
                                   (v1.x + v2.x) * 0.5,
                                   (v1.y + v2.y) * 0.5,
                                   (v1.z + v2.z) * 0.5);
              }
       }

       private static class Triangle {
              int v1, v2, v3;

              Triangle(int v1, int v2, int v3) {
                     this.v1 = v1;
                     this.v2 = v2;
                     this.v3 = v3;
              }
       }

       private static class Edge {
              Vec3 v1, v2;
              int index;

              Edge(Vec3 v1, Vec3 v2, int index) {
                     this.v1 = v1;
                     this.v2 = v2;
                     this.index = index;
              }
       }

       /**
        * 计算能量流动效果
        */
       private static float calculateEnergyFlow(Vec3 pos, float time, int lat, int lon) {
              float distanceFromTop = (float) Math.abs(pos.y);
              float positionHash = lat * 0.3f + lon * 0.2f;
              return (Mth.sin(time * 0.5f + distanceFromTop * 1.5f + positionHash) + 1.0f) * 0.5f;
       }

       /**
        * 渲染线条
        */
       private static void renderLine(VertexConsumer consumer, Matrix4f matrix,
                     Vec3 start, Vec3 end, float r, float g, float b, float alpha) {

              Vector3f startVec = new Vector3f((float) start.x, (float) start.y, (float) start.z);
              Vector3f endVec = new Vector3f((float) end.x, (float) end.y, (float) end.z);

              // 计算线条的方向和法向量
              Vector3f direction = new Vector3f(endVec).sub(startVec).normalize();
              Vector3f toCenter = new Vector3f(startVec).normalize();
              Vector3f perpendicular = new Vector3f(direction).cross(toCenter).normalize();

              // 创建线条的四个顶点（形成有宽度的线条）
              float halfWidth = LINE_WIDTH;
              Vector3f offset = new Vector3f(perpendicular).mul(halfWidth);

              Vector3f v1 = new Vector3f(startVec).sub(offset);
              Vector3f v2 = new Vector3f(startVec).add(offset);
              Vector3f v3 = new Vector3f(endVec).add(offset);
              Vector3f v4 = new Vector3f(endVec).sub(offset);

              // 渲染线条为四边形（两个三角形）
              // 三角形 1
              consumer.addVertex(matrix, v1.x, v1.y, v1.z)
                            .setColor(r, g, b, alpha);
              consumer.addVertex(matrix, v2.x, v2.y, v2.z)
                            .setColor(r, g, b, alpha);
              consumer.addVertex(matrix, v3.x, v3.y, v3.z)
                            .setColor(r, g, b, alpha);

              // 三角形 2
              consumer.addVertex(matrix, v1.x, v1.y, v1.z)
                            .setColor(r, g, b, alpha);
              consumer.addVertex(matrix, v3.x, v3.y, v3.z)
                            .setColor(r, g, b, alpha);
              consumer.addVertex(matrix, v4.x, v4.y, v4.z)
                            .setColor(r, g, b, alpha);
       }

}
