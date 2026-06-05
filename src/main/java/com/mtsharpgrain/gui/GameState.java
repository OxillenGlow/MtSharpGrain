package com.mtsharpgrain.gui;

public class GameState {
    private static boolean okPlace;
    private static boolean darkMode;
    public static void setModes(boolean okP, boolean dark) {
        this.okPlace = okP;
        this.darkMode = dm;
    }
    boolean isOkPlace(){
        return okPlace;
    }
    boolean isDark(){
        return darkMode;
    }
}
