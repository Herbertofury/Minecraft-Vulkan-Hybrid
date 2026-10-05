package dev.engine_room.flywheel.backend.compile;
import dev.engine_room.flywheel.backend.glsl.ShaderSources;
import net.minecraft.server.packs.resources.ResourceManager;
import mvhflywheelbackend.NativeShaderSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/** Original resource-loader entrypoint now rebuilds the actual native source graph/cache. */
public final class FlwPrograms {
 public static final Logger LOGGER=LoggerFactory.getLogger("flywheel/backend/native-shaders");
 public static ShaderSources SOURCES;
 static void reload(ResourceManager manager){SOURCES=new ShaderSources(manager);NativeShaderSources.reload(SOURCES);}
}
