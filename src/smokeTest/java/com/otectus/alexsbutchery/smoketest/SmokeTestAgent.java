package com.otectus.alexsbutchery.smoketest;

import net.minecraftforge.fml.common.Mod;

/**
 * Test-only companion mod used by {@code runClient -PsmokeTest}. It does nothing unless the system property
 * {@code alexsbutchery.smoketest.output} names a directory for its screenshots and result file.
 */
@Mod(SmokeTestAgent.MOD_ID)
public class SmokeTestAgent {
    public static final String MOD_ID = "alexsbutchery_smoketest";
    public static final String OUTPUT_PROPERTY = "alexsbutchery.smoketest.output";

    public SmokeTestAgent() {
    }
}
