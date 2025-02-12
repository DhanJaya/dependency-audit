package org.dep.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class CommandExecutor {

    public static boolean executeCommand(String command, File directory) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        processBuilder.directory(directory);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
//        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
//            String line;
//            while ((line = reader.readLine()) != null) {
//                System.out.println(line);
//            }
//        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println(command + " failed with exit code: " + exitCode);
            return false;
        }
        return true;
    }
}
