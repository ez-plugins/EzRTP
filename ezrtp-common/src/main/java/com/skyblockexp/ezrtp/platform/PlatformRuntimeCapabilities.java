package com.skyblockexp.ezrtp.platform;

/**
 * Explicit capability flags that describe what runtime/platform features are available.
 * <p>
 * This is the single source of truth used by runtime providers and provider {@code supports(...)}
 * checks in platform modules.
 */
public record PlatformRuntimeCapabilities(boolean paperApi, boolean purpurApi, boolean regionizedRuntime) {

    public static final PlatformRuntimeCapabilities BUKKIT = new PlatformRuntimeCapabilities(false, false, false);
    public static final PlatformRuntimeCapabilities PAPER = new PlatformRuntimeCapabilities(true, false, false);
    public static final PlatformRuntimeCapabilities PAPER_FOLIA = new PlatformRuntimeCapabilities(true, false, true);
    public static final PlatformRuntimeCapabilities PURPUR = new PlatformRuntimeCapabilities(true, true, false);

    /**
     * A non-Folia Purpur (or Paper) build whose server classpath nonetheless carries the
     * Folia regionized-runtime marker class, so {@code regionizedRuntime()} is detected as
     * {@code true} even though the server is not actually running region-threaded. This is
     * the exact combination reported against Purpur builds where the region-scheduler
     * reflection path is exercised but not necessarily functional.
     */
    public static final PlatformRuntimeCapabilities PURPUR_REGIONIZED = new PlatformRuntimeCapabilities(true, true, true);

    public boolean isStrictPaper() {
        return paperApi && !purpurApi;
    }
}
