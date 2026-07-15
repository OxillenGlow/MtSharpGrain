package com.mtsharpgrain.gui;

public class GameState {

    private static boolean okPlace;
    public static boolean darkMode;
    private static String PLAYERSTATE = "editor";
    public static String guiState = "home";
    // Remembers the last non-"play" path so exiting play returns you to
    // where you were (e.g. deep in home/modview/<pack>) instead of always
    // resetting to "home".
    private static String lastHomePath = "home";
    public static Boolean jsOverrideGui = false; 
    
    public static void setokPlace(boolean okP) {
        okPlace = okP;
    }

    public static void setModes(boolean okP, boolean dark) {
        okPlace = okP;
        darkMode = dark;
    }

    public static boolean isOkPlace() {
        return okPlace;
    }

    public static boolean isDark() {
        return darkMode;
    }

    public static String getPlayerState() {
        return PLAYERSTATE;
    }

    public static void setPlayerState(String state) {
        PLAYERSTATE = state;
    }

    // ── Navigation (file-path-style gui state) ─────────────────────────────

    /** Direct set — used for menu-to-menu navigation (home -> home/modview -> ...). */
    public static void navigateTo(String path) {
        guiState = path;
    }

    /** Pops one segment off the current path; never goes above "home". */
    public static void goBack() {
        int idx = guiState.lastIndexOf('/');
        guiState = (idx <= 0) ? "home" : guiState.substring(0, idx);
    }

    /** True if guiState is exactly this path or nested under it (e.g. "home/modview/x" under "home"). */
    public static boolean isUnderPath(String path) {
        return guiState.equals(path) || guiState.startsWith(path + "/");
    }
    /** Call when entering flying/playing mode. Remembers the current menu path to return to later. */
    public static void enterPlay() {
        if (!"play".equals(guiState)) {
            lastHomePath = guiState;
        }
        guiState = "play";
    }

    /** Call when leaving flying/playing mode. Restores the menu path you were last on. */
    public static void exitPlay() {
        guiState = lastHomePath;
    }
}
