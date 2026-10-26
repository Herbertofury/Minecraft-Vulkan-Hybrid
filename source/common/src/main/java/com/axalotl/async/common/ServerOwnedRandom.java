package com.axalotl.async.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/** Keeps C2ME's checked random and its original sequence on the actual owner thread. */
public final class ServerOwnedRandom implements RandomSource {
    private final ServerLevel world;
    private final RandomSource delegate;

    public ServerOwnedRandom(ServerLevel world, RandomSource delegate) {
        this.world = world;
        this.delegate = delegate;
    }

    @Override public RandomSource fork() {
        RandomSource value = C2meThreadBoundary.mustHandoff()
                ? C2meThreadBoundary.call(world, delegate::fork) : delegate.fork();
        return new ServerOwnedRandom(world, value);
    }
    @Override public PositionalRandomFactory forkPositional() {
        return C2meThreadBoundary.mustHandoff()
                ? C2meThreadBoundary.call(world, delegate::forkPositional) : delegate.forkPositional();
    }
    @Override public void setSeed(long seed) {
        if (C2meThreadBoundary.mustHandoff()) C2meThreadBoundary.run(world, () -> delegate.setSeed(seed));
        else delegate.setSeed(seed);
    }
    @Override public int nextInt() {
        return C2meThreadBoundary.mustHandoff() ? C2meThreadBoundary.call(world, delegate::nextInt) : delegate.nextInt();
    }
    @Override public int nextInt(int bound) {
        return C2meThreadBoundary.mustHandoff() ? C2meThreadBoundary.call(world, () -> delegate.nextInt(bound)) : delegate.nextInt(bound);
    }
    @Override public long nextLong() {
        return C2meThreadBoundary.mustHandoff() ? C2meThreadBoundary.call(world, delegate::nextLong) : delegate.nextLong();
    }
    @Override public boolean nextBoolean() {
        return C2meThreadBoundary.mustHandoff() ? C2meThreadBoundary.call(world, delegate::nextBoolean) : delegate.nextBoolean();
    }
    @Override public float nextFloat() {
        return C2meThreadBoundary.mustHandoff() ? C2meThreadBoundary.call(world, delegate::nextFloat) : delegate.nextFloat();
    }
    @Override public double nextDouble() {
        return C2meThreadBoundary.mustHandoff() ? C2meThreadBoundary.call(world, delegate::nextDouble) : delegate.nextDouble();
    }
    @Override public double nextGaussian() {
        return C2meThreadBoundary.mustHandoff() ? C2meThreadBoundary.call(world, delegate::nextGaussian) : delegate.nextGaussian();
    }
}
