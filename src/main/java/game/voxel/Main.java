package game.voxel;

import engine.Engine;
import engine.IGameLogic;

public class Main {

    public static void main(String[] args) {
        try {
            boolean vSync = true;
            IGameLogic gameLogic = new VoxelGame();
            Engine gameEng = new Engine("Java Voxel Engine", 1280, 720, vSync, gameLogic);
            gameEng.start();
        } catch (Exception excp) {
            excp.printStackTrace();
            System.exit(-1);
        }
    }
}
