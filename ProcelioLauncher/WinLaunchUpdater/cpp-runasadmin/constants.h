#pragma once
#ifdef _WIN32
#define DOWNLOAD_FOLDER "_download"
#define LAUNCHER_NAME "ProcelioLauncher.exe"
#define UPDATER_NAME "LauncherUpdater.exe"
#endif

#ifdef __linux__
#define DOWNLOAD_FOLDER "_download"
#define LAUNCHER_NAME "bin/ProcelioLauncher"
#define UPDATER_NAME "LauncherUpdater"
#endif