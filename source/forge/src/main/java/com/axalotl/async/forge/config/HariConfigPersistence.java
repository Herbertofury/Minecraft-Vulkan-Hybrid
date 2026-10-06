package com.axalotl.async.forge.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "harimt", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HariConfigPersistence {
    private static volatile CommentedFileConfig config;
    private HariConfigPersistence() {}

    @SubscribeEvent
    public static void bind(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == AsyncConfigForge.SPEC) {
            config = (CommentedFileConfig) event.getConfig().getConfigData();
        }
    }

    public static synchronized void save(Map<String, Object> snapshot) {
        CommentedFileConfig loaded = config;
        if (loaded == null) throw new IllegalStateException("Hari configuration has not loaded");
        AtomicConfigPersistence.save(loaded.getNioPath(), snapshot);
        loaded.load();
        AsyncConfigForge.SPEC.afterReload();
    }
}
