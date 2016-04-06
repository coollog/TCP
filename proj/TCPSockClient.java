import java.util.*;

public class TCPSockClient {
    private int nextSeqNum;
    private int sendBase;
    private int windowSize = TCPSockServerClient.READ_BUFFER_SIZE;
    private int congestionWindowSize = Transport.MAX_PAYLOAD_SIZE;

    private TCPSockClientTimer timer = new TCPSockClientTimer(this);
    private int duplicateACKs = 0;

    private TCPSock sock;

    public TCPSockClient(TCPSock sock) {
        // Initialize client variables.
        nextSeqNum = generateSeqNum();
        sendBase = nextSeqNum;
        this.sock = sock;
    }

    public void send(int type, byte[] payload) {
        sock.getManager().log("Send: " + type + ", " + nextSeqNum + ", " + payload.length);

        send(type, nextSeqNum, payload);
        incNextSeqNum(payload.length);
    }
    public void send(int type, int seqNum, byte[] payload) {
        sock.send(type, 0, seqNum, payload);

        // Add segment to the queue waiting for ACK.
        timer.addToQueue(type, seqNum, payload);

        // Start timer.
        if (!timer.isRunning()) timer.start();
    }

    public void receivedACKForSeqNum(int seqNum) {
        sock.getManager().log("\tReceived ACK for seqNum " + seqNum + ", current sendBase " + sendBase);

        int numACKed = 1;

        if (seqNum > sendBase) {
            sock.getManager().log("\tReceived ACK, updated sendBase from " + sendBase + " to " + seqNum);

            numACKed = setSendBase(seqNum);
            duplicateACKs = 0;

            // If there are currently any not-yet-acknowledged segments,
            // start timer.
            if (nextSeqNum > sendBase) {
                sock.getManager().log("Still has unACKed segments till " + nextSeqNum);

                timer.start();
            }
        } else { // A duplicate ACK received.
            // Increment number of duplicate ACKs.
            duplicateACKs ++;

            // TCP fast retransmit resend segment.
            if (duplicateACKs == 3) {
                duplicateACKs = 0;

                sock.getManager().log("TCP fast retransmit:");
                timer.resend();

                slowDecreaseCongestionWindowSize();
            }
        }

        // Increase the congestion window no matter what.
        increaseCongestionWindowSize(numACKed);
    }

    public TCPSock getSock() { return sock; }
    public int getNextSeqNum() { return nextSeqNum; }
    public int getSendBase() { return sendBase; }
    public int getWindowSize() { return windowSize; }
    public void incNextSeqNum(int amount) { nextSeqNum += amount; }
    public void setNextSeqNum(int seqNum) { nextSeqNum = seqNum; }
    public void setWindowSize(int windowSize) { this.windowSize = windowSize; }

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