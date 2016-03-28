import java.util.*;

public class SocketManager {
    private class SockSet {
        private Map<String, TCPSock> sockMap = new HashMap<String, TCPSock>();

        public SockSet(TCPSock sock) {
            sockMap.put("", sock);
        }
        public SockSet(String srcAddress, TCPSock sock) {
            sockMap.put(srcAddress, sock);
        }

        public TCPSock get(int srcAddr, int srcPort) {
            String srcAddress = AddressPair.toString(srcAddr, srcPort);

            TCPSock sock = sockMap.get(srcAddress);
            if (sock == null) return sockMap.get("");

            return sock;
        }

        public boolean hasAddress(String srcAddress) {
            return sockMap.containsKey(srcAddress);
        }
    }

    private Map<Integer, SockSet> portMap = new HashMap<Integer, SockSet>();

    public int assign(int destPort, TCPSock sock) {
        if (!isPortAvailable(destPort)) return -1;

        portMap.put(destPort, new SockSet(sock));

        return 0;
    }

    public int assign(int srcAddr, int srcPort, int destPort, TCPSock sock) {
        String srcAddress = AddressPair.toString(srcAddr, srcPort);

        if (!isSockAvailable(srcAddress, destPort)) return -1;

        portMap.put(destPort, new SockSet(srcAddress, sock));

        return 0;
    }

    public boolean deassign(int destPort) {
        return portMap.remove(destPort) != null;
    }

    public TCPSock find(int srcAddr, int srcPort, int destPort) {
        SockSet sockSet = portMap.get(destPort);
        if (sockSet == null) return null;

        return sockSet.get(srcAddr, srcPort);
    }

    private boolean isPortValid(int destPort) {
        return destPort >= 0 && destPort <= Transport.MAX_PORT_NUM;
    }

    private boolean isPortAvailable(int destPort) {
        if (!isPortValid(destPort)) return false;

        return !portMap.containsKey(destPort);
    }

    private boolean isSockAvailable(String srcAddress, int destPort) {
        if (isPortAvailable(destPort)) return true;
        if (!isPortValid(destPort)) return false;

        SockSet sockSet = portMap.get(destPort);
        return !sockSet.hasAddress(srcAddress);
    }
}