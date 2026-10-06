/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.entity.ai.attributes.AttributeInstance
 *  net.minecraft.world.entity.ai.attributes.AttributeModifier
 *  net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={AttributeInstance.class})
public class AttributeInstanceMixin {
    @Shadow
    @Final
    @Mutable
    private Map<UUID, AttributeModifier> modifierById;
    @Shadow
    @Final
    @Mutable
    private Set<AttributeModifier> permanentModifiers;
    @Shadow
    @Final
    @Mutable
    private Map<AttributeModifier.Operation, Set<AttributeModifier>> modifiersByOperation;

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void makeThreadSafe(CallbackInfo ci) {
        Map<UUID, AttributeModifier> ids = ConcurrentCollections.newHashMap();
        ids.putAll(this.modifierById);
        this.modifierById = ids;

        Set<AttributeModifier> permanent = ConcurrentCollections.newHashSet();
        permanent.addAll(this.permanentModifiers);
        this.permanentModifiers = permanent;

        Map<AttributeModifier.Operation, Set<AttributeModifier>> byOperation = ConcurrentCollections.newHashMap();
        this.modifiersByOperation.forEach((operation, modifiers) -> {
            Set<AttributeModifier> concurrent = ConcurrentCollections.newHashSet();
            concurrent.addAll(modifiers);
            byOperation.put(operation, concurrent);
        });
        this.modifiersByOperation = byOperation;
    }

    /**
     * @author HariMultiThread Ultimate
     * @reason Preserve vanilla semantics while ensuring newly-created operation
     *         buckets remain safe under HMT's concurrent entity ticking.
     */
    @Overwrite
    public Set<AttributeModifier> getModifiers(AttributeModifier.Operation operation) {
        return this.modifiersByOperation.computeIfAbsent(operation, key -> ConcurrentCollections.newHashSet());
    }
}

