import java.nio.*;

public class TCPSockServerClient {
    public static final int READ_BUFFER_SIZE = 0x4000;

    private Segment.Buffer segmentBuffer = new Segment.Buffer();
    private ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);

    private int nextSeqNum;
    private int seqNumFIN = -1; // -1 means it is not set.

    private TCPSock sock;

    public TCPSockServerClient(TCPSock sock) {
        this.sock = sock;
    }

    public int getNextSeqNum() { return nextSeqNum; }
    public int getSeqNumFIN() { return seqNumFIN; }
    public void setSeqNumFIN(int seqNum) { seqNumFIN = seqNum; }
    public void setNextSeqNum(int seqNum) { nextSeqNum = seqNum; }

    public void sendACK() {
        sock.send(Transport.ACK, getReceiveWindow(), nextSeqNum, TCPSock.dummy);
    }
    public void sendACKForFIN() {
        sock.send(Transport.ACK, getReceiveWindow(), seqNumFIN, TCPSock.dummy);
    }

    public void receiveDATA(int seqNum, byte[] payload) {
        int prevSeqNum = nextSeqNum;

        if (nextSeqNum == seqNum) {
            // Segment received is in-order.
            // Deliver all consecutive received segments and ACK for last
            // delivered segment.
            bufferSegment(seqNum, payload);
            unloadSegmentBuffer();

            System.out.print(".:");
        } else if (nextSeqNum < seqNum) {
            // Segment received is out-of-order.
            // Queue up the segment.
            bufferSegment(seqNum, payload);

            System.out.print(".?");
        } else {
            // Segment received is old, ignore.
            System.out.print("!?");
        }

        // Send an ACK no matter what.
        sendACK();
        sock.getManager().log("Sent ACK for data (" + payload.length + ") with seqNum " + prevSeqNum + " and ackSeqNum " + nextSeqNum);
    }

    public int read(byte[] buf, int pos, int len) {
        readBuffer.flip();
        int bytesRead = Math.min(readBuffer.remaining(), len);
        readBuffer.get(buf, pos, bytesRead);
        readBuffer.compact();

        return bytesRead;
    }

    private int getReceiveWindow() { return readBuffer.remaining(); }

    // Segment buffer functions.
    private void bufferSegment(int seqNum, byte[] payload) {
        segmentBuffer.add(seqNum, payload);
    }
    /**
     * Delivers consecutive segments in segmentBuffer into the readbuffer.
     * Only call this if the first segment has been ACK'd.
     */
    private void unloadSegmentBuffer() {
        int byteCount = 0;

        int firstSeqNum = segmentBuffer.peekSeqNum();
        if (firstSeqNum == -1) return;

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

        // Increment the seqNum by the amount unloaded.
        nextSeqNum += byteCount;
    }
}