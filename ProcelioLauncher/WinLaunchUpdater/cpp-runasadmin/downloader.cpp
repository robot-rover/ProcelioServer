#include <iostream>
#include <filesystem>
#include <string>
#include <vector>
#include <Windows.h>

#include "constants.h"
namespace fs = std::filesystem;


int downloadmain(int argc, char** argv) {
    
    fs::path here = fs::current_path();
    here.replace_filename(LAUNCHER_NAME);

    fs::path unzipPath = fs::canonical(argv[0]);
    unzipPath.replace_filename(DOWNLOAD_FOLDER);

    std::wstring args = L"\"" + here.wstring() + L"\" -download \"" + unzipPath.wstring() + L"\""; // run with download arg
    wchar_t* arg = static_cast<wchar_t*>(malloc((args.length() + 1) * sizeof(wchar_t)));
    wcscpy_s(arg, args.length() + 1, args.c_str());

    STARTUPINFO info = { sizeof(info) };
    PROCESS_INFORMATION processInfo;
    std::cout << "Executing download in "; std::wcout << args << std::endl;
    if (CreateProcess(NULL, arg, NULL, NULL, TRUE, 0, NULL, NULL, &info, &processInfo))
    {
        WaitForSingleObject(processInfo.hProcess, INFINITE);
        CloseHandle(processInfo.hProcess);
        CloseHandle(processInfo.hThread);
    }
    std::cout << "Download complete" << std::endl;
    free(arg);
    return 0;
}