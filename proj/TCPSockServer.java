import java.util.concurrent.*;
import java.util.*;

public class TCPSockServer {
    private class Segment implements Comparable<Segment> {
        private int seqNum;
        private byte[] payload;

        public Segment(int seqNum, byte[] payload) {
                this.seqNum = seqNum;
                this.payload = payload;
        }

        public int getSeqNum() { return seqNum; }

        public int compareTo(Segment o) {
                return new Integer(seqNum).compareTo(o.getSeqNum());
        }
    }

    private ArrayBlockingQueue<TCPSock> backlog;
    private PriorityQueue<Segment> segmentBuffer;

    public TCPSockServer(int backlog) {
        // Create the backlog queue.
        this.backlog = new ArrayBlockingQueue<TCPSock>(backlog);

        // Create the segment buffer;
        segmentBuffer = new PriorityQueue<Segment>();
    }

    // Backlog functions.
    public TCPSock pollBacklog() { return backlog.poll(); }
    public boolean isBacklogFull() { return backlog.remainingCapacity() == 0; }
    public boolean addToBacklog(TCPSock sock) {
        try { backlog.add(sock); }
        catch (IllegalStateException e) { return false; }

        return true;
    }

    // Segment buffer functions.
    public void bufferSegment(int seqNum, byte[] payload) {
        segmentBuffer.add(new Segment(seqNum, payload));
    }
}