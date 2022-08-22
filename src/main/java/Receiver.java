import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;

public class Receiver {

	private static Protocol protocol = Protocol.GBN;
	private static int packetNumber = 20;
	public static double lostPack = 0.1;
	private static int portNumber = 5001;

	//receiver class should be started first
	public static void main(String[] args) {

		DatagramSocket socket;

		//incoming and outgoing data is represented by byte arrays of size 1024
		byte[] incomingData = new byte[1024];
		try {

			socket = new DatagramSocket(portNumber);
			System.out.println("Receiver Ready");

			//receiving empty packet to start transmission
			DatagramPacket initialPacket = new DatagramPacket(incomingData, incomingData.length);
			socket.receive(initialPacket);
			Instant start = Instant.now();

			//calling the function based on the protocol
			if (protocol == Protocol.GBN) {
				gbn(socket);
			} else {
				selectiveRepeat(socket);
			}

			Instant finish = Instant.now();
			System.out.println("Total duration :" + Duration.between(start, finish).toSeconds());

		} catch (Exception e) {

			e.printStackTrace();
		}
	}

	private static void gbn(DatagramSocket socket) throws IOException, ClassNotFoundException {

		boolean end = false;
		int waitingFor = 0;
		byte[] incomingData = new byte[1024];

		//looping until we reach the end
		while (!end) {
			//sending and receiving data using DatagramPacket
			DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
			socket.receive(incomingPacket);
			byte[] data = incomingPacket.getData();
			ByteArrayInputStream in = new ByteArrayInputStream(data);
			ObjectInputStream is = new ObjectInputStream(in);
			Packet packet = (Packet) is.readObject();
			System.out.println("Received packet " + packet.getPacketNumber());

			//packet will be discarded if it is not send in order
			if (packet.getPacketNumber() == waitingFor) {
				if(packet.isLast()){
					end = true;
				}
				waitingFor++;
			}
			else{
				System.out.println("Error : Packet " + packet.getPacketNumber() + " not in order");
				//we set packet number to -1 when it is discarded
				packet.setPacketNumber(-1);
			}

			InetAddress IPAddress = incomingPacket.getAddress();
			int port = incomingPacket.getPort();

			Ack ack = new Ack();
			ack.setAckNumber(waitingFor);

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ObjectOutputStream os = new ObjectOutputStream(outputStream);
			os.writeObject(ack);

			byte[] replyByte = outputStream.toByteArray();
			DatagramPacket replyPacket = new DatagramPacket(replyByte, replyByte.length, IPAddress, port);

			//packet received
			if (Math.random() > lostPack && packet.getPacketNumber() != -1) {
				System.out.println("Sending ack " + (ack.getAckNumber() - 1));
				socket.send(replyPacket);
			}
			//packet not received
			else if (packet.getPacketNumber() != -1){
				System.out.println("Packet " + packet.getPacketNumber() + " not received");
				waitingFor--;
				if (end) {
					end = false;
				}
			}
		}
	}

	private static void selectiveRepeat(DatagramSocket socket) throws IOException, ClassNotFoundException {

		ArrayList<Packet> received = new ArrayList<>();
		boolean end = false;
		int waitingFor = 0;
		byte[] incomingData = new byte[1024];

		ArrayList<Packet> buffer = new ArrayList<>();

		while (!end) {
			DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
			socket.receive(incomingPacket);
			InetAddress IPAddress = incomingPacket.getAddress();
			int port = incomingPacket.getPort();
			byte[] data = incomingPacket.getData();
			ByteArrayInputStream in = new ByteArrayInputStream(data);
			ObjectInputStream is = new ObjectInputStream(in);
			Packet packet = (Packet) is.readObject();

			if (packet.getPacketNumber() == waitingFor && packet.isLast()) {
				waitingFor++;
				received.add(packet);
				int value = sendData(packet, waitingFor, socket, IPAddress, port, false);
				if (value < waitingFor) {
					waitingFor = value;
					int length = received.size();
					System.out.println("Packet " + (waitingFor) + " lost");
					received.remove(length - 1);
					end = false;
				} else {
					end = true;
				}
			}

			else if (packet.getPacketNumber() == waitingFor && buffer.size() > 0) {
				received.add(packet);
				waitingFor++;
				int value = sendData(packet, waitingFor, socket, IPAddress, port, false);
				if (value < waitingFor) {
					waitingFor = value;
					int length = received.size();
					System.out.println("Packet " + (waitingFor) + " lost");
					received.remove(length - 1);

				} else {
					ArrayList<Packet> temp = new ArrayList<>(buffer);
					int count = 0;
					for (int i = 0; i < temp.size(); i++) {
						if (!(waitingFor == temp.get(i).getPacketNumber())) {
							break;
						} else {
							waitingFor++;
							count++;
							System.out.println("Packet " + buffer.get(i).getPacketNumber() + " delivered");
						}
					}
					buffer = new ArrayList<>();
					for (int j = 0; j < temp.size(); j++) {
						if (j < count) {
							continue;
						}
						buffer.add(temp.get(j));
					}
					if (waitingFor == packetNumber) {
						end = true;
					}
				}
			}
			else if (packet.getPacketNumber() == waitingFor && buffer.size() == 0) {
				received.add(packet);
				waitingFor++;
				int value = sendData(packet, waitingFor, socket, IPAddress, port, false);
				if (value < waitingFor) {
					waitingFor = value;
					int length = received.size();
					System.out.println("Packet " + (waitingFor) + " lost");
					System.out.println("Packet Lost");
					received.remove(length - 1);
				}
			}

			else if (packet.getPacketNumber() > waitingFor) {
				sendData(packet, waitingFor, socket, IPAddress, port, true);
				System.out.println("Packet " + packet.getPacketNumber() + " Stored in buffer");
				buffer.add(packet);
				Collections.sort(buffer);
			}

			else if (packet.getPacketNumber() < waitingFor) {
				sendData(packet, waitingFor, socket, IPAddress, port, true);
			}
			else {
				System.out.println("Packet " + packet.getPacketNumber() + " discarded");
				packet.setPacketNumber(-1);
			}
		}
	}

	public static int sendData(Packet packet, int waitingFor, DatagramSocket socket, InetAddress iPAddress, int port, boolean b) throws IOException {

		Ack ack = new Ack();
		ack.setAckNumber(packet.getPacketNumber() + 1);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ObjectOutputStream os = new ObjectOutputStream(outputStream);
		os.writeObject(ack);

		byte[] replyByte = outputStream.toByteArray();
		DatagramPacket replyPacket = new DatagramPacket(replyByte, replyByte.length, iPAddress, port);

		if ((Math.random() > lostPack | b) && packet.getPacketNumber() != -1) {
			System.out.println("Received packet " + (ack.getAckNumber()-1));
			System.out.println("Sending ack " + (ack.getAckNumber() - 1));
			socket.send(replyPacket);
		} else if (packet.getPacketNumber() != -1 && !b) {
			waitingFor--;
		}
		return waitingFor;
	}
}
