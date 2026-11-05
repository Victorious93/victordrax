package com.victorious.victordrax.shizuku;

import android.content.Context;
import android.os.RemoteException;
import android.system.Os;
import android.util.Log;

import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Runs in a Shizuku-hosted process (ADB shell uid, or root when Sui is used).
 * Constructor is required; Context constructor is available from Shizuku API v13.
 */
public class ShellUserService extends IShellService.Stub {
    private static final String TAG = "VictorDraxShellSvc";

    public ShellUserService() {
        Log.i(TAG, "constructor uid=" + Os.getuid());
    }

    @Keep
    public ShellUserService(Context context) {
        Log.i(TAG, "constructor(Context) uid=" + Os.getuid() + " ctx=" + context);
    }

    @Override
    public void destroy() {
        Log.i(TAG, "destroy");
        System.exit(0);
    }

    @Override
    public int getUid() {
        return Os.getuid();
    }

    @Override
    public String exec(String command) throws RemoteException {
        if (command == null) return "-1\nnull command";
        try {
            Process p = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) >= 0) out.append(buf, 0, n);
            }
            int code = p.waitFor();
            return code + "\n" + out;
        } catch (Exception e) {
            return "-1\n" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
