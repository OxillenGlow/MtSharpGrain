/*
 * 
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