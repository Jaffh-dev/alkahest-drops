package com.jaffh.alkahestcore.fluidscore.client;

import com.jaffh.alkahestcore.AlkahestCore;
import com.jaffh.alkahestcore.fluidscore.common.fluids.BaseFluid;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.math.Vector4f;
import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side rendering subsystem responsible for manipulating camera fog effects.
 * <p>This class automatically hooks into Forge's event bus on the client distribution layer.
 * When the player's camera viewport becomes submerged within any fluid extending {@link BaseFluid},
 * it intercepts vanilla fog metrics to inject custom color, density thresholds, and structural shapes.</p>
 */
@Mod.EventBusSubscriber(modid = AlkahestCore.ID, value = Dist.CLIENT)
public class FluidFogRender {

    /**
     * Intercepts the fog rendering layout pass to establish custom depth, thickness, and distribution geometry.
     * <p>Utilizes the {@code w} component (Alpha) of the fluid's fog color vector as an inverse
     * visibility/density multiplier to calculate the far-plane cutoff boundaries.</p>
     *
     * @param event The active render fog configuration event payload.
     */
    @SubscribeEvent
    public static void onFogRender(EntityViewRenderEvent.RenderFogEvent event){
        Camera camera = event.getCamera();
        FluidState fluidState = camera.getBlockAtCamera().getFluidState();

        if(fluidState.getType() instanceof BaseFluid baseFluid){
            Vector4f fogRGBA = baseFluid.attributes().fogColor();

            // An alpha/w value of 0.0 or lower signifies a transparent or disabled custom fog profile
            if(fogRGBA.w() <= 0.0f){
                return;
            }

            // Enforces spherical fog distribution around the camera viewport orientation
            event.setFogShape(FogShape.SPHERE);

            float minDistance = 0.3f;
            float maxDistance = 10f;

            // Scales the fog thickness dynamically; a higher alpha reduces maximum visibility range
            float fogEnd = Math.max(minDistance, maxDistance - (fogRGBA.w() * (maxDistance - minDistance)));
            // Setting the near plane to a negative value ensures fog completely blankets the player's immediate eyesight boundary
            float fogStart = -1.0f;

            event.setFarPlaneDistance(fogEnd);
            event.setNearPlaneDistance(fogStart);

            // Cancels the event to prevent vanilla or generic liquid fog profiles from overwriting these calculations
            event.setCanceled(true);
        }
    }

    /**
     * Intercepts the color configuration pass to apply custom tonal shading to the environmental fog.
     * Maps the vector dimensions linearly into active display channels.
     *
     * @param event The active fog color mutation event payload.
     */
    @SubscribeEvent
    public static void onFogColor(EntityViewRenderEvent.FogColors event){
        Camera camera = event.getCamera();
        FluidState fluidState = camera.getBlockAtCamera().getFluidState();

        if(fluidState.getType() instanceof BaseFluid baseFluid){
            Vector4f fogRGBA = baseFluid.attributes().fogColor();

            // An alpha/w value of 0.0 or lower signifies a transparent or disabled custom fog profile
            if(fogRGBA.w() <= 0.0f){
                return;
            }

            // Maps vector indices directly into standard Red, Green, and Blue color buffer limits
            event.setRed(fogRGBA.x());
            event.setRed(fogRGBA.x());
            event.setGreen(fogRGBA.y());
            event.setBlue(fogRGBA.z());
        }
    }
}
