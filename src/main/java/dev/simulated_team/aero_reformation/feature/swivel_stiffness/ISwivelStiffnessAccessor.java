package dev.simulated_team.aero_reformation.feature.swivel_stiffness;

/**
 * Accessor interface for the swivel bearing stiffness level.
 * MUST be outside the mixin package to avoid IllegalClassLoadError.
 */
public interface ISwivelStiffnessAccessor {
    int aero_reformation$getStiffnessLevel();
    void aero_reformation$cycleStiffnessLevel();
    double aero_reformation$getStiffnessMultiplier();
}
