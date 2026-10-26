package com.axalotl.async.forge;

import com.axalotl.async.common.AsyncCommon;
import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.commands.AsyncCommand;
import com.axalotl.async.common.commands.StatsCommand;
import com.axalotl.async.forge.platform.ForgePermissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import org.slf4j.Logger;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import static com.axalotl.async.common.config.AsyncConfig.getParallelism;
import static com.axalotl.async.forge.config.AsyncConfigForge.SPEC;
import static com.axalotl.async.forge.config.AsyncConfigForge.loadConfig;

@Mod(AsyncForge.MOD_ID)
public class AsyncForge extends AsyncCommon {
    public static final String MOD_ID = "harimt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AsyncForge(FMLJavaModLoadingContext context) {
        LOGGER.info("Initializing Async...");
        com.axalotl.async.common.CpuWorkBudget.configure(
                net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT,
                AsyncCommon.HARICHUNK);
        LOGGER.info("Hari CPU budget: entityWorkers={} meshWorkers={} externalChunks={}",
                com.axalotl.async.common.CpuWorkBudget.entityWorkers(),
                com.axalotl.async.common.CpuWorkBudget.meshWorkers(), AsyncCommon.HARICHUNK);
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            if (net.vulkanmod.compat.UniversalRendererGate.vulkanRendererEnabled()) {
                LOGGER.info("Hari 2.4 selected merged Vulkan renderer: {}", net.vulkanmod.compat.UniversalRendererGate.reason());
                new net.vulkanmod.Initializer();
            } else {
                LOGGER.info("Hari 2.4 selected OpenGL compatibility renderer: {}", net.vulkanmod.compat.UniversalRendererGate.reason());
            }
        }
        MinecraftForge.EVENT_BUS.register(this);
        if (Boolean.getBoolean("harimt.qa.entityFault")) {
            MinecraftForge.EVENT_BUS.register(new com.axalotl.async.forge.qa.HariServerFailureProbe());
        }
        LOGGER.info("Initializing Async Config...");
        context.registerConfig(ModConfig.Type.COMMON, SPEC, "harimt.toml");
        LOGGER.info("Async Initialized successfully");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Async Setting up thread-pool...");
        this.initialize();
        loadConfig();
        StatsCommand.runStatsThread();
        ParallelProcessor.setServer(event.getServer());
        ParallelProcessor.setupThreadPool(getParallelism(), this.getClass());
    }

    @SubscribeEvent
    public void registerCommandsEvent(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        AsyncCommand.register(dispatcher);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        StatsCommand.shutdown();
        ParallelProcessor.stop();
    }

    @SubscribeEvent
    public void handlePermissionNodesGather(PermissionGatherEvent.Nodes event) {
        ForgePermissions.addNodes(event);
    }
}
