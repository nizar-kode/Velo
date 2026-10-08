using System.Net;
using System.Net.Sockets;
using System.Text;
using Velo.Desktop.Models;
using Velo.Desktop.Services;
using Xunit;

namespace Velo.Desktop.Tests;

public class DiscoveryBeaconTests
{
    [Fact]
    public async Task DiscoveryBeacon_RespondsToVeloProbe()
    {
        // Start beacon
        DiscoveryBeacon.Instance.Start();

        try
        {
            using var clientUdp = new UdpClient();
            clientUdp.Client.ReceiveTimeout = 4000;

            // Send VELO_PROBE to local discovery port
            string probe = "VELO_PROBE";
            byte[] probeBytes = Encoding.UTF8.GetBytes(probe);
            var serverEndpoint = new IPEndPoint(IPAddress.Loopback, DiscoveryBeacon.DiscoveryPort);

            await clientUdp.SendAsync(probeBytes, probeBytes.Length, serverEndpoint);

            // Wait for response
            var response = await clientUdp.ReceiveAsync();
            string text = Encoding.UTF8.GetString(response.Buffer);

            Assert.StartsWith("VELO_BEACON|", text);
            var parts = text.Split('|');
            Assert.Equal(4, parts.Length);
            Assert.Equal("VELO_BEACON", parts[0]);
            Assert.False(string.IsNullOrWhiteSpace(parts[1])); // Hostname
            Assert.False(string.IsNullOrWhiteSpace(parts[2])); // Local IP
            Assert.Equal("51821", parts[3]);                   // Service Port
        }
        finally
        {
            DiscoveryBeacon.Instance.Stop();
        }
    }

    [Fact]
    public async Task DiscoveryBeacon_RespondsToLegacyDiscoverQuery()
    {
        DiscoveryBeacon.Instance.Start();

        try
        {
            using var clientUdp = new UdpClient();
            clientUdp.Client.ReceiveTimeout = 4000;

            string probe = "{\"service\":\"AirPilot\",\"type\":\"discover_query\",\"version\":1}";
            byte[] probeBytes = Encoding.UTF8.GetBytes(probe);
            var serverEndpoint = new IPEndPoint(IPAddress.Loopback, DiscoveryBeacon.DiscoveryPort);

            await clientUdp.SendAsync(probeBytes, probeBytes.Length, serverEndpoint);

            var response = await clientUdp.ReceiveAsync();
            string text = Encoding.UTF8.GetString(response.Buffer);

            Assert.StartsWith("VELO_BEACON|", text);
        }
        finally
        {
            DiscoveryBeacon.Instance.Stop();
        }
    }

    [Fact]
    public void DiscoveryBeacon_CreatesAdvertisementPacket_FormatValid()
    {
        byte[] bytes = DiscoveryBeacon.Instance.CreateAdvertisementPacket();
        string text = Encoding.UTF8.GetString(bytes);

        Assert.StartsWith("VELO_BEACON|", text);
        Assert.EndsWith("|51821", text);
        var parts = text.Split('|');
        Assert.Equal(4, parts.Length);
        Assert.Equal(Environment.MachineName, parts[1]);
    }
}
