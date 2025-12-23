package game.voxel;

import engine.Engine;
import engine.IGameLogic;

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
        try (Scanner scanner = new Scanner(System.in)) {
            String worldName;
            long seed;

            // In non-interactive environments (e.g. Gradle run with no stdin),
            // fall back to a default world so the game can still start.
            if (!scanner.hasNextLine()) {
                worldName = "world";
                seed = System.currentTimeMillis();
                System.out.println("No console input detected. Starting default world '" + worldName + "' with seed " + seed);

                boolean vSync = false;
                IGameLogic gameLogic = new VoxelGame(worldName, seed);
                Engine gameEng = new Engine("Java Voxel Engine - " + worldName, 1280, 720, vSync, gameLogic);
                gameEng.start();
                return;
            }

            System.out.println("=== Java Voxel Engine ===");
            System.out.println("1) Create new world");
            System.out.println("2) Load world");
            System.out.print("Select option (1/2): ");
            String choice = scanner.nextLine().trim();

            if ("2".equals(choice)) {
                List<String> worlds = listWorlds();
                if (worlds.isEmpty()) {
                    System.out.println("No existing worlds. Creating a new one instead.");
                    choice = "1";
                } else {
                    System.out.println("Available worlds:");
                    for (int i = 0; i < worlds.size(); i++) {
                        System.out.println((i + 1) + ") " + worlds.get(i));
                    }
                    System.out.print("Select world number: ");
                    int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
                    if (idx < 0 || idx >= worlds.size()) {
                        System.out.println("Invalid selection.");
                        return;
                    }
                    worldName = worlds.get(idx);
                    game.voxel.WorldSave.LoadedWorld loaded = game.voxel.WorldSave.load(worldName);
                    seed = loaded.seed;
                    System.out.println("Loading world '" + worldName + "' with seed " + seed);

                    boolean vSync = false;
                    IGameLogic gameLogic = new VoxelGame(worldName, seed);
                    Engine gameEng = new Engine("Java Voxel Engine - " + worldName, 1280, 720, vSync, gameLogic);
                    gameEng.start();
                    return;
                }
            }

            // Create new world
            System.out.print("World name: ");
            worldName = scanner.nextLine().trim();
            if (worldName.isEmpty()) {
                worldName = "world";
            }

            System.out.print("Seed (leave blank for random): ");
            String seedStr = scanner.nextLine().trim();
            if (seedStr.isEmpty()) {
                seed = System.currentTimeMillis();
            } else {
                try {
                    seed = Long.parseLong(seedStr);
                } catch (NumberFormatException e) {
                    seed = seedStr.hashCode();
                }
            }

            System.out.println("Creating world '" + worldName + "' with seed " + seed);

            boolean vSync = true;
            IGameLogic gameLogic = new VoxelGame(worldName, seed);
            Engine gameEng = new Engine("Java Voxel Engine - " + worldName, 1980, 1080, vSync, gameLogic);
            gameEng.start();
        } catch (Exception excp) {
            excp.printStackTrace();
            System.exit(-1);
        }
    }
}
