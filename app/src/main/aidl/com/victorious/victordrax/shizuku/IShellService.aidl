// AIDL for the Shizuku UserService that runs shell commands with elevated identity.
package com.victorious.victordrax.shizuku;

interface IShellService {
    /** Reserved destroy method required by the Shizuku server. */
    void destroy() = 16777114;

    /**
     * Run [command] via /system/bin/sh -c and return "exitCode\\n" + combined stdout/stderr.
     * The first line is always a decimal exit code (or -1 on failure to start).
     */
    String exec(String command) = 1;

    /** Identity of the service process (uid). */
    int getUid() = 2;
}
