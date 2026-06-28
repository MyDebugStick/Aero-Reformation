package dev.simulated_team.aero_reformation.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Prevents radar_fix mixins from loading when Create Radar is not installed.
 * Uses class existence check instead of ModList (not available during mixin phase).
 */
public class RadarCompatPlugin implements IMixinConfigPlugin {

    private static final String RADAR_FIX_PREFIX = "dev.simulated_team.aero_reformation.mixin.radar_fix.";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(RADAR_FIX_PREFIX)) {
            // Check if the target class exists without loading it (avoids interfering with Mixin pipeline)
            String resourcePath = targetClassName.replace('.', '/') + ".class";
            return getClass().getClassLoader().getResource(resourcePath) != null;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
