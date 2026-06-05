package com.mtsharpgrain.gui;

public class GameState {

    private static boolean okPlace;
    private static boolean darkMode;

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
}
