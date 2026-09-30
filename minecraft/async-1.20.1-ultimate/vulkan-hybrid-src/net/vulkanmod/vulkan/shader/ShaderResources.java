package net.vulkanmod.vulkan.shader;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import net.minecraft.FileUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.client.ForgeHooksClient;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Resolve the active resource pack's program names and imports exactly as Forge does. */
public final class ShaderResources {
    private ShaderResources() {}

    public record Programs(ResourceLocation vertex, ResourceLocation fragment) {}

    public static Programs resolve(ResourceProvider resources, ResourceLocation shader) throws IOException {
        ResourceLocation json = new ResourceLocation(shader.getNamespace(), "shaders/core/" + shader.getPath() + ".json");
        try (Reader reader = resources.openAsReader(json)) {
            JsonObject definition = GsonHelper.parse(reader);
            return new Programs(program(GsonHelper.getAsString(definition, "vertex"), ".vsh"),
                    program(GsonHelper.getAsString(definition, "fragment"), ".fsh"));
        }
    }

    private static ResourceLocation program(String name, String extension) {
        ResourceLocation location = new ResourceLocation(name);
        return new ResourceLocation(location.getNamespace(), "shaders/core/" + location.getPath() + extension);
    }

    public static String read(ResourceProvider resources, ResourceLocation program) throws IOException {
        String source;
        try (InputStream stream = resources.getResourceOrThrow(program).open()) {
            source = IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
        String directory = FileUtil.getFullResourcePath(program.getPath());
        GlslPreprocessor preprocessor = new GlslPreprocessor() {
            private final Set<String> imported = new HashSet<>();

            @Override
            public String applyImport(boolean relative, String requested) {
                ResourceLocation location = ForgeHooksClient.getShaderImportLocation(directory, relative, requested);
                if (!imported.add(location.toString())) return null;
                try (Reader reader = resources.openAsReader(location)) {
                    return IOUtils.toString(reader);
                } catch (IOException failure) {
                    throw new UncheckedIOException("Cannot load shader import " + location + " for " + program, failure);
                }
            }
        };
        return String.join("", preprocessor.process(source));
    }
}
