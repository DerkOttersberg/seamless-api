package com.derko.seamlessapi.api.visual;

/**
 * Lightweight immutable 3D vector used by visual helper APIs.
 */
public record SeamlessVec3(double x, double y, double z) {
    public static final SeamlessVec3 ZERO = new SeamlessVec3(0.0D, 0.0D, 0.0D);

    public SeamlessVec3 add(SeamlessVec3 other) {
        return new SeamlessVec3(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public SeamlessVec3 subtract(SeamlessVec3 other) {
        return new SeamlessVec3(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public SeamlessVec3 multiply(double scalar) {
        return new SeamlessVec3(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    public double dot(SeamlessVec3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    public SeamlessVec3 cross(SeamlessVec3 other) {
        return new SeamlessVec3(
            this.y * other.z - this.z * other.y,
            this.z * other.x - this.x * other.z,
            this.x * other.y - this.y * other.x
        );
    }

    public double lengthSquared() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public SeamlessVec3 normalize() {
        double lengthSquared = lengthSquared();
        if (lengthSquared < 1.0E-12D) {
            return ZERO;
        }

        double inverseLength = 1.0D / Math.sqrt(lengthSquared);
        return new SeamlessVec3(this.x * inverseLength, this.y * inverseLength, this.z * inverseLength);
    }
}
