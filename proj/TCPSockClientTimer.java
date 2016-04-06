import java.lang.reflect.Method;
import java.util.*;

public class TCPSockClientTimer {
    private static final int DEFAULT_TIMEOUT = 1000;

    private Segment.Buffer segmentQueue = new Segment.Buffer();
    private boolean running = false;
    private int timeoutInterval = DEFAULT_TIMEOUT;
    private double estimatedRTT = DEFAULT_TIMEOUT;
    private double devRTT = 0;
    private long currentId = 0;

    private TCPSockClient client;

    public TCPSockClientTimer(TCPSockClient client) {
        this.client = client;
    }

    public void timeout(TCPSock sock,
                        Long id,
                        Integer timeoutMultiplier) {
        // If this timer is outdated, just return.
        if (id != currentId) return;

        sock.getNode().logOutput("Timer " + id + " timed out with multiplier " + timeoutMultiplier);

        // estimatedRTT *= 2;
        resend(sock, timeoutMultiplier);

        client.decreaseCongestionWindowSize();
    }

    public void resend(TCPSock sock) { resend(sock, 1); }

    public void start(TCPSock sock) { start(sock, 1); }
    public void stop() { running = false; }
    public boolean isRunning() { return running; }

    public void addToQueue(int type, int seqNum, byte[] payload) {
        segmentQueue.add(type, seqNum, payload);
    }
    // Removes from segmentQueue all segments with seqNum < nextSeqNum.
    // Also gets the sampleRTTs and recalculates the timeoutInterval.
    // @return Number of segments pruned.
    public int pruneQueue(int nextSeqNum) {
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

        return sampleRTTMap.size();
    }
    public Segment peekQueue() { return segmentQueue.peek(); }

    private void recalculateTimeoutInterval(int sampleRTT) {
        estimatedRTT = 0.875 * estimatedRTT + 0.125 * sampleRTT;
        devRTT = 0.75 * devRTT + 0.25 * Math.abs(sampleRTT - estimatedRTT);
        timeoutInterval = (int)(estimatedRTT + 4 * devRTT) + 1;
        System.out.println("\tnew timeout: " + timeoutInterval + " (" + sampleRTT + ")");
    }

    // Starts the timer for timeoutInterval * timeoutMultiplier.
    private void start(TCPSock sock, Integer timeoutMultiplier) {
        // Make sure the queue has something.
        if (segmentQueue.peekSeqNum() == -1) return;

        // Keep track of the latest timer.
        currentId ++;

        // Construct callback parameters.
        String[] paramTypes =
            { "TCPSock", "java.lang.Long", "java.lang.Integer" };
        Object[] params = { sock, currentId, timeoutMultiplier };

        // Construct callback.
        try {
            Method method = Callback.getMethod("timeout", this, paramTypes);
            Callback callback = new Callback(method, this, params);

            // Add timer.
            sock.getManager().addTimer(
                sock.getMyAddr(), timeoutInterval * timeoutMultiplier, callback);

            sock.getNode().logOutput("Added timer " + currentId + " with timeout " + timeoutInterval * timeoutMultiplier);
        } catch (Exception e) {
            currentId --;
            sock.getNode().logError("Timer could not be created!");
            e.printStackTrace();
            return;
        }

        running = true;
    }

    private void resend(TCPSock sock, Integer timeoutMultiplier) {
        Segment segment = peekQueue();
        if (segment == null) {
            stop();
            sock.getNode().logOutput("Timeout/Resend: No segments on queue.");
            return;
        }

        sock.getNode().logOutput("Timeout/Resend: " + segment.getType() + ", " + segment.getSeqNum());

        client.send(
            sock, segment.getType(), segment.getSeqNum(), segment.getPayload());

        start(sock, timeoutMultiplier * 2);
    }
}