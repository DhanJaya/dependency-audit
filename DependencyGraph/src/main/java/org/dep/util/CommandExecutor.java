package org.dep.util;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;

public class CommandExecutor {

    public static String executeCommand(String command, File directory) throws IOException {
        CommandLine cmdLine = CommandLine.parse(command);
        DefaultExecutor executor = DefaultExecutor.builder().setWorkingDirectory(directory).get();
        // Output streams to capture stdout and stderr
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
        executor.setStreamHandler(streamHandler);

        ExecuteWatchdog watchdog = ExecuteWatchdog.builder().setTimeout(Duration.ofSeconds(300)).get();
        executor.setWatchdog(watchdog);
        if (executor.execute(cmdLine) == 0) {
            return outputStream.toString();
        } else {
            return errorStream.toString();
        }
    }
}
