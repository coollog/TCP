import java.util.*;

// Class that represents a message that is sent, including the
// seqNum and payload.
public class Segment implements Comparable<Segment> {
    public static class Buffer extends PriorityQueue<Segment> {
        public void add(int seqNum, byte[] payload) {
            add(new Segment(seqNum, payload));
        }
        public void add(int type, int seqNum, byte[] payload) {
            add(new Segment(type, seqNum, payload));
        }

        /**
         * Gets the head segment's seqNum.
         *
         * @return -1 if queue is empty.
         */
        public int peekSeqNum() {
            Segment segment = peek();
            if (segment == null) return -1;

            return segment.getSeqNum();
        }
    }

    private int type;
    private int seqNum;
    private byte[] payload;
    private long startTime;

    public Segment(int seqNum, byte[] payload) {
        this(Transport.DATA, seqNum, payload);
    }
    public Segment(int type, int seqNum, byte[] payload) {
        this.type = type;
        this.seqNum = seqNum;
        this.payload = payload;

        startTime = System.currentTimeMillis();
    }

    public int getType() { return type; }
    public int getSeqNum() { return seqNum; }
    public byte[] getPayload() { return payload; }
    public int getPayloadSize() { return payload.length; }
    public int getRTT()
        { return (int)(System.currentTimeMillis() - startTime); }

    public int compareTo(Segment o) {
        return new Integer(seqNum).compareTo(o.getSeqNum());
    }

    public boolean isNextTo(Segment o) {
        return o.getSeqNum() + o.getPayloadSize() == seqNum;
    }
}