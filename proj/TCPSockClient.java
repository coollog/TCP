import java.util.*;

public class TCPSockClient {
    private int nextSeqNum;
    private int sendBase;
    private int windowSize;
    private int destAddr;
    private int destPort;

    private int seqNumFIN = -1; // -1 means it is not set.

    private TCPSockClientTimer timer = new TCPSockClientTimer(this);

    public TCPSockClient(int destAddr, int destPort) {
        // Initialize client variables.
        nextSeqNum = generateSeqNum();
        sendBase = nextSeqNum;
        windowSize = 2 * Transport.MAX_PAYLOAD_SIZE;
        this.destAddr = destAddr;
        this.destPort = destPort;
    }

    public void send(TCPSock sock, int type, byte[] payload) {
        sock.getNode().logOutput("Send: " + type + ", " + nextSeqNum + ", " + payload.length);

        send(sock, type, nextSeqNum, payload);
        incNextSeqNum(payload.length);
    }
    public void send(TCPSock sock, int type, int seqNum, byte[] payload) {
        sock.send(type, windowSize, seqNum, payload);

        // Add segment to the queue waiting for ACK.
        timer.addToQueue(type, seqNum, payload);

        // Start timer.
        if (!timer.isRunning()) timer.start(sock);
    }

    public void receivedACKForSeqNum(TCPSock sock, int seqNum) {
        if (seqNum > sendBase) {
            sock.getNode().logOutput("\tReceived ACK, updated sendBase from " + getSendBase() + " to " + seqNum);

            setSendBase(seqNum);

            // If there are currently any not-yet-acknowledged segments,
            // start timer.
            if (nextSeqNum > sendBase) {
                sock.getNode().logOutput("Still has unACKed segments till " + nextSeqNum);

                timer.start(sock);
            }
        } else { // A duplicate ACK received.
            // increment number of duplicate ACKs received for y
            // if (number of duplicate ACKS received for y==3) {
            //     /* TCP fast retransmit */
            //     resend segment with sequence number y
            // }
        }
    }

    public int getNextSeqNum() { return nextSeqNum; }
    public int getSendBase() { return sendBase; }
    public int getWindowSize() { return windowSize; }
    public int getDestAddr() { return destAddr; }
    public int getDestPort() { return destPort; }
    public int getSeqNumFIN() { return seqNumFIN; }
    public void incNextSeqNum(int amount) { nextSeqNum += amount; }
    public void setNextSeqNum(int seqNum) { nextSeqNum = seqNum; }
    public void setWindowSize(int windowSize) { this.windowSize = windowSize; }
    public void setSeqNumFIN(int seqNum) { seqNumFIN = seqNum; }

    public void setSendBase(int sendBase) {
        this.sendBase = sendBase;

        // Remove from timer queue messages that are ACK'd.
        timer.pruneQueue(sendBase);
    }

    private int generateSeqNum() {
        Random rand = new Random(System.nanoTime());
        return rand.nextInt(1 << 16);
    }
}