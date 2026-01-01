package game;

import engine.Engine;
import engine.IGameLogic;
import game.voxel.VoxelGame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

public class Main {

    private static List<String> listWorlds() throws IOException {
        Path savesDir = Paths.get("saves");
        if (!Files.isDirectory(savesDir)) {
            return List.of();
        }
        try (var stream = Files.list(savesDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".dat"))
                    .map(p -> p.getFileName().toString().replace(".dat", ""))
                    .sorted()
                    .toList();
        }
    }

    public static void main(String[] args) {
        try {
            // Start directly into the menu
            // We pass a dummy world name "menu_background" to signal VoxelGame to start in
            // the menu
            String worldName = "menu_background";
            long seed = System.currentTimeMillis();

            System.out.println("Starting Java Voxel Engine...");

            boolean vSync = true;
            // Create game instance
            IGameLogic gameLogic = new VoxelGame(worldName, seed);

            // Start engine
            // Use 1280x720 for now, or maybe reads from settings later
            Engine gameEng = new Engine("Java Voxel Engine", 1280, 720, vSync, gameLogic);
            gameEng.start();

        } catch (Exception excp) {
            String msg = excp.getMessage() != null ? excp.getMessage() : excp.getClass().getSimpleName();
            excp.printStackTrace();
            javax.swing.JOptionPane.showMessageDialog(null, "Critical Error: " + msg, "Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            System.exit(-1);
        }
    }
}
