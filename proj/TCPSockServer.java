import java.util.concurrent.*;

public class TCPSockServer {
    private ArrayBlockingQueue<TCPSock> backlog;

    public TCPSockServer(int backlog) {
        // Create the backlog queue.
        this.backlog = new ArrayBlockingQueue<TCPSock>(backlog);
    }

    // Backlog functions.
    public TCPSock pollBacklog() { return backlog.poll(); }
    public boolean isBacklogFull() { return backlog.remainingCapacity() == 0; }
    public boolean addToBacklog(TCPSock sock) {
        try { backlog.add(sock); }
        catch (IllegalStateException e) { return false; }

        return true;
    }
}