import java.lang.reflect.Method;
import java.util.*;

public class TCPSockClientTimer {
    private static final int DEFAULT_TIMEOUT = 1000;

    private Segment.Buffer segmentQueue = new Segment.Buffer();
    private boolean running = false;
    private int timeoutInterval = DEFAULT_TIMEOUT;
    private double estimatedRTT = DEFAULT_TIMEOUT;
    private double devRTT = 0;

    private TCPSockClient client;

    public TCPSockClientTimer(TCPSockClient client) {
        this.client = client;
    }

    public void timeout(TCPSock sock, Integer seqNum) {
        Segment segment = peekQueue();
        if (segment == null) {
            stop();
            sock.getNode().logOutput("Timeout: No segments on queue.");
            return;
        }

        // // If the segment has already been ACK'd, return.
        // if (segment.getSeqNum() != seqNum) {
        //     node.logOutput("Timeout: " + seqNum + "!=" + segment.getSeqNum());
        //     return;
        // }

        sock.getNode().logOutput("Timeout resend: " + segment.getType() + ", " + segment.getSeqNum());

        client.send(
            sock, segment.getType(), segment.getSeqNum(), segment.getPayload());

        start(sock);
    }

    public void start(TCPSock sock) {
        // Get smallest not-yet-acknowledged segment.
        int seqNumMin = segmentQueue.peekSeqNum();
        if (seqNumMin == -1) return;

        // Construct callback parameters (just the seqNum).
        String[] paramTypes = { "TCPSock", "java.lang.Integer" };
        Object[] params = { sock, new Integer(seqNumMin) };

        // Construct callback.
        try {
            Method method = Callback.getMethod("timeout", this, paramTypes);
            Callback callback = new Callback(method, this, params);

            // Add timer.
            sock.getManager().addTimer(
                sock.getMyAddr(), timeoutInterval, callback);
        } catch (Exception e) {
            sock.getNode().logError("Timer could not be created!");
            e.printStackTrace();
            return;
        }

        running = true;
    }
    public void stop() { running = false; }
    public boolean isRunning() { return running; }

    public void addToQueue(int type, int seqNum, byte[] payload) {
        segmentQueue.add(type, seqNum, payload);
    }
    // Removes from segmentQueue all segments with seqNum < nextSeqNum.
    // Also gets the sampleRTTs and recalculates the timeoutInterval.
    public void pruneQueue(int nextSeqNum) {
        // This holds all seqNums pruned mapped to a sampleRTT. If any seqNum is
        // encountered twice, we set the sampleRTT to -1 to invalidate it.
        // (ie. we do not use any sampleRTT for retransmitted segments)
        Map<Integer, Integer> sampleRTTMap = new HashMap<Integer, Integer>();

        // Prune the segmentQueue.
        while (segmentQueue.peekSeqNum() != -1 &&
               segmentQueue.peekSeqNum() < nextSeqNum) {
            Segment ackedSegment = segmentQueue.poll();

            Integer sampleRTTOrig = sampleRTTMap.put(
                ackedSegment.getSeqNum(), ackedSegment.getRTT());
            if (sampleRTTOrig != null)
                sampleRTTMap.put(ackedSegment.getSeqNum(), -1);
        }

        // Iterate through sampleRTTMap to recalculate the timeoutInterval.
        for (Integer sampleRTT : sampleRTTMap.values()) {
            if (sampleRTT == -1) continue;
            recalculateTimeoutInterval(sampleRTT);
        }
    }
    public Segment peekQueue() { return segmentQueue.peek(); }

    private void recalculateTimeoutInterval(int sampleRTT) {
        estimatedRTT = 0.875 * estimatedRTT + 0.125 * sampleRTT;
        devRTT = 0.75 * devRTT + 0.25 * Math.abs(sampleRTT - estimatedRTT);
        timeoutInterval = (int)(estimatedRTT + 4 * devRTT);
        System.out.println("\tnew timeout: " + timeoutInterval + " (" + sampleRTT + ")");
    }
}