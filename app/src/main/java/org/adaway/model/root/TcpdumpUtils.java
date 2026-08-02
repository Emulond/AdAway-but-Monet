/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.model.root;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;

import org.adaway.R;

import com.topjohnwu.superuser.Shell;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static org.adaway.model.root.ShellUtils.isBundledExecutableRunning;
import static org.adaway.model.root.ShellUtils.killBundledExecutable;
import static org.adaway.model.root.ShellUtils.mergeAllLines;
import static org.adaway.model.root.ShellUtils.runBundledExecutable;

import timber.log.Timber;

class TcpdumpUtils {
    private static final String TCPDUMP_EXECUTABLE = "tcpdump";
    private static final String TCPDUMP_LOG = "dns_log.txt";
    private static final String TCPDUMP_HOSTNAME_REGEX = "(?:A\\?|AAAA\\?)\\s(\\S+)\\.\\s";
    /**
     * The delay before checking the capture is still alive, in milliseconds.
     */
    private static final long START_CHECK_DELAY_MS = 500L;
    /**
     * The number of log file lines reported when a capture fails to start.
     */
    private static final int LOG_TAIL_LINES = 10;
    private static final Pattern TCPDUMP_HOSTNAME_PATTERN = Pattern.compile(TCPDUMP_HOSTNAME_REGEX);

    /**
     * Private constructor.
     */
    private TcpdumpUtils() {

    }

    /**
     * Checks if tcpdump is running
     *
     * @return true if tcpdump is running
     */
    static boolean isTcpdumpRunning() {
        return isBundledExecutableRunning(TCPDUMP_EXECUTABLE);
    }

