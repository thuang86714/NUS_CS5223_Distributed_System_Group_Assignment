import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public final class Tracker {

    public static final String RMI_NAME = "CS5223_TRACKER";
    public static int GRID_N = 0;
    public static int TREASURE_K = 0;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Missing required arguments! Usage: java Tracker [Port] [N] [K] ");
            System.exit(0);
        }

        int port = Integer.parseInt(args[0]);
        GRID_N = Integer.parseInt(args[1]);
        TREASURE_K = Integer.parseInt(args[2]);

        Registry registry;
        try {
            registry = LocateRegistry.createRegistry(port);
            IRmiTrack iRmiTrack = (IRmiTrack) UnicastRemoteObject.exportObject(mTracker, port);
            registry.rebind(RMI_NAME, iRmiTrack);
        } catch (Exception e) {
            System.err.println("Tracker went wrong with err: " + e.getMessage());
        }
    }

    /**
     * Implementation of Tracker logics
     */
    public static final IRmiTrack mTracker = new IRmiTrack() {
        final BlockingDeque<Player> mClients = new LinkedBlockingDeque<>();
        volatile boolean initialized = false;

        @Override
        public synchronized GameProvision connect(Player client) throws RemoteException {
            if (!initialized) {
                if (mClients.size() == 0) {
                    client.serverType = Game.RemoteServerImpl.ServerType.PRIMARY_SERVER;
                } else if (mClients.size() == 1) {
                    client.serverType = Game.RemoteServerImpl.ServerType.BACKUP_SERVER;
                    initialized = true;
                }
            }
            mClients.offer(client);
            System.out.println("The player connected: " + client.playerID);
            return new GameProvision(GRID_N, TREASURE_K, new ArrayList<>(mClients));
        }

        @Override
        public void disconnect(Player client) throws RemoteException {
            mClients.removeIf(c -> Objects.equals(client.playerID, c.playerID));
        }

        @Override
        public synchronized void updateServer(String servername, Game.RemoteServerImpl.ServerType serverType) throws RemoteException {
            mClients.stream().filter(p -> p.serverType == serverType).findAny().ifPresent(p -> p.serverType = null);
            mClients.stream().filter(p -> p.playerID.equals(servername)).findAny().ifPresent(p -> p.serverType = serverType);
        }
    };

    interface IRmiTrack extends Remote {

        /**
         * Presumably the caller is a primary server, then we give him everything that we got.
         * 1. N and K
         * 2. List of Clients
         *
         * @return GameProvision
         * @throws RemoteException Rmi Exception
         */
        GameProvision connect(Player client) throws RemoteException;

        void disconnect(Player client) throws RemoteException;

        void updateServer(String servername, Game.RemoteServerImpl.ServerType serverType) throws RemoteException;
    }

    static class GameProvision implements Serializable {
        private final int N;
        private final int K;
        private final List<Player> mClients;

        GameProvision(int n, int k, List<Player> clients) {
            this.N = n;
            this.K = k;
            mClients = clients;
        }

        public int getN() {
            return N;
        }

        public int getK() {
            return K;
        }

        public List<Player> getClients() {
            return mClients;
        }
    }
}
