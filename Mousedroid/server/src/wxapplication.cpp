#include "wxapplication.h"

wxIMPLEMENT_APP(wxApplication);

#include "../res/icon.xpm"

wxApplication::wxApplication() { }

bool wxApplication::OnInit()
{        
    main_frame = new wxMain(settings);
    
    int wifiPort = settings.GetWifiPort();
    int adbPort = settings.GetAdbPort();
    server = new Server(wifiPort, adbPort, *this, settings, inputManager);

    Server::HostInfo hostInfo = server->GetHostInfo();
    
    main_frame->Bind(wxEVT_CLOSE_WINDOW, &wxApplication::OnWindowCloseEvent, this);
    main_frame->SetHostInfo(std::get<0>(hostInfo), std::get<1>(hostInfo), std::get<2>(hostInfo));

    if (!server->IsWifiServerBound())
    {
        wxString err = wxString::Format(
            "Failed to bind Wi-Fi server to port %d.\n\nError: %s\n\nOnly one Mousedroid instance can bind to this port. Please make sure no other instance is running, or change WIFI_PORT in config.ini.",
            wifiPort,
            server->GetBindErrorMessage().c_str()
        );
        wxMessageBox(err, "Mousedroid - Port Error", wxOK | wxICON_ERROR);
    }

    if(!settings.GetRunAtStartup())
        main_frame->Show();
    
    server->Start();

    main_frame->UpdateUI();

    return true;
}

void wxApplication::OnDeviceConnected(std::string device) const
{
    auto devices = server->GetConnectedDevicesInfo();
    main_frame->wxdevlist->SetDevices(devices);
}

void wxApplication::OnDeviceDisconnected(std::string device) const
{
	auto devices = server->GetConnectedDevicesInfo();
    main_frame->wxdevlist->SetDevices(devices);
}

void wxApplication::OnWindowCloseEvent(wxCloseEvent &evt)
{
    wxMessageDialog *box = new wxMessageDialog(main_frame, "This will disconnect all connected devices. Proceed?", "Confirm", wxYES_NO | wxICON_INFORMATION);

    int res = box->ShowModal();

    if(res == wxID_YES)
    {
        main_frame->Hide();
        server->Close();
        Logger::monitor->Destroy();
        main_frame->Destroy();
    }
}
