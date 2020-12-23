dir
mkdir Release
rmdir /Q /S .\jpckg\ProcelioLauncher
call ..\gradlew clean &  call ..\gradlew makeImage
copy .\jpckg\LauncherUpdater.exe .\jpckg\ProcelioLauncher\LauncherUpdater.exe
copy .\jpckg\RunLaunchUpdate.exe .\jpckg\ProcelioLauncher\RunLaunchUpdate.exe
copy .\jpckg\try.bat .\jpckg\ProcelioLauncher\try.bat