    /**
     * Start tcpdump tool.
     *
     * @param context The application context.
     * @return {@code null} when the capture started, otherwise the reason it did not
     */
    static String startTcpdump(Context context) {
        Timber.d("Starting tcpdump...");
        checkSystemTcpdump();

        // Root is required to capture packets at all.
        if (!Shell.getShell().isRoot()) {
            return context.getString(R.string.dns_recording_error_no_root);
        }

        // The capture runs the executable bundled in the native library directory.
        File executable = new File(
                context.getApplicationInfo().nativeLibraryDir,
                ShellUtils.getExecutableName(TCPDUMP_EXECUTABLE));
        if (!executable.exists()) {
            return context.getString(R.string.dns_recording_error_missing, executable.getAbsolutePath());
        }

        File file = getLogFile(context);
        try {
            // Create log file before using it with tcpdump if not exists
            if (!file.exists() && !file.createNewFile()) {
                return context.getString(R.string.dns_recording_error_log_file, file.getAbsolutePath());
            }
        } catch (IOException e) {
            Timber.e(e, "Problem while getting cache directory!");
            return context.getString(R.string.dns_recording_error_log_file, file.getAbsolutePath());
        }

        // "-i any": listen on any network interface
        // "-p": disable promiscuous mode (doesn't work anyway)
        // "-l": Make stdout line buffered. Useful if you want to see the data while
        // capturing it.
        // "-v": verbose
        // "-t": don't print a timestamp
        // "-s 0": capture first 512 bit of packet to get DNS content
        String parameters = "-i any -p -l -v -t -s 512 'udp dst port 53' >> " + file + " 2>&1";

        Shell.Result startResult = runBundledExecutable(context, TCPDUMP_EXECUTABLE, parameters);
        if (!startResult.isSuccess()) {
            return context.getString(R.string.dns_recording_error_start, ShellUtils.describe(startResult));
        }
        // The executable is backgrounded by the shell, so a successful command only means it was
        // launched. Give it a moment and check it is still alive, otherwise a tcpdump that exits
        // straight away is reported as a running capture.
        try {
            Thread.sleep(START_CHECK_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (isTcpdumpRunning()) {
            return null;
        }
        // Anything tcpdump printed before dying was redirected to the log file. When that is empty
        // the process left no trace, so also report what the launching shell said and whether any
        // matching process exists, which separates a capture that never ran from one that runs but
        // is not being detected.
        String output = readLogFileTail(context);
        if (output.isEmpty()) {
            String processes = ShellUtils.listBundledExecutableProcesses(TCPDUMP_EXECUTABLE);
            output = context.getString(
                    R.string.dns_recording_error_no_output,
                    ShellUtils.describe(startResult),
                    processes.isEmpty() ? context.getString(R.string.dns_recording_error_no_process) : processes);
        }
        Timber.w("Tcpdump exited right after being started: %s", output);
        return context.getString(R.string.dns_recording_error_exited, output);
    }

    /**
     * Read the end of the log file, to report why a capture did not start.
     *
     * @param context The application context.
     * @return The last lines of the log file, or an empty string if it could not be read.
     */
    private static String readLogFileTail(Context context) {
        try {
            List<String> lines = Files.readAllLines(getLogFile(context).toPath());
            int from = Math.max(0, lines.size() - LOG_TAIL_LINES);
            return String.join("\n", lines.subList(from, lines.size()));
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /**
     * Tell whether encrypted DNS hides the requested host names from the capture.
     *
     * The capture reads plain DNS on port 53. With Private DNS the requests leave the device over
     * TLS, so the packets carry no readable host name and the log stays empty however well the
     * capture runs.
     *
     * @param context The application context.
     * @return The warning to report, or {@code null} when plain DNS is in use.
     */
    static String getEncryptedDnsWarning(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return null;
        }
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return null;
        }
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return null;
        }
        LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
        if (linkProperties == null || !linkProperties.isPrivateDnsActive()) {
            return null;
        }
        String serverName = linkProperties.getPrivateDnsServerName();
        return context.getString(
                R.string.dns_recording_warning_private_dns,
                serverName == null ? "automatic" : serverName);
    }

    /**
     * Stop tcpdump.
     */
    static void stopTcpdump() {
        killBundledExecutable(TCPDUMP_EXECUTABLE);
    }

    /**
     * Check if tcpdump binary in bundled in the system.
     */
    static void checkSystemTcpdump() {
        try {
            Shell.Result result = Shell.cmd("tcpdump --version").exec();
            int exitCode = result.getCode();
            String output = mergeAllLines(result.getOut());
            String msg = "Tcpdump " + (
                            exitCode == 0 ?
                                    "present" :
                                    "missing (" + exitCode + ")"
                    ) + "\n" + output;
            Timber.i(msg);
        } catch (Exception exception) {
            Timber.w(exception, "Failed to check system tcpdump binary.");
        }
    }

    /**
     * Get the tcpdump log file.
     *
     * @param context The application context.
     * @return The tcpdump log file.
     */
    static File getLogFile(Context context) {
        return new File(context.getCacheDir(), TCPDUMP_LOG);
    }

    /**
     * Get the tcpdump log content.
     *
     * @param context The application context.
     * @return The tcpdump log file content.
     */
    static List<String> getLogs(Context context) {
        Path logPath = getLogFile(context).toPath();
        // Check if the log file exists
        if (!Files.exists(logPath)) {
            return emptyList();
        }
        try (Stream<String> lines = Files.lines(logPath)) {
            return lines
                    .map(TcpdumpUtils::getTcpdumpHostname)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            Timber.e(exception, "Can not get cache directory.");
            return emptyList();
        }
    }

    /**
     * Delete log file of tcpdump.
     *
     * @param context The application context.
     */
    static boolean clearLogFile(Context context) {
        // Get the log file
        File file = getLogFile(context);
        // Check if file exists
        if (!file.exists()) {
            return true;
        }
        // Truncate the file content
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            // Only truncate the file
            outputStream.close();   // Useless but help lint
        } catch (IOException exception) {
            Timber.e(exception, "Error while truncating the tcpdump file!");
            // Return failed to clear the log file
            return false;
        }
        // Return successfully clear the log file
        return true;
    }

    /**
     * Gets hostname out of tcpdump log line.
     *
     * @param input One line from dns log.
     * @return A hostname or {code null} if no DNS query in the input.
     */
    private static String getTcpdumpHostname(String input) {
        Matcher tcpdumpHostnameMatcher = TCPDUMP_HOSTNAME_PATTERN.matcher(input);
        if (tcpdumpHostnameMatcher.find()) {
            return tcpdumpHostnameMatcher.group(1);
        } else {
            return null;
        }
    }
}
