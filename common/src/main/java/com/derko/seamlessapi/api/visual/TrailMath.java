package com.derko.seamlessapi.api.visual;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable trail-shaping math for projectile and slash effects.
 */
public final class TrailMath {
    private TrailMath() {
    }

    public static List<SeamlessVec3> smoothCatmullRom(List<SeamlessVec3> sourcePoints, int subdivisions, SeamlessVec3 latestPointOverride) {
        if (sourcePoints.size() < 2) {
            return sourcePoints;
        }

        List<SeamlessVec3> points = new ArrayList<>(sourcePoints);
        points.set(points.size() - 1, latestPointOverride);

        if (points.size() < 3) {
            return points;
        }

        List<SeamlessVec3> smoothed = new ArrayList<>((points.size() - 1) * subdivisions + 1);
        smoothed.add(points.get(0));

        for (int index = 0; index < points.size() - 1; index++) {
            SeamlessVec3 previous = index > 0 ? points.get(index - 1) : points.get(index);
            SeamlessVec3 current = points.get(index);
            SeamlessVec3 next = points.get(index + 1);
            SeamlessVec3 following = index + 2 < points.size() ? points.get(index + 2) : next;

            for (int step = 1; step <= subdivisions; step++) {
                float delta = step / (float) subdivisions;
                smoothed.add(catmullRom(previous, current, next, following, delta));
            }
        }

        return smoothed;
    }

    public static float widthAtProgress(float baseWidth, float progress) {
        return (float) Math.sqrt(Math.max(progress, 0.0F)) * baseWidth;
    }

    public static float alphaAtProgress(float alphaScale, float progress) {
        return (float) Math.cbrt(Math.max(0.0F, alphaScale * progress - 0.1F));
    }

    private static SeamlessVec3 catmullRom(SeamlessVec3 p0, SeamlessVec3 p1, SeamlessVec3 p2, SeamlessVec3 p3, float t) {
        double t2 = t * t;
        double t3 = t2 * t;

        return new SeamlessVec3(
            0.5D * ((2.0D * p1.x()) + (-p0.x() + p2.x()) * t + (2.0D * p0.x() - 5.0D * p1.x() + 4.0D * p2.x() - p3.x()) * t2 + (-p0.x() + 3.0D * p1.x() - 3.0D * p2.x() + p3.x()) * t3),
            0.5D * ((2.0D * p1.y()) + (-p0.y() + p2.y()) * t + (2.0D * p0.y() - 5.0D * p1.y() + 4.0D * p2.y() - p3.y()) * t2 + (-p0.y() + 3.0D * p1.y() - 3.0D * p2.y() + p3.y()) * t3),
            0.5D * ((2.0D * p1.z()) + (-p0.z() + p2.z()) * t + (2.0D * p0.z() - 5.0D * p1.z() + 4.0D * p2.z() - p3.z()) * t2 + (-p0.z() + 3.0D * p1.z() - 3.0D * p2.z() + p3.z()) * t3)
        );
    }
}
