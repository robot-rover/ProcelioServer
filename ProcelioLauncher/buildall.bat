dir
mkdir Release
rmdir /Q /S .\jpckg\ProcelioLauncher
..\gradlew clean &  ..\gradlew makeImage
copy .\jpckg\LauncherUpdater.exe .\jpckg\ProcelioLauncher
copy .\jpckg\RunLaunchUpdate.exe .\jpckg\ProcelioLauncher
7z a Release\windows .\jpckg\ProcelioLauncher\*