import java.util.*;

public class TCPSockClient {
    private int nextSeqNum;
    private int sendBase;
    private int windowSize;
    private int destAddr;
    private int destPort;

    public TCPSockClient(int destAddr, int destPort) {
        // Initialize client variables.
        nextSeqNum = generateSeqNum();
        sendBase = nextSeqNum;
        windowSize = 2 * Transport.MAX_PAYLOAD_SIZE;
        this.destAddr = destAddr;
        this.destPort = destPort;
    }

    public int getNextSeqNum() { return nextSeqNum; }
    public int getSendBase() { return sendBase; }
    public int getWindowSize() { return windowSize; }
    public int getDestAddr() { return destAddr; }
    public int getDestPort() { return destPort; }
    public void incNextSeqNum(int amount) { nextSeqNum += amount; }
    public void setNextSeqNum(int seqNum) { nextSeqNum = seqNum; }
    public void setSendBase(int sendBase) { this.sendBase = sendBase; }
    public void setWindowSize(int windowSize) { this.windowSize = windowSize; }

    private int generateSeqNum() {
        Random rand = new Random(System.nanoTime());
        return rand.nextInt(1 << 16);
    }
}