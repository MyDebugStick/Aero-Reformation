package dev.simulated_team.aero_reformation.feature.nav_inverted;

/**
 * Accessor interface for the inverted redstone state on NavTableBlockEntity.
 * Implemented by NavTableBlockEntityMixin.
 * <p>
 * NOTE: This interface MUST NOT be in the mixin package! Mixin framework disallows
 * non-mixin classes (like interfaces) in the defined mixin package to be
 * referenced externally. See crash: IllegalClassLoadError for details.
 */
public interface INavTableAccessor {
    boolean aero_reformation$isInverted();
    void aero_reformation$toggleInverted();
}
