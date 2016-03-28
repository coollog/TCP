import java.nio.*;
import java.util.*;

public class TCPSockServerClient {
    private class Segment implements Comparable<Segment> {
        private int seqNum;
        private byte[] payload;

        public Segment(int seqNum, byte[] payload) {
            this.seqNum = seqNum;
            this.payload = payload;
        }

        public int getSeqNum() { return seqNum; }
        public byte[] getPayload() { return payload; }
        public int getPayloadSize() { return payload.length; }

        public int compareTo(Segment o) {
            return new Integer(seqNum).compareTo(o.getSeqNum());
        }

        public boolean isNextTo(Segment o) {
            return o.getSeqNum() + o.getPayloadSize() == seqNum;
        }
    }

    private PriorityQueue<Segment> segmentBuffer = new PriorityQueue<Segment>();
    private ByteBuffer readBuffer = ByteBuffer.allocate(0x400);

    // Segment buffer functions.
    public void bufferSegment(int seqNum, byte[] payload) {
        segmentBuffer.add(new Segment(seqNum, payload));
    }
    /**
     * Delivers consecutive segments in segmentBuffer into the readbuffer.
     * Only call this if the first segment has been ACK'd.
     *
     * @return # bytes unloaded into readBuffer.
     */
    public int unloadSegmentBuffer() {
        int byteCount = 0;

        Segment lastSegment =
            new Segment(segmentBuffer.peek().getSeqNum(), TCPSock.dummy);

        while (true) {
            Segment segment = segmentBuffer.peek();
            if (segment == null) break;

            // If segment is next to the last segment, write the segment's
            // payload to the readbuffer and remove the segment.
            if (segment.isNextTo(lastSegment)) {
                byteCount += segment.getPayloadSize();
                readBuffer.put(segment.getPayload());
                lastSegment = segmentBuffer.poll();

                System.out.println("Unloaded: " + Utility.byteArrayToString(segment.getPayload()));
            } else break;
        }

        return byteCount;
    }

    public int read(byte[] buf, int pos, int len) {
        readBuffer.flip();
        int bytesRead = Math.min(readBuffer.remaining(), len);
        readBuffer.get(buf, pos, bytesRead);
        readBuffer.flip();

        return bytesRead;
    }
}