package org.dep.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Helper {

    /**
     *Create a folder for a given path if the folder does not exist
     * @param folderPath
     * @throws IOException
     */
    public static boolean createFolderIfNotExists(String folderPath) {
        Path path = Paths.get(folderPath);
        try {
            if (Files.exists(path)) {
                // Delete contents, but not the folder itself
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    for (Path entry : stream) {
                        deleteRecursively(entry);
                    }
                }
            } else {
                Files.createDirectories(path);
            }
            return true;
        } catch (IOException e) {
            System.err.println("Failed to create or clean the folder: " + e.getMessage());
            return false;
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.delete(path);
    }
}
