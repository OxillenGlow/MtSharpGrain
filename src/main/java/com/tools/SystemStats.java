package com.tools;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

public final class SystemStats {
    private static final OperatingSystemMXBean OS =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private SystemStats() {}

    /** 0–100, or -1 until the second sample. Whole machine, not per core. */
    public static float processCpuPercent() {
        var load = OS.getProcessCpuLoad();
        return load < 0 ? -1f : (float) (load * 100.0);
    }

    /** 0–100, or -1 until the second sample. */
    public static float systemCpuPercent() {
        var load = OS.getCpuLoad();
        return load < 0 ? -1f : (float) (load * 100.0);
    }

    public static long usedRamMb() {
        return (OS.getTotalMemorySize() - OS.getFreeMemorySize()) / (1024 * 1024);
    }

    public static long totalRamMb() {
        return OS.getTotalMemorySize() / (1024 * 1024);
    }

    public static long usedHeapMb() {
        var rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }
}
