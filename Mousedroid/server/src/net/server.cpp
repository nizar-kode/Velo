#include "net/server.h"

#include "logger.h"

Server::Server(int wifiPort, int adbPort, const ConnectionListener& connectionListener, SettingsManager& settings, const INPUT_MANAGER& inputmanager)
	: settings(settings), 
	inputmanager(inputmanager),
	wifiPort(wifiPort),
	adbPort(adbPort),
	connectionListener(connectionListener),
	acceptor(context),
	adbAcceptor(context),
	udpsocket(context)
{
	byteBuffer = new char[Connection::MAX_BUFFER_SIZE];
	InitSockets();
}

Server::Server(int wifiPort, const ConnectionListener& connectionListener, SettingsManager& settings, const INPUT_MANAGER& inputmanager)
	: Server(wifiPort, SettingsManager::DEFAULT_ADB_PORT, connectionListener, settings, inputmanager)
{
}

Server::~Server()
{
	delete[] byteBuffer;
}

void Server::InitSockets()
{
	// 1. Initialize Wi-Fi TCP Acceptor & UDP socket with exclusive binding and graceful error handling
	try
	{
		asio::ip::tcp::endpoint tcp_endpoint(asio::ip::tcp::v4(), wifiPort);
		acceptor.open(tcp_endpoint.protocol());

		// Enforce that only one Wi-Fi server instance can bind this port
		acceptor.set_option(asio::ip::tcp::acceptor::reuse_address(false));
#if defined(_WIN32) && defined(SO_EXCLUSIVEADDRUSE)
		asio::detail::socket_option::boolean<SOL_SOCKET, SO_EXCLUSIVEADDRUSE> exclusive_option(true);
		acceptor.set_option(exclusive_option);
#endif
		acceptor.bind(tcp_endpoint);
		acceptor.listen();

		// Bind Wi-Fi UDP socket
		asio::ip::udp::endpoint udp_endpoint(asio::ip::udp::v4(), wifiPort);
		udpsocket.open(udp_endpoint.protocol());
		udpsocket.set_option(asio::ip::udp::socket::reuse_address(false));
		udpsocket.bind(udp_endpoint);

		bWifiBound = true;
		LOG("[SERVER] Wi-Fi server bound successfully to TCP/UDP port ", wifiPort);
	}
	catch (const std::exception& e)
	{
		bWifiBound = false;
		bindErrorMessage = e.what();
		LOG("[SERVER ERROR] Wi-Fi server failed to bind port ", wifiPort, ": ", e.what());
		asio::error_code ec;
		if (acceptor.is_open()) acceptor.close(ec);
		if (udpsocket.is_open()) udpsocket.close(ec);
	}

	// 2. Initialize ADB TCP Acceptor on 127.0.0.1:adbPort (independent from Wi-Fi)
	try
	{
		asio::ip::tcp::endpoint adb_endpoint(asio::ip::address_v4::loopback(), adbPort);
		adbAcceptor.open(adb_endpoint.protocol());
		adbAcceptor.set_option(asio::ip::tcp::acceptor::reuse_address(true));
		adbAcceptor.bind(adb_endpoint);
		adbAcceptor.listen();

		bAdbBound = true;
		LOG("[SERVER] ADB TCP server bound successfully to 127.0.0.1:", adbPort);
	}
	catch (const std::exception& e)
	{
		bAdbBound = false;
		LOG("[SERVER WARNING] ADB TCP server failed to bind to 127.0.0.1:", adbPort, " (handled gracefully): ", e.what());
		asio::error_code ec;
		if (adbAcceptor.is_open()) adbAcceptor.close(ec);
	}

	// Trigger ADB port reverse
	settings.ADBOn();

	if (bWifiBound)
	{
		StartReceive();
	}
}

