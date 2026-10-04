package com.axalotl.async.forge.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** A loading overlay owns its logo until its final draw, across texture reloads. */
public final class OwnedLoadingLogo extends SimpleTexture {
    private static final ResourceLocation LOGO = new ResourceLocation("textures/gui/title/mojangstudios.png");

    public OwnedLoadingLogo(Minecraft minecraft) {
        super(LOGO);
        try { load(minecraft.getResourceManager()); }
        catch (IOException failure) { throw new UncheckedIOException("Cannot load the Forge overlay logo", failure); }
    }

    @Override
    protected TextureImage getTextureImage(ResourceManager resources) {
        var resource = resources.getResource(LOGO);
        try (InputStream input = resource.isPresent() ? resource.get().open()
                : Minecraft.getInstance().getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES, LOGO).get()) {
            return new TextureImage(new TextureMetadataSection(true, true), NativeImage.read(input));
        } catch (IOException failure) { return new TextureImage(failure); }
    }
}
