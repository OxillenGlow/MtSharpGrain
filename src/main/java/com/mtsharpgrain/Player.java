/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mtsharpgrain;

import com.jme3.math.Vector3f;

public class Player {

    private Vector3f pos = new Vector3f();

    public Vector3f getWorldPosition(){
        return pos;
    }

    /**
     *
     * @param p
     */
    public void setWorldPosition(Vector3f p){
        pos = p;
    }
}