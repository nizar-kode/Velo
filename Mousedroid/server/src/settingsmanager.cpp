#include "settingsmanager.h"

#include "adb.hpp"

SettingsManager::SettingsManager() : bAdbStarted(false)
{
    #ifdef _WIN32
        char buff[128] = { 0 };
        DWORD buffSize = sizeof(buff);

        LONG s2 = RegGetValueW(HKEY_CURRENT_USER, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run", L"Mousedroid", RRF_RT_REG_SZ, nullptr, &buff, &buffSize);
        
        settings["RUN_STARTUP"] = std::to_string((s2 == ERROR_SUCCESS));
    #endif

    LoadConfigs();
}

void SettingsManager::LoadConfigs()
{
    std::ifstream fin("config.ini");

    std::string line;
    while(std::getline(fin, line))
    {
        size_t pos = line.find('=');
        std::string setting = line.substr(0, pos);
        std::string value = line.substr(pos + 1);

        settings[setting] = value; 
    }

    fin.close();

    if(settings.find("WIFI_PORT") == settings.end() || settings["WIFI_PORT"].empty())
    {
        settings["WIFI_PORT"] = std::to_string(DEFAULT_WIFI_PORT);
    }
    if(settings.find("ADB_PORT") == settings.end() || settings["ADB_PORT"].empty())
    {
        settings["ADB_PORT"] = std::to_string(DEFAULT_ADB_PORT);
    }

    InputManager::MOVE_SENSITIVITY = std::atoi(settings["MOVE_SENSITIVITY"].c_str());
    InputManager::SCROLL_SENSITIVITY = std::atoi(settings["SCROLL_SENSITIVITY"].c_str());
}

void SettingsManager::SaveConfigs()
{
    std::ofstream fout("config.ini");
    for(auto &pair : settings)
    {
        fout << pair.first << "=" << pair.second << "\n";
    }
}

void SettingsManager::SetRunAtStartup(bool enabled)
{
    #ifdef _WIN32
        if(enabled)
        {
            std::string progPath = GetFilePath();
            LONG status = RegSetKeyValueA(HKEY_CURRENT_USER, "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run", "Mousedroid", REG_SZ, progPath.c_str(), (progPath.size() + 1) * sizeof(char));
        }
        else
            LONG status = RegDeleteKeyValueW(HKEY_CURRENT_USER, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run", L"Mousedroid");
    #endif 
}

void SettingsManager::SetMinimizeToTaskbar(bool enabled)
{
    settings["MINIMIZE_TASKBAR"] = std::to_string(enabled);
    SaveConfigs();
}

void SettingsManager::SetMoveSensitivity(int value)
{
    settings["MOVE_SENSITIVITY"] = std::to_string(value);
    InputManager::MOVE_SENSITIVITY = value;
    SaveConfigs();
}

void SettingsManager::SetScrollSensitivity(int value)
{
    settings["SCROLL_SENSITIVITY"] = std::to_string(value);
    InputManager::SCROLL_SENSITIVITY = value;
    SaveConfigs();
}

std::string SettingsManager::GetFilePath()
{
    #ifdef _WIN32
        char buffer[128] = {0};
        GetModuleFileNameA(NULL, buffer, 128);
        return std::string(buffer);
    #endif
}

bool SettingsManager::GetRunAtStartup()
{
    return settings["RUN_STARTUP"] == "1";
}

bool SettingsManager::GetMinToTaskbar()
{
    return settings["MINIMIZE_TASKBAR"] == "1";
}

int SettingsManager::GetMoveSensitivity()
{
    return std::atoi(settings["MOVE_SENSITIVITY"].c_str());
}

int SettingsManager::GetScrollSensitivity()
{
    return std::atoi(settings["SCROLL_SENSITIVITY"].c_str());
}

int SettingsManager::GetWifiPort()
{
    if (settings.find("WIFI_PORT") != settings.end() && !settings["WIFI_PORT"].empty())
    {
        int p = std::atoi(settings["WIFI_PORT"].c_str());
        if (p > 0 && p <= 65535) return p;
    }
    return DEFAULT_WIFI_PORT;
}

void SettingsManager::SetWifiPort(int value)
{
    settings["WIFI_PORT"] = std::to_string(value);
    SaveConfigs();
}

int SettingsManager::GetAdbPort()
{
    if (settings.find("ADB_PORT") != settings.end() && !settings["ADB_PORT"].empty())
    {
        int p = std::atoi(settings["ADB_PORT"].c_str());
        if (p > 0 && p <= 65535) return p;
    }
    return DEFAULT_ADB_PORT;
}

bool SettingsManager::ADBOnOff()
{   
    if(bAdbStarted)
        ADBOff();
    else
        ADBOn();

    return bAdbStarted;
}

void SettingsManager::ADBOn()
{
    int port = GetAdbPort();
    #ifdef _WIN32
        //a = system("adb reverse tcp:6969 tcp:6969");        
        bAdbStarted = adb::start(port);
    #else
        std::string cmd = "./adb reverse tcp:" + std::to_string(port) + " tcp:" + std::to_string(port);
        int a = system(cmd.c_str());
        if(a == 0)
        {
            LOG("ADB Server [PORT ", port, "] STARTED");
            bAdbStarted = true;
        }
        else
            LOG("ADB Server FAILED");
    #endif
}

void SettingsManager::ADBOff()
{
    if(bAdbStarted)
    {
        int port = GetAdbPort();
        #ifdef _WIN32
            //int a = system("adb reverse --remove-all");
            adb::kill(port);
        #else
            int a = system("./adb reverse --remove-all");
            LOG("ADB Server [PORT ", port, "] STOPPED");
        #endif
        bAdbStarted = false;
    }
}

bool SettingsManager::ADBState()
{
    return bAdbStarted;
}