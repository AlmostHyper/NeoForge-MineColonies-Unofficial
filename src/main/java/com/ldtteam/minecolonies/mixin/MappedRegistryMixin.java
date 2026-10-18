package com.ldtteam.minecolonies.mixin;

import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * PORT26 compat-fix v2 (0.7.1): serialize registry writes during parallel mod
 * construction — <b>without replacing the target method</b>.
 *
 * <h2>Root cause #1 (modpack CME, fixed in 0.7.0)</h2>
 * NeoForge constructs mods in parallel on {@code modloading-worker-*} threads. The
 * registry write entry point ({@code Registry.register} statics &#8594; the 3-arg
 * {@code MappedRegistry#register}) is <b>not</b> thread-safe: {@code MappedRegistry}
 * is backed by plain {@code HashMap}s and {@code HashMap.computeIfAbsent} throws
 * {@link java.util.ConcurrentModificationException} when another thread structurally
 * modifies the same map concurrently. Any two mods that (illegally, but commonly)
 * register into the <i>same</i> vanilla registry from their mod constructors can race
 * and crash the whole pack during boot — e.g. WoVer's {@code BiomeCodecRegistryImpl}
 * registration racing another construction-phase registrant (observed with Blueprint /
 * Better Archeology in the pack). The legal path (NeoForge {@code RegisterEvent}) is
 * single-threaded and unaffected.
 *
 * <h2>Root cause #2 (0.7.0 regression: WoVer crash at Bootstrap)</h2>
 * 0.7.0 fixed #1 with an {@code @Overwrite} of the 3-arg {@code register}. That turned
 * out to break third-party name-only injectors: a Mixin {@code @Overwrite} merge
 * removes the original method and re-appends the replacement at the <i>end</i> of the
 * target class's method list, reordering the {@code register} overloads. On
 * NeoForge-patched {@code MappedRegistry} there are TWO overloads — the vanilla 3-arg
 * {@code register(ResourceKey, T, RegistrationInfo)} and the NeoForge-added 4-arg
 * {@code register(int, ResourceKey, T, RegistrationInfo)}. Mods that target
 * {@code @Inject(method = "register", ...)} <b>by name only</b> (WorldWeaver/WoVer's
 * {@code MappedRegistryMixin.wover_register} does exactly this) resolve their handler
 * against the wrong overload after the reorder: the descriptor check demands a leading
 * {@code int} and the game hard-crashes in vanilla Bootstrap
 * ({@code BuiltInRegistries.<clinit>}), before any mod is constructed:
 * <pre>Invalid descriptor on wover.core.mixins.common.json:registry.MappedRegistryMixin
 *   -&gt;@Inject::wover_register(...)V! Expected (ILnet/minecraft/resources/ResourceKey;...)V
 *   but found (Lnet/minecraft/resources/ResourceKey;...)V</pre>
 * Reproduced and bisected on a clean dedicated server (NeoForge 26.1.2.122 + a
 * byte-exact replica of WoVer's mixin): wover alone boots; wover + 0.7.0-style
 * {@code @Overwrite} crashes with the exact above error; wover + this v2 mixin boots.
 *
 * <h2>Fix (v2)</h2>
 * Redirect the single internal delegation
 * {@code this.register(this.byId.size(), key, value, info)} <i>inside</i> the untouched
 * 3-arg method to a handler that (a) locks the registry, (b) re-mints the numeric id
 * <i>inside</i> the lock (the original delegation computed it before entering, so two
 * racing threads could mint the same numeric id) and (c) invokes the 4-arg
 * implementation. The 3-arg method itself keeps its name, descriptor, position and
 * flags, so other mods' injectors targeting it resolve exactly as on a pristine class.
 *
 * <ul>
 *   <li>the exact CME class (two threads writing the same registry) becomes impossible;</li>
 *   <li>writes to <i>different</i> registries still run in parallel (lock is
 *       per-registry);</li>
 *   <li>the single-threaded {@code RegisterEvent} path only pays one uncontended
 *       monitor enter/exit (~ns) per registration;</li>
 *   <li>other mods' injectors into the 3-arg method (e.g. WoVer's datapack-load
 *       tracker) keep working unchanged.</li>
 * </ul>
 *
 * <p>Known coverage notes (same as 0.7.0): the 4-arg implementation itself is left
 * untouched (NeoForge registry callbacks, immediate {@code bindValue} binding and id
 * range validation keep working); {@code DefaultedMappedRegistry} overrides the 3-arg
 * entry point with its own body, so defaulted registries keep vanilla behavior.
 * Exception-safe by construction (a {@code synchronized} block releases the monitor on
 * any throw).</p>
 *
 * <p>Mixin application stays fail-open ({@code required: false} in
 * {@code minecolonies.mixins.json}): if a future NeoForge build restructures
 * {@code MappedRegistry} such that the redirect can no longer apply, the game still
 * boots (with a mixin warning) instead of hard-crashing at startup.</p>
 */
@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin<T>
{
    /**
     * The id-ordered holder list of the target registry; shadowed to re-mint the next
     * free numeric id under the registry lock.
     */
    @Shadow
    private ObjectList<Holder.Reference<T>> byId;

    /**
     * PORT26 compat-fix: thread-safe delegation of the standard registry write entry
     * point. Redirects the {@code this.register(this.byId.size(), key, value, info)}
     * call inside the vanilla 3-arg register.
     *
     * @param instance        the registry whose 3-arg register delegated here (== this)
     * @param unlockedRawId   the id minted <i>outside</i> the lock by the original body
     *                        (discarded — re-minted under the lock)
     * @param key             the registry key of the new entry
     * @param value           the value to register
     * @param registrationInfo the registration info (lifecycle etc.)
     * @return the holder for the registered entry
     */
    @Redirect(
        method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/MappedRegistry;register(ILnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;"
        )
    )
    private Holder.Reference<T> minecolonies$synchronizedRegister(
            final MappedRegistry<T> instance,
            final int unlockedRawId,
            final ResourceKey<T> key,
            final T value,
            final RegistrationInfo registrationInfo)
    {
        synchronized (instance)
        {
            return instance.register(this.byId.size(), key, value, registrationInfo);
        }
    }
}
