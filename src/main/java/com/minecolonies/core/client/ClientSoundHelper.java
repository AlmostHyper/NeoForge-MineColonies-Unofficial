package com.minecolonies.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * PORT26 (0.7.0, server-boot fix): client-only sound plumbing, extracted from
 * {@code PlayAudioMessage.onExecute}.
 *
 * <p>Message classes are loaded on BOTH dists (they are registered in
 * {@code RegisterPayloadHandlersEvent} on the dedicated server too). Referencing
 * {@link SoundInstance}/{@link SimpleSoundInstance} directly in a message class's
 * bytecode makes the class fail verification on the server with
 * {@code NoClassDefFoundError: net/minecraft/client/resources/sounds/SoundInstance}.
 * Keeping the client sound classes inside this helper (which only ever loads on the
 * client) keeps the message class server-safe.</p>
 */
public final class ClientSoundHelper
{
    private ClientSoundHelper()
    {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Plays a one-shot sound at the receiving (client) player's position.
     *
     * @param soundEvent the sound event id
     * @param category   the sound category
     * @param player     the receiving player (the local player on the client)
     */
    public static void playAtPlayer(final Identifier soundEvent, final SoundSource category, final Player player)
    {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
          soundEvent, category,
            1.0F, 1.0F, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, player.getX(), player.getY(), player.getZ(), true));
    }
}
