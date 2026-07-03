package com.mtsharpgrain.gui;

public class GameState {

    private static boolean okPlace;
    public static boolean darkMode;
    private static String PLAYERSTATE = "editor";
    public static String guiState = "load";
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
}
