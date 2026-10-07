package com.shoecommerce.catalog;

public record ProductEvidence(
        IntendedUse intendedUse,
        PrimarySurface primarySurface,
        UpperConstruction upperConstruction,
        CutProfile cutProfile,
        PrimarySoleProfile primarySoleProfile) {

    public enum IntendedUse { CASUAL_DAILY, ROAD_RUNNING, TRAIL_WALKING, TRAIL_RUNNING }
    public enum PrimarySurface { SMOOTH_HARD_SURFACE, ROAD_TRACK, MIXED_TRAIL }
    public enum UpperConstruction {
        SYNTHETIC_LEATHER,
        CANVAS_TEXTILE,
        MESH_TEXTILE,
        KNIT_TEXTILE,
        WOVEN_TEXTILE,
        MESH_TEXTILE_WITH_SYNTHETIC_OVERLAYS
    }
    public enum CutProfile { LOW_TOP, HIGH_TOP }
    public enum PrimarySoleProfile { CUPSOLE, FLEX_GROOVED, LUGGED }

    public static ProductEvidence empty() {
        return new ProductEvidence(null, null, null, null, null);
    }

    public boolean isEmpty() {
        return intendedUse == null && primarySurface == null && upperConstruction == null
                && cutProfile == null && primarySoleProfile == null;
    }
}
