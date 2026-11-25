package com.ldtteam.structurize.client;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import net.minecraft.client.Minecraft;

/**
 * Client-only helpers for blueprint preview data.
 * <p>
 * PORT26: upstream marked BlueprintPreviewData#applyRotationMirrorAndSync with @OnlyIn(Dist.CLIENT) so the
 * method was stripped on dedicated servers. NeoForge 26.1 no longer strips @OnlyIn members, so the
 * {@code Minecraft.getInstance().level} (type ClientLevel) reference stayed in a class that the server loads
 * when decoding SyncPreviewCacheToServer. Bytecode verification of that method then needs to load ClientLevel
 * (to check it is assignable to Level), which throws NoClassDefFoundError on a dedicated server.
 * Keeping the reference in this separate class means it is only loaded when actually called on the client.
 */
public final class ClientBlueprintHelper
{
    private ClientBlueprintHelper()
    {
    }

    /**
     * Apply rotation/mirror to a blueprint using the client level.
     *
     * @param blueprint      the blueprint.
     * @param rotationMirror the rotation/mirror to apply.
     */
    public static void applyRotationMirror(final Blueprint blueprint, final RotationMirror rotationMirror)
    {
        blueprint.setRotationMirror(rotationMirror, Minecraft.getInstance().level);
    }
}
