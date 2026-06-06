package dev.simulated_team.aero_reformation.mixin.feature.physics_anchor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.map.gui.GuiMap;

@Mixin(value = GuiMap.class, remap = false)
public interface XaeroMapAccessor {
    @Accessor("cameraX") double aero$getCameraX();
    @Accessor("cameraZ") double aero$getCameraZ();
    @Accessor("scale") double aero$getScale();
}