Server::HostInfo Server::GetHostInfo()
{
	std::string name = asio::ip::host_name();

	// Get the active network adapter by 
	// creating a dummy UDP connection and let the OS 
	// determine what network adapter will be used
	udp::resolver resolver(context);
	udp::socket socket(context);

	try
	{
		socket.connect(udp::endpoint(asio::ip::address::from_string("8.8.8.8"), 80));
		asio::ip::address local_addr = socket.local_endpoint().address();
		socket.close();
		return { name, local_addr.to_string(), std::to_string(wifiPort)};
	}
	catch (...)
	{
		return { name, "127.0.0.1", std::to_string(wifiPort)};
	}
}

void Server::Start()
{
	try
	{
		if (bWifiBound)
		{
			TCPDoAccept(acceptor, "Wi-Fi");
		}
		if (bAdbBound)
		{
			TCPDoAccept(adbAcceptor, "ADB");
		}
		thread = std::thread([this]() {
			context.run();
		});
		LOG("[SERVER] Started");
	}
	catch (const std::exception& e)
	{
		LOG("[SERVER] Start failed: ", e.what());
	}
}

void Server::Close()
{
	LOG("[SERVER] Closing...\n");

	asio::error_code ec;
	if (acceptor.is_open())
	{
		acceptor.close(ec);
		if (ec) LOG("[SERVER] Error closing Wi-Fi acceptor: ", ec.message(), "\n");
	}

	if (adbAcceptor.is_open())
	{
		adbAcceptor.close(ec);
		if (ec) LOG("[SERVER] Error closing ADB acceptor: ", ec.message(), "\n");
	}

	if (udpsocket.is_open())
	{
		udpsocket.close(ec);
	}

	for (std::shared_ptr<Connection> conn : connections)
	{
		conn->Close(true);
	}
	connections.clear();

	context.stop();

	if (thread.joinable())
	{
		thread.join();
	}

	LOG("[SERVER] Closed.\n");

	settings.ADBOff();
}

std::vector<DeviceInfo> Server::GetConnectedDevicesInfo()
{
	std::vector<DeviceInfo> connectionInfo;
	for (auto& connection : connections)
	{
		connectionInfo.push_back(connection->GetDeviceInfo());
	}
	return connectionInfo;
}

void Server::TCPDoAccept(tcp::acceptor& acc, const std::string& type)
{
	if (!acc.is_open()) return;

	acc.async_accept([&, this, type](asio::error_code ec, tcp::socket socket) {
		if (!ec)
		{
			LOG("[SERVER] Device connected via ", type, " (", socket.remote_endpoint(), ")");

			// Read the device details sent by the client (name, model, manufacturer)
			std::array<char, 128> buf;
			asio::error_code read_ec;
			size_t bytes = socket.read_some(asio::buffer(buf, 128), read_ec);
			std::string deviceDetails;
			if (!read_ec)
			{
				deviceDetails = std::string(buf.data(), bytes);
			}

			auto conn = std::make_shared<Connection>(this->inputmanager, std::move(socket), deviceDetails, std::bind(&Server::OnConnectionDisconnected, this, std::placeholders::_1));
			connections.push_back(std::move(conn));

			connectionListener.OnDeviceConnected(connections.back()->GetDeviceInfo().Name);
		}

		if (acc.is_open())
		{
			TCPDoAccept(acc, type);
		}
	});
}

void Server::StartReceive()
{
	udpsocket.async_receive_from(asio::buffer(byteBuffer, Connection::MAX_BUFFER_SIZE), remote_endpoint, [&](const std::error_code& ec, size_t bytesReceived) {
		if (!ec)
		{
			inputmanager.execute(byteBuffer, bytesReceived);

			// Mechanism to synchronize the server with the client
			// otherwise client might send the next segment of the 
			// frame before the server finished processing the current 
			// one result in loss of data
			// The client waits to read some bytes after between sending segments
			// udpsocket.send_to(asio::buffer("done"), remote_endpoint);

			StartReceive();
		}
	});
}

void Server::OnConnectionDisconnected(std::shared_ptr<Connection> connection)
{
	connections.erase(std::remove(connections.begin(), connections.end(), connection), connections.end());
	connectionListener.OnDeviceDisconnected(connection->GetDeviceInfo().Name);
}
