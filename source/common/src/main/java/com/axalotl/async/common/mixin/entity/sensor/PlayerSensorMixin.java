/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.ai.sensing.PlayerSensor
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.axalotl.async.common.mixin.entity.sensor;

import com.axalotl.async.common.config.AsyncConfig;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.function.ToDoubleFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.PlayerSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={PlayerSensor.class}, priority=1500)
public class PlayerSensorMixin {
    @Redirect(method={"doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"}, at=@At(value="INVOKE", target="Ljava/util/Comparator;comparingDouble(Ljava/util/function/ToDoubleFunction;)Ljava/util/Comparator;"))
    private <T extends ServerPlayer> Comparator<T> async$safeComparator(ToDoubleFunction<? super T> keyExtractor, ServerLevel world, LivingEntity entity) {
        if (AsyncConfig.disabled.getValue().booleanValue()) {
            return Comparator.comparingDouble(keyExtractor);
        }
        double ex = entity.getX();
        double ey = entity.getY();
        double ez = entity.getZ();
        IdentityHashMap<T, Double> distances = new IdentityHashMap<>();
        return Comparator.comparingDouble(target -> distances.computeIfAbsent(target, value -> {
            double dx = ex - value.getX();
            double dy = ey - value.getY();
            double dz = ez - value.getZ();
            return dx * dx + dy * dy + dz * dz;
        }));
    }

}
