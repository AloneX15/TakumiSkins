package com.takumistudios.takumi_skins.client;

import com.mojang.authlib.GameProfile;
import com.takumistudios.takumi_skins.SkinProtocol.Profile;
import com.takumistudios.takumi_skins.TakumiSkins;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.client.resources.DefaultPlayerSkin;
//? if >=26.1 {
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
//?} else {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}

/**
 * Puts a player's Takumi skin and cape on top of the skin Minecraft resolved. Only the data changes (which texture,
 * which arm model): renderers, shaders and other mods draw it like any skin. Used by the mixins on {@code PlayerInfo}
 * and {@code SkinManager}; on any problem the original skin is returned.
 */
public final class SkinOverlay {
    private static final AtomicLong HOOK_CALLS = new AtomicLong();
    private static final Map<UUID, Memo> MEMO = new ConcurrentHashMap<>();
    private static volatile boolean broken;

    /** Last result per player, so the per-frame lookup does not allocate. */
    private record Memo(PlayerSkin original, Profile profile, PlayerSkin result) {
    }

    private SkinOverlay() {
    }

    /** How many times a skin hook ran; the startup checks use it to prove the mixins are applied. */
    public static long hookCalls() {
        return HOOK_CALLS.get();
    }

    /** The original skin with the Takumi skin/cape on top, or the original itself. Never throws. */
    public static PlayerSkin apply(GameProfile profile, PlayerSkin original) {
        if (broken || original == null || profile == null) {
            return original;
        }
        try {
            UUID id = id(profile);
            Profile takumi = SkinRegistry.current(id, name(profile));
            if (takumi == null) {
                return original;
            }
            Memo memo = MEMO.get(id);
            if (memo != null && memo.original() == original && memo.profile() == takumi) {
                return memo.result();
            }
            PlayerSkin result = build(original, takumi);
            MEMO.put(id, new Memo(original, takumi, result));
            if (MEMO.size() > 512) {
                MEMO.clear();
            }
            return result;
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            TakumiSkins.LOGGER.error("Takumi Skins no es compatible con esta partida; se muestran las skins normales", e);
            return original;
        }
    }

    private static PlayerSkin build(PlayerSkin original, Profile takumi) {
        //? if >=26.1 {
        Optional<ClientAsset.ResourceTexture> body = Optional.ofNullable(takumi.skin()).map(SkinOverlay::texture);
        Optional<ClientAsset.ResourceTexture> cape = Optional.ofNullable(takumi.cape()).map(SkinOverlay::texture);
        Optional<PlayerModelType> model = takumi.skin() == null ? Optional.empty()
                : Optional.of(takumi.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE);
        return original.with(PlayerSkin.Patch.create(body, cape, cape, model));
        //?} else {
        /*boolean skin = takumi.skin() != null;
        boolean cape = takumi.cape() != null;
        // secure = true: PlayerInfo replaces insecure skins of other players with the default one.
        return new PlayerSkin(
                skin ? SkinTextures.id(takumi.skin()) : original.texture(),
                skin ? null : original.textureUrl(),
                cape ? SkinTextures.id(takumi.cape()) : original.capeTexture(),
                cape ? SkinTextures.id(takumi.cape()) : original.elytraTexture(),
                skin ? (takumi.slim() ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE) : original.model(),
                true);
        *///?}
    }

    //? if >=26.1 {
    private static ClientAsset.ResourceTexture texture(String sha) {
        // Both ids point at the registered DynamicTexture (the one-argument form would look under textures/…png).
        return new ClientAsset.ResourceTexture(SkinTextures.id(sha), SkinTextures.id(sha));
    }
    //?}

    /** Wraps a skin lookup so each call adds the Takumi skin. Idempotent; unchanged when Takumi Skins is inactive. */
    public static Supplier<PlayerSkin> wrap(GameProfile profile, Supplier<PlayerSkin> lookup) {
        HOOK_CALLS.incrementAndGet();
        if (lookup == null || profile == null || lookup instanceof TakumiSkinLookup || !SkinRegistry.active()) {
            return lookup;
        }
        return new TakumiSkinLookup(profile, lookup);
    }

    /** Supplier marker: a lookup already wrapped by Takumi Skins. */
    public record TakumiSkinLookup(GameProfile profile, Supplier<PlayerSkin> delegate) implements Supplier<PlayerSkin> {
        @Override
        public PlayerSkin get() {
            return apply(profile, delegate.get());
        }
    }

    //? if >=26.1 {
    /**
     * Wraps {@code SkinManager#get}, used for player heads. Their skin is resolved once, so it waits (at most 3 s, off
     * the render thread) for the Takumi skin before completing.
     */
    public static CompletableFuture<Optional<PlayerSkin>> wrapFuture(GameProfile profile,
                                                                    CompletableFuture<Optional<PlayerSkin>> future) {
        HOOK_CALLS.incrementAndGet();
        if (future == null || profile == null || broken || !SkinRegistry.active()) {
            return future;
        }
        try {
            UUID id = id(profile);
            String name = name(profile);
            return future.thenCompose(original -> SkinRegistry.whenSettled(id, name)
                    .completeOnTimeout(null, 3, TimeUnit.SECONDS)
                    .handle((ignored, error) -> {
                        PlayerSkin base = original.orElseGet(() -> DefaultPlayerSkin.get(profile));
                        PlayerSkin result = apply(profile, base);
                        return result == base ? original : Optional.of(result);
                    }));
        } catch (RuntimeException e) {
            return future;
        }
    }
    //?}

    //? if >=26.1 {
    private static UUID id(GameProfile profile) {
        return profile.id();
    }

    private static String name(GameProfile profile) {
        return profile.name();
    }
    //?} else {
    /*private static UUID id(GameProfile profile) {
        return profile.getId();
    }

    private static String name(GameProfile profile) {
        return profile.getName();
    }
    *///?}
}
