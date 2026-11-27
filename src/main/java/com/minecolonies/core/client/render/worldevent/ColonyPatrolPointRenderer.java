package com.minecolonies.core.client.render.worldevent;

import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.items.component.BuildingId;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import net.minecraft.core.BlockPos;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.minecolonies.api.util.constant.Constants.STORAGE_STYLE;

public class ColonyPatrolPointRenderer
{
    /**
     * Cached wayPointBlueprint.
     */
    private static BlueprintPreviewData partolPointTemplate;

    private static Future<Blueprint> pendingTemplate;

    /**
     * Renders the guard scepter objects into the world.
     * 
     * @param ctx rendering context
     */
    static void render(final WorldEventContext ctx)
    {
        if (ctx.mainHandItem.getItem() != ModItems.scepterGuard)
        {
            return;
        }

        final IBuildingView guardTowerView = BuildingId.readBuildingViewFromItemStack(ctx.mainHandItem);
        if (guardTowerView == null)
        {
            return;
        }

        if (pendingTemplate == null && partolPointTemplate == null)
        {
            pendingTemplate = StructurePacks.getBlueprintFuture(STORAGE_STYLE, "infrastructure/misc/patrolpoint.blueprint", ctx.clientLevel.registryAccess());
            return;
        }
        else if (pendingTemplate != null && pendingTemplate.isDone())
        {
            try
            {
                final BlueprintPreviewData tempPreviewData = new BlueprintPreviewData();
                tempPreviewData.setBlueprint(pendingTemplate.get());
                tempPreviewData.setPos(BlockPos.ZERO);
                partolPointTemplate = tempPreviewData;
                pendingTemplate = null;
            }
            catch (final InterruptedException | ExecutionException e)
            {
                // PORT26 FIX (0.7.5, hardening): was printStackTrace; a failed future also left
                // pendingTemplate set, so this retried (and spammed stderr) on every frame.
                Log.getLogger().error("Failed to load patrol point template blueprint, patrol points will not render", e);
                pendingTemplate = null;
            }
            return;
        }

        if (guardTowerView instanceof AbstractBuildingGuards.View guardTower)
        {
            ctx.renderBlueprint(partolPointTemplate, guardTower.getPatrolTargets());
        }
    }
}
