package org.vera20k;

import android.app.Application;

/** Installs crash capture before either the launcher or native game Activity starts. */
public final class IronHarborApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        StartupDiagnostics.install(this);
    }
}
