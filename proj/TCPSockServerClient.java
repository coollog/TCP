import java.nio.*;

public class TCPSockServerClient {
    public static final int READ_BUFFER_SIZE = 10; //0x4000;

    private Segment.Buffer segmentBuffer = new Segment.Buffer();
    private ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);

    public int getReceiveWindow() { return readBuffer.remaining(); }

    // Segment buffer functions.
    public void bufferSegment(int seqNum, byte[] payload) {
        segmentBuffer.add(seqNum, payload);
    }
    /**
     * Delivers consecutive segments in segmentBuffer into the readbuffer.
     * Only call this if the first segment has been ACK'd.
     *
     * @return # bytes unloaded into readBuffer.
     */
    public int unloadSegmentBuffer() {
        int byteCount = 0;

        int firstSeqNum = segmentBuffer.peekSeqNum();
        if (firstSeqNum == -1) return 0;

        Segment lastSegment = new Segment(firstSeqNum, TCPSock.dummy);

        while (true) {
            Segment segment = segmentBuffer.peek();
            if (segment == null) break;

            // If segment is next to the last segment, write the segment's
            // payload to the readbuffer and remove the segment.
            if (segment.isNextTo(lastSegment)) {
                if (readBuffer.remaining() < segment.getPayloadSize()) break;

                readBuffer.put(segment.getPayload());
                byteCount += segment.getPayloadSize();
                lastSegment = segmentBuffer.poll();
            } else break;
        }

        return byteCount;
    }

    public int read(byte[] buf, int pos, int len) {
        readBuffer.flip();
        int bytesRead = Math.min(readBuffer.remaining(), len);
        readBuffer.get(buf, pos, bytesRead);
        readBuffer.compact();

        return bytesRead;
    }
}