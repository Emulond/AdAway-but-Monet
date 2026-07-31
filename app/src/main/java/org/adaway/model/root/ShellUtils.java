package org.adaway.model.root;

import static com.topjohnwu.superuser.ShellUtils.escapedString;

import android.content.Context;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.List;
import java.util.Optional;

import timber.log.Timber;

/**
 * This class is an utility class to help with shell commands.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class ShellUtils {
    private static final String EXECUTABLE_PREFIX = "lib";
    private static final String EXECUTABLE_SUFFIX = "_exec.so";
    /**
     * The maximum length of a process name, as exposed by the kernel through {@code comm}.
     * Longer names are truncated there and therefore in the default {@code ps} output.
     */
    private static final int MAX_PROCESS_NAME_LENGTH = 15;
    /**
     * The shell exit code reported when a command is not found.
     */
    private static final int COMMAND_NOT_FOUND = 127;

    /**
     * Private constructor.
     */
    private ShellUtils() {

    }

    public static String mergeAllLines(List<String> lines) {
        return String.join("\n", lines);
    }

    public static boolean isBundledExecutableRunning(String executable) {
        String name = getExecutableName(executable);
        // Match on the full command line: bundled executable names are longer than the kernel
        // process name limit, so the default ps output never contains the whole name.
        Shell.Result result = Shell.cmd("pgrep -f " + escapedString(selfExcludingPattern(name))).exec();
        if (result.getCode() == COMMAND_NOT_FOUND) {
            return Shell.cmd("ps -A | grep " + escapedString(truncateToProcessName(name))).exec().isSuccess();
        }
        return result.isSuccess();
    }

    public static boolean runBundledExecutable(Context context, String executable, String parameters) {
        String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
        String command = "LD_LIBRARY_PATH=" + nativeLibraryDir + " " +
                nativeLibraryDir + File.separator + EXECUTABLE_PREFIX + executable + EXECUTABLE_SUFFIX + " " +
                parameters + " &";
        return Shell.cmd(command).exec().isSuccess();
    }

    public static void killBundledExecutable(String executable) {
        String name = getExecutableName(executable);
        // Same reason as isBundledExecutableRunning: killall matches the truncated process name.
        Shell.Result result = Shell.cmd("pkill -f " + escapedString(selfExcludingPattern(name))).exec();
        if (result.getCode() == COMMAND_NOT_FOUND) {
            Shell.cmd("killall " + escapedString(truncateToProcessName(name))).exec();
        }
    }

    /**
     * Get the file name of a bundled executable.
     *
     * @param executable The bundled executable name.
     * @return The related shared object file name.
     */
    public static String getExecutableName(String executable) {
        return EXECUTABLE_PREFIX + executable + EXECUTABLE_SUFFIX;
    }

    /**
     * Wrap the first character of a name in a character class.
     * The resulting expression still matches the name but no longer matches the shell command
     * carrying it, which would otherwise be reported as a match by pgrep and pkill.
     *
     * @param name The name to turn into a pattern.
     * @return The related pattern.
     */
    private static String selfExcludingPattern(String name) {
        return "[" + name.charAt(0) + "]" + name.substring(1);
    }

    /**
     * Truncate a name the way the kernel truncates process names.
     *
     * @param name The name to truncate.
     * @return The truncated name.
     */
    private static String truncateToProcessName(String name) {
        return name.length() > MAX_PROCESS_NAME_LENGTH
                ? name.substring(0, MAX_PROCESS_NAME_LENGTH)
                : name;
    }



    /**
     * Check if a path is writable.
     *
     * @param file The file to check.
     * @return <code>true</code> if the path is writable, <code>false</code> otherwise.
     */
    public static boolean isWritable(File file) {
        // Check first if file can be written without privileges
        if (file.canWrite()) {
            return true;
        }
        return Shell.cmd("test -w " + escapedString(file.getAbsolutePath()))
                .exec()
                .isSuccess();
    }

    public static boolean remountPartition(File file, MountType type) {
        Optional<String> partitionOptional = findPartition(file);
        if (!partitionOptional.isPresent()) {
            return false;
        }
        String partition = partitionOptional.get();
        Shell.Result result = Shell.cmd("mount -o " + type.getOption() + ",remount " + partition).exec();
        boolean success = result.isSuccess();
        if (!success) {
            Timber.w("Failed to remount partition %s as %s: %s.", partition, type.getOption(), mergeAllLines(result.getErr()));
        }
        return success;
    }

    private static Optional<String> findPartition(File file) {
        // Get mount points
        Shell.Result result = Shell.cmd("cat /proc/mounts | cut -d ' ' -f2").exec();
        List<String> out = result.getOut();
        // Check file and each parent against mount points
        while (file != null) {
            String path = file.getAbsolutePath();
            for (String mount : out) {
                if (path.equals(mount)) {
                    return Optional.of(mount);
                }
            }
            file = file.getParentFile();
        }
        return Optional.empty();
    }
}
