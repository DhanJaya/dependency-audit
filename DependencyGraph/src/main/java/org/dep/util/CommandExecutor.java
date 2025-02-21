package org.dep.util;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

public class CommandExecutor {

    public static boolean executeCommand(String command, File directory) throws IOException {
        CommandLine cmdLine = CommandLine.parse(command);
        DefaultExecutor executor = DefaultExecutor.builder().setWorkingDirectory(directory).get();
        ExecuteWatchdog watchdog = ExecuteWatchdog.builder().setTimeout(Duration.ofSeconds(300)).get();
        executor.setWatchdog(watchdog);
        if (executor.execute(cmdLine) == 0) {
            return true;
        } else {
            return false;
        }
    }
}
