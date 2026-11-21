# Upgrade cleanup can only clear a legacy native refresh request; it cannot set a rate.
-keep class com.elitedarkkaiser.redmagic.LegacyHardwareCleanupCli { *; }

# Root touch mode's helper, launched by name through app_process as root.
-keep class com.elitedarkkaiser.redmagic.gametrigger.TouchInjectorCli { *; }

# Root device-controls helper and its JNI entry points.
-keep class com.elitedarkkaiser.redmagic.parts.DevicePartsCli { *; }
-keep class com.elitedarkkaiser.redmagic.parts.PartsNative { *; }
