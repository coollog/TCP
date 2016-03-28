import java.util.*;

public class SocketManager {
    public static class AddressPair {
        public static String toString(int addr, int port) {
            return addr + ":" + port;
        }
        public static String toString(int srcAddr, int srcPort, int destPort) {
            return toString(srcAddr, srcPort) + "->" + destPort;
        }
    }

    private class SockSet {
        private Map<String, TCPSock> sockMap = new HashMap<String, TCPSock>();

        public SockSet(TCPSock sock) {
            sockMap.put("", sock);
        }
        public SockSet(String srcAddress, TCPSock sock) {
            put(srcAddress, sock);
        }

        public TCPSock get(int srcAddr, int srcPort) {
            String srcAddress = AddressPair.toString(srcAddr, srcPort);

            TCPSock sock = sockMap.get(srcAddress);
            if (sock == null) return sockMap.get("");

            return sock;
        }

        public void put(String srcAddress, TCPSock sock) {
            sockMap.put(srcAddress, sock);
        }

        public TCPSock remove(String srcAddress) {
            return sockMap.remove(srcAddress);
        }

        public boolean hasAddress(String srcAddress) {
            return sockMap.containsKey(srcAddress);
        }

        public String toString() {
            return sockMap.toString();
        }
    }

    private Map<Integer, SockSet> portMap = new HashMap<Integer, SockSet>();

    public boolean assign(int destPort, TCPSock sock) {
        if (!isPortAvailable(destPort)) return false;

        portMap.put(destPort, new SockSet(sock));
        // System.out.println("SockMan Bind (" + destPort + "): portMap=" + portMap);

        return true;
    }

    public boolean assign(int srcAddr, int srcPort, int destPort, TCPSock sock) {
        String srcAddress = AddressPair.toString(srcAddr, srcPort);

        if (!isSockAvailable(srcAddress, destPort)) return false;

        if (isPortAvailable(destPort)) {
            portMap.put(destPort, new SockSet(srcAddress, sock));
        } else {
            SockSet sockSet = portMap.get(destPort);
            sockSet.put(srcAddress, sock);
        }

        // System.out.println("SockMan Bind (" + destPort + ", " + srcAddress + "): portMap=" + portMap);

        return true;
    }

    public boolean unassign(int destPort) {
        return portMap.remove(destPort) != null;
    }

    public boolean unassign(int srcAddr, int srcPort, int destPort) {
        String srcAddress = AddressPair.toString(srcAddr, srcPort);

        if (isSockAvailable(srcAddress, destPort)) return false;
        SockSet sockSet = portMap.get(destPort);

        // System.out.println("portMap=" + portMap);
        return sockSet.remove(srcAddress) != null;
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