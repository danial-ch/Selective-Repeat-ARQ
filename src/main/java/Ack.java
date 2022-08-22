import java.io.Serializable;

public class Ack implements Serializable {

	private static final long serialVersionUID = 1L;
	int ackNumber;

	public int getAckNumber() {
		return ackNumber;
	}

	public void setAckNumber(int ackNumber) {
		this.ackNumber = ackNumber;
	}
}
