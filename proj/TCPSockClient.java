import java.lang.reflect.Method;
import java.util.*;

public class TCPSockClient {
    private int nextSeqNum;
    private int sendBase;
    private int windowSize;
    private int destAddr;
    private int destPort;

    private int seqNumFIN = -1; // -1 means it is not set.

    private Segment.Buffer timerQueue = new Segment.Buffer();
    private boolean timerRunning = false;
    private int timeout = 200;

    public TCPSockClient(int destAddr, int destPort) {
        // Initialize client variables.
        nextSeqNum = generateSeqNum();
        sendBase = nextSeqNum;
        windowSize = 2 * Transport.MAX_PAYLOAD_SIZE;
        this.destAddr = destAddr;
        this.destPort = destPort;
    }

    public void startTimer(TCPSock sock) {
        // Get smallest not-yet-acknowledged segment.
        int seqNumMin = timerQueue.peekSeqNum();
        if (seqNumMin == -1) return;

        // Construct callback parameters (just the seqNum).
        String[] paramTypes = { "java.lang.Integer" };
        Object[] params = { new Integer(seqNumMin) };

        // Construct callback.
        try {
            Method method = Callback.getMethod("timeout", sock, paramTypes);
            Callback callback = new Callback(method, sock, params);

            // Add timer.
            sock.getManager().addTimer(sock.getMyAddr(), timeout, callback);
        } catch (Exception e) {
            sock.getNode().logError("Timer could not be created!");
            e.printStackTrace();
            return;
        }

        timerRunning = true;
    }
    public void stopTimer() { timerRunning = false; }
    public boolean isTimerRunning() { return timerRunning; }

    public void addToTimerQueue(int type, int seqNum, byte[] payload) {
        timerQueue.add(type, seqNum, payload);
    }
    // Removes from timerQueue all segments with seqNum < nextSeqNum.
    public void pruneTimerQueue(int nextSeqNum) {
        while (timerQueue.peekSeqNum() != -1 &&
               timerQueue.peekSeqNum() < nextSeqNum) timerQueue.poll();
    }
    public Segment peekTimerQueue() { return timerQueue.peek(); }

    public int getNextSeqNum() { return nextSeqNum; }
    public int getSendBase() { return sendBase; }
    public int getWindowSize() { return windowSize; }
    public int getDestAddr() { return destAddr; }
    public int getDestPort() { return destPort; }
    public int getSeqNumFIN() { return seqNumFIN; }
    public void incNextSeqNum(int amount) { nextSeqNum += amount; }
    public void setNextSeqNum(int seqNum) { nextSeqNum = seqNum; }
    public void setSendBase(int sendBase) { this.sendBase = sendBase; }
    public void setWindowSize(int windowSize) { this.windowSize = windowSize; }
    public void setSeqNumFIN(int seqNum) { seqNumFIN = seqNum; }

    private int generateSeqNum() {
        Random rand = new Random(System.nanoTime());
        return rand.nextInt(1 << 16);
    }
}