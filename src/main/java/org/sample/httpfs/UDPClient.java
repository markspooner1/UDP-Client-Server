package org.sample.httpfs;

import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.IOException;
import java.net.InetSocketAddress;

public class UDPClient { 

    private static final int MAX_LEN = Packet.MAX_LEN;
    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private static final String FIN = "FIN";
    private InetSocketAddress clientAddr;
    private InetSocketAddress serverAddr;
    private InetSocketAddress routerAddr;
    private HashMap<Long, Packet> packetsToBeSent;
    private HashMap<Long, Packet> packetsToBeReceived;
    private boolean done = false;
    public UDPClient(InetSocketAddress serverAddr, InetSocketAddress routerAddr, InetSocketAddress clientAddr) {
        this.serverAddr = serverAddr;
        this.routerAddr = routerAddr;
        this.clientAddr = clientAddr;
        packetsToBeSent = new HashMap<>();
        packetsToBeReceived = new HashMap<>();
    }

    public void startClient(byte[] data) throws IOException {
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(clientAddr);
            ByteBuffer buf = ByteBuffer.allocate(MAX_LEN).order(BYTE_ORDER);
            performHandshake(channel, serverAddr, routerAddr, buf);
            packetsToBeSent = Packet.splitMessage(data, routerAddr, serverAddr, 2);
            sendAndReceive(packetsToBeReceived, channel, routerAddr, serverAddr, buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void performHandshake(DatagramChannel channel, InetSocketAddress server, InetSocketAddress router, ByteBuffer buf) throws IOException {
        Packet synPacket = new Packet.Builder()
            .setType(Packet.PacketType.SYN)
            .setSequenceNumber(0)
            .setPortNumber(server.getPort())
            .setPeerAddress(server.getAddress())
            .setPayload("synPacket".getBytes())
            .create();

        sendPacketWithRetry(channel, router, synPacket);
    }

    private void sendAndReceive(HashMap<Long, Packet> packets, DatagramChannel channel, InetSocketAddress routerAddr, InetSocketAddress serverAddr, ByteBuffer buf) throws IOException { 
        Iterator<Entry<Long, Packet>> iterator = packetsToBeSent.entrySet().iterator();
 

        while (iterator.hasNext() || !done) {
            buf.clear();
            if (iterator.hasNext()) {
                Packet dataPacket = iterator.next().getValue();
                sendPacketWithRetry(channel, routerAddr, dataPacket);
                continue;
            }
            receivePacket(channel, buf);
            Packet response = Packet.fromBuffer(buf);
            if(response != null) {
    	         handleResponse(response, packetsToBeReceived, channel, routerAddr, serverAddr);
            }

          
        }

        String responseMessage = buildResponseMessage();
        JavaFXApp.showResponse(responseMessage);
        
           }

    private void sendPacketWithRetry(DatagramChannel channel, InetSocketAddress router, Packet packet) throws IOException {
        int retryCount = 0;
        final int maxRetries = 9999999;
        Packet response = null;

        while (retryCount < maxRetries && (response == null || response.getSequenceNumber() != packet.getSequenceNumber() + 1)) {
            packet.sendPacket(channel, router);
            response = Packet.waitForResponse(channel);

            if (response != null && response.getSequenceNumber() == packet.getSequenceNumber() + 1) {
                break;
            }

            retryCount++;
            System.out.println("Retrying... Attempt " + retryCount + " for packet " + packet.getType());
        }

        if (retryCount == maxRetries) {
            System.out.println("Max retries reached for packet " + packet.getSequenceNumber());
        } else if (response != null) {
            handleResponse(response, packetsToBeReceived, channel, router, new InetSocketAddress(response.getPeerAddress(), response.getPeerPort()));
        }
    }


    private void receivePacket(DatagramChannel channel, ByteBuffer buf) throws IOException {
        channel.receive(buf);
        buf.flip();
    }

    private void handleResponse(Packet response, HashMap<Long, Packet> packets, DatagramChannel channel, InetSocketAddress routerAddr, InetSocketAddress serverAddr) throws IOException {
        if(packetsToBeReceived.containsKey(response.getSequenceNumber())) {
        	System.out.println("Packet already received");
        }
    	if (response.getType() == Packet.PacketType.ACK) {
            return;
        } else if (response.getType() == Packet.PacketType.DATA) {
            packets.put(response.getSequenceNumber(), response);
            long sequenceNumber = response.getSequenceNumber() + 1;
            Packet ackPacket = new Packet.Builder()
                .setType(Packet.PacketType.ACK)
                .setSequenceNumber(sequenceNumber)
                .setPortNumber(serverAddr.getPort())
                .setPeerAddress(serverAddr.getAddress())
                .setPayload("ACK".getBytes())
                .create();
            sendPacketWithRetry(channel, routerAddr, ackPacket);
		} else if (response.getType() == Packet.PacketType.FIN) {
			packets.put(response.getSequenceNumber(), response);
			System.out.println("Received FIN packet");
			done = true;
			return;
		} else if (response.getType() == Packet.PacketType.SYNACK ) {
            return;
        }
    }

    private String buildResponseMessage() {
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("Response from server: \n");
        for (Packet packet : packetsToBeReceived.values()) {
            String payload = new String(packet.getPayload(), UTF_8);
            if (!payload.contains(FIN)) {
                responseBuilder.append(payload);
            }
        }
        responseBuilder.append("\n");
        return responseBuilder.toString();
    }
}
