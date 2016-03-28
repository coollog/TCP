import java.nio.*;

public class TCPSockServerClient {
    private Segment.Buffer segmentBuffer = new Segment.Buffer();
    private ByteBuffer readBuffer = ByteBuffer.allocate(0x400);

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