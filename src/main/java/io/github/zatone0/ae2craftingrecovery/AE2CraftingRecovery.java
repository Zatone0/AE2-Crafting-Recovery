package io.github.zatone0.ae2craftingrecovery;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(AE2CraftingRecovery.MOD_ID)
public final class AE2CraftingRecovery {
    public static final String MOD_ID = "ae2_crafting_recovery";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AE2CraftingRecovery() {
        LOGGER.info("AE2 Crafting Recovery diagnostic detector enabled");
    }
}
