import java.util.*;

public class TCPSockClient {
    private int nextSeqNum;
    private int sendBase;
    private int windowSize = TCPSockServerClient.READ_BUFFER_SIZE;
    private int congestionWindowSize = Transport.MAX_PAYLOAD_SIZE;
    private int destAddr;
    private int destPort;

    private int seqNumFIN = -1; // -1 means it is not set.

    private TCPSockClientTimer timer = new TCPSockClientTimer(this);
    private int duplicateACKs = 0;

    public TCPSockClient(int destAddr, int destPort) {
        // Initialize client variables.
        nextSeqNum = generateSeqNum();
        sendBase = nextSeqNum;
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
        sock.getNode().logOutput("\tReceived ACK for seqNum " + seqNum + ", current sendBase " + sendBase);

        int numACKed = 1;

        if (seqNum > sendBase) {
            sock.getNode().logOutput("\tReceived ACK, updated sendBase from " + sendBase + " to " + seqNum);

            numACKed = setSendBase(seqNum);
            duplicateACKs = 0;

            // If there are currently any not-yet-acknowledged segments,
            // start timer.
            if (nextSeqNum > sendBase) {
                sock.getNode().logOutput("Still has unACKed segments till " + nextSeqNum);

                timer.start(sock);
            }
        } else { // A duplicate ACK received.
            // Increment number of duplicate ACKs.
            duplicateACKs ++;

            // TCP fast retransmit resend segment.
            if (duplicateACKs == 3) {
                duplicateACKs = 0;

                sock.getNode().logOutput("TCP fast retransmit:");
                timer.resend(sock);

                slowDecreaseCongestionWindowSize();
            }
        }

        // Increase the congestion window no matter what.
        increaseCongestionWindowSize(numACKed);
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

    // Flow Control: Gets the number of bytes that still can be sent.
    //               We can always send >=1 byte.
    public int getCanSendSize() {
        int unackedSize = nextSeqNum - sendBase + 1;
        return Math.max(1,
                        Math.min(congestionWindowSize - unackedSize,
                                 windowSize - unackedSize));
    }

    // @return Number of segments ACKed.
    public int setSendBase(int sendBase) {
        this.sendBase = sendBase;

        // Remove from timer queue messages that are ACK'd.
        return timer.pruneQueue(sendBase);
    }

    private int generateSeqNum() {
        Random rand = new Random(System.nanoTime());
        return rand.nextInt(1 << 16);
    }

    private void increaseCongestionWindowSize(int count) {
        congestionWindowSize +=
            count *
            Transport.MAX_PAYLOAD_SIZE * Transport.MAX_PAYLOAD_SIZE /
            congestionWindowSize;
        System.out.println("\tcwnd inc to " + congestionWindowSize);
    }
    public void decreaseCongestionWindowSize() {
        congestionWindowSize /= 2;
        System.out.println("\tcwnd dec to " + congestionWindowSize);
    }
    private void slowDecreaseCongestionWindowSize() {
        decreaseCongestionWindowSize();
        congestionWindowSize += Transport.MAX_PAYLOAD_SIZE * 3;
        System.out.println("\tcwnd dec to " + congestionWindowSize);
    }
}