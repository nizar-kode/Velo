#ifndef SERVER_HPP
#define SERVER_HPP

#include <tuple>
#include <thread>
#include <asio.hpp>
#include <string>

#include "connection.h"
#include "input/input.h"
#include "settingsmanager.h"
#include "device.h"

/// <summary>
/// Server implementation for the custom transmission protocol in use.
///
/// The server accepts tcp connections in order to be able to manage and
/// control connected streaming devices.
///
/// Messaging between server and clients is done over TCP while actual
/// video streaming is done over UDP. The server commands which client
/// should stream video feed.
/// </summary>
class Server : std::enable_shared_from_this<Server>
{
public:
	using tcp = asio::ip::tcp;
	using udp = asio::ip::udp;
	using HostInfo = std::tuple<std::string, std::string, std::string>;

	struct PacketType
	{
		static const int FRAME = 0x00;
		static const int RESOLUTION = 0x01;
		static const int ACTIVATION = 0x02;
		static const int CAMERA = 0x03;
		static const int QUALITY = 0x04;
		static const int WB = 0x05;
		static const int EFFECT = 0x06;
	};

	struct ConnectionListener
	{
		virtual void OnDeviceConnected(std::string device) const = 0;
		virtual void OnDeviceDisconnected(std::string device) const = 0;
	};

	Server(int wifiPort, int adbPort, const ConnectionListener& connectionListener, SettingsManager& settings, const INPUT_MANAGER& inputmanager);
	Server(int wifiPort, const ConnectionListener& connectionListener, SettingsManager& settings, const INPUT_MANAGER& inputmanager);
	~Server();

	/// <summary>
	/// Gets host device's info (name, IPv4 address and port)
	/// </summary>
	HostInfo GetHostInfo();

	void Start();
	void Close();

	bool IsWifiServerBound() const { return bWifiBound; }
	std::string GetBindErrorMessage() const { return bindErrorMessage; }
	int GetWifiPort() const { return wifiPort; }
	int GetAdbPort() const { return adbPort; }

	std::vector<DeviceInfo> GetConnectedDevicesInfo();

private:
	int wifiPort;
	int adbPort;
	bool bWifiBound = false;
	bool bAdbBound = false;
	std::string bindErrorMessage;

	SettingsManager& settings;
	const INPUT_MANAGER& inputmanager;
	const ConnectionListener& connectionListener;

	char* byteBuffer;

	//asio::executor_work_guard<asio::io_context::executor_type> guard;
	asio::io_context context;

	tcp::acceptor acceptor;
	tcp::acceptor adbAcceptor;
	udp::socket udpsocket;
	udp::endpoint remote_endpoint;

	std::thread thread;

	std::vector<std::shared_ptr<Connection>> connections;

	void TCPDoAccept(tcp::acceptor& acc, const std::string& type);
	void StartReceive();
	void OnConnectionDisconnected(std::shared_ptr<Connection> connection);
	void InitSockets();
};

#endif