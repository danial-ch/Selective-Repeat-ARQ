import java.io.Serializable;

public class Packet implements Serializable,Comparable<Packet> {

	private static final long serialVersionUID = 1L;
	private int packetNumber;
	private boolean last = false;

	public int getPacketNumber() {
		return packetNumber;
	}

	public void setPacketNumber(int packetNumber) {
		this.packetNumber = packetNumber;
	}

	public boolean isLast() {
		return last;
	}

	public void setLast(boolean last) {
		this.last = last;
	}

	@Override
	public int compareTo(Packet o) {
		return this.getPacketNumber()-(o.getPacketNumber());
	}

}
