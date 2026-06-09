package com.mtsharpgrain.node;

import jme3utilities.sky.SkyControl;

/**
 * Manages day-night cycle for SkyControl.
 * Cycles through a full 24-hour day in a configurable time period.
 */
public class DayNightCycleManager {
    
    private SkyControl skyControl;
    private float cycleSeconds;  // Total seconds for a full day cycle
    private float elapsedSeconds; // Time elapsed in current cycle
    
    /**
     * Create a day-night cycle manager.
     *
     * @param skyControl the SkyControl instance to manage
     * @param cycleSeconds total seconds for a full 24-hour cycle (e.g., 3600 for 1 hour = 1 full day)
     */
    public DayNightCycleManager(SkyControl skyControl, float cycleSeconds) {
        this.skyControl = skyControl;
        this.cycleSeconds = cycleSeconds;
        this.elapsedSeconds = 0f;
    }
    
    /**
     * Update the day-night cycle. Call this from your game's update loop.
     *
     * @param tpf time per frame in seconds
     */
    public void update(float tpf) {
        elapsedSeconds += tpf;
        
        // Loop the cycle
        if (elapsedSeconds >= cycleSeconds) {
            elapsedSeconds = 0f;
        }
        
        // Convert elapsed time to hour of day (0-24)
        float hour = (elapsedSeconds / cycleSeconds) * 24f;
        
        // Set the sun's hour
        skyControl.getSunAndStars().setHour(hour);
    }
    
    /**
     * Get the current hour of the day (0-24).
     *
     * @return current hour
     */
    public float getCurrentHour() {
        return (elapsedSeconds / cycleSeconds) * 24f;
    }
    
    /**
     * Get the progress through the current cycle (0-1).
     *
     * @return cycle progress
     */
    public float getCycleProgress() {
        return elapsedSeconds / cycleSeconds;
    }
    
    /**
     * Set the cycle duration.
     *
     * @param cycleSeconds total seconds for a full 24-hour cycle
     */
    public void setCycleSeconds(float cycleSeconds) {
        this.cycleSeconds = cycleSeconds;
    }
    
    /**
     * Get the cycle duration.
     *
     * @return total seconds for a full cycle
     */
    public float getCycleSeconds() {
        return cycleSeconds;
    }
    
    /**
     * Reset the cycle to the beginning.
     */
    public void resetCycle() {
        elapsedSeconds = 0f;
    }
    
    /**
     * Set the current time in the cycle.
     *
     * @param hour hour of day (0-24)
     */
    public void setHour(float hour) {
        // Clamp hour to 0-24 range
        hour = Math.max(0f, Math.min(hour, 24f));
        elapsedSeconds = (hour / 24f) * cycleSeconds;
        skyControl.getSunAndStars().setHour(hour);
    }
}
