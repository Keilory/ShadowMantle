package com.keilory.shadowmantle.core;

/**
 * Common lifecycle contract for ShadowMantle subsystems.
 * Features should keep expensive work out of the render path.
 */
public interface Feature {
    String id();

    default void initialize(ShadowMantleCore core) {
    }

    default void shutdown() {
    }
}
