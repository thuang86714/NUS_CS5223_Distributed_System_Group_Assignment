import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Timer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Game {

    public volatile GameState gameState;

    private final String playerID;
    public final String PREFIX_URL;
    private Tracker.IRmiTrack mTracker;
    public IGameClientListener mListener;
    private final ExecutorService mThreadPool;

    private volatile String primaryId;
    private volatile String backupId;

    private final Timer mPrimaryTimer = new Timer();
    private final Timer mBackupTimer = new Timer();

    private RemoteServerImpl mIRemoteServerImplCallback;
    private GUI mGui;

    private Game(String host, int port, String playerID) {
        this.playerID = playerID;
        this.PREFIX_URL = "rmi://" + host + ":" + port + "/";
        this.mThreadPool = Executors.newFixedThreadPool(50);
    }

    public void show() {
        if (mGui == null) {
            mGui = new GUI(new Point(200, 300), playerID, gameState.N);
        }
        updateUI();
        mGui.show();
    }

    public void hide() {
        if (mGui != null) {
            final GUI gui = mGui;
            SwingUtilities.invokeLater(gui::hide);
        }
    }

    public void updateLocalGameState(GameState gameState) {
        if (gameState == null) {
            return;
        }
        System.out.println();
        this.gameState = gameState;
        updateServer();
        updateUI();
    }

    public void updateUI() {
        SwingUtilities.invokeLater(() -> {
            if (gameState == null || mGui == null) {
                return;
            }
            if (gameState.treasures.size() == 0) {
                return;
            }
            mGui.updateTreasures(gameState.getTreasures());
            mGui.updatePlayers(gameState.getLatestPlayers());
            mGui.updateScores(gameState.getLatestScores());
        });
    }

    private void updateServer() {
        Optional<Player> primaryPlayer = gameState.players
                .stream()
                .filter(p -> p.serverType == RemoteServerImpl.ServerType.PRIMARY_SERVER)
                .findAny();
        primaryPlayer.ifPresent(player -> setPrimaryId(player.playerID));
        Optional<Player> backupPlayer = gameState.players
                .stream()
                .filter(p -> p.serverType == RemoteServerImpl.ServerType.BACKUP_SERVER)
                .findAny();
        backupPlayer.ifPresent(player -> setBackupId(player.playerID));
    }

    public void setPrimaryId(String id) {
        this.primaryId = PREFIX_URL + id;
    }

    public void setBackupId(String id) {
        this.backupId = PREFIX_URL + id;
    }

    public void registerClientListener() {
        try {
            mListener = new IGameClientImpl(mLocalListener);
            Naming.rebind(PREFIX_URL + "client/" + playerID, mListener);
        } catch (RemoteException | MalformedURLException e) {
            // Pass
        }
    }

    public void registerServer(RemoteServerImpl.ServerType serverType) {
        try {
            if (mIRemoteServerImplCallback != null) {
                mIRemoteServerImplCallback.stopHeartbeatChecking();
                mIRemoteServerImplCallback = null;
            }
            mIRemoteServerImplCallback = new RemoteServerImpl(this.mThreadPool,
                    this.gameState,
                    this.mTracker,
                    serverType,
                    PREFIX_URL,
                    playerID);
            Naming.rebind(PREFIX_URL + playerID, mIRemoteServerImplCallback);
            updateTrackerServer(playerID, serverType);
            mIRemoteServerImplCallback.heartbeatChecking();
        } catch (Exception e) {
            // Pass
        }
    }

    public void updateTrackerServer(String name, RemoteServerImpl.ServerType serverType) {
        mThreadPool.submit(() -> {
            try {
                mTracker.updateServer(name, serverType);
            } catch (Exception e) {
                // pass
            }
        });
    }

    public void unregisterServer() {
        if (mIRemoteServerImplCallback != null) {
            try {
                Naming.unbind(PREFIX_URL + playerID);
            } catch (RemoteException | NotBoundException | MalformedURLException e) {
                System.out.println("unbind server failed");
            }
            mIRemoteServerImplCallback.stopHeartbeatChecking();
            mIRemoteServerImplCallback = null;
        }
        if (mListener != null) {
            try {
                Naming.unbind(PREFIX_URL + "client/" + playerID);
            } catch (MalformedURLException | NotBoundException | RemoteException e) {
                System.out.println("unbind client failed");
            }
        }
    }

    public void connectToTracker() {
        try {
            mTracker = (Tracker.IRmiTrack) Naming.lookup(Tracker.RMI_NAME);
            Player mClient = new Player(playerID);
            Tracker.GameProvision gameProvision = mTracker.connect(mClient);
            gameState = new GameState(gameProvision.getN(), gameProvision.getK());
            gameState.players.addAll(gameProvision.getClients());
        } catch (Exception e) {
            System.out.println("Connect to tracker failed " + e.getMessage());
        }
    }

    public void disconnectToTracker() {
        if (mTracker != null) {
            try {
                mTracker.disconnect(new Player(playerID));
            } catch (RemoteException e) {
                System.out.println("Disconnect failed");
            }
        }
    }

    public void quit() {
        mPrimaryTimer.cancel();
        mBackupTimer.cancel();
        hide();
        mGui = null;
        unregisterServer();
        disconnectToTracker();
    }

    // The normal user becomes a server
    private final IGameClientListener mLocalListener = new IGameClientListener() {

        @Override
        public void becomeServer(RemoteServerImpl.ServerType serverType, GameState latestGameState) throws RemoteException {
            gameState = latestGameState;
            registerServer(serverType);
            updateServer();
        }

        @Override
        public void doubleCheck() {
            // Return nothing
        }

        @Override
        public void onServerChanged(RemoteServerImpl.ServerType serverType, String serverName) throws RemoteException {
            if (serverType == RemoteServerImpl.ServerType.PRIMARY_SERVER) {
                setPrimaryId(serverName);
            } else {
                setBackupId(serverName);
            }
            System.out.println("On server changed: " + serverName + " server type" + serverType);
        }
    };

    public void join(Player player) {
        Runnable joinPrimary = () -> {
            try {
                if (player.serverType == RemoteServerImpl.ServerType.PRIMARY_SERVER) {
                    registerServer(RemoteServerImpl.ServerType.PRIMARY_SERVER);
                    updateTrackerServer(player.playerID, RemoteServerImpl.ServerType.PRIMARY_SERVER);
                    setPrimaryId(player.playerID);
                }
                updateLocalGameState(getPrimaryServer().join(player));
                System.out.println("---join Primary  success " + primaryId);
            } catch (Exception e) {
                System.out.println("Primary join failed " + primaryId);
            }
        };
        Runnable joinBackup = () -> {
            try {
                Thread.sleep(10);
                if (player.serverType == RemoteServerImpl.ServerType.BACKUP_SERVER) {
                    registerServer(RemoteServerImpl.ServerType.BACKUP_SERVER);
                    setBackupId(player.playerID);
                    updateTrackerServer(player.playerID, RemoteServerImpl.ServerType.BACKUP_SERVER);
                }
                updateLocalGameState(getBackupServer().join(player));
                System.out.println("---join Backup  success " + backupId);
            } catch (Exception e) {
                System.out.println("Backup join failed " + backupId);
            }
        };
        mThreadPool.submit(joinPrimary);
        mThreadPool.submit(joinBackup);
    }

    private boolean operate(String moveType, Point pos) {
        //TODO: We should to think whether we need thread pool to submit parallel missions
        GameState primaryState = safetyOperate(getPrimaryServer(), moveType, pos, playerID);
        if (primaryState != null) {
            updateLocalGameState(primaryState);
            updateUI();
            return false;
        }
        GameState backupState = safetyOperate(getBackupServer(), moveType, pos, playerID);
        if (backupState != null) {
            updateLocalGameState(backupState);
            updateUI();
            return false;
        }
        return true;
    }

    private GameState safetyOperate(IGameCallback server, String moveType, Point pos, String id) {
        try {
            if ("9".equals(moveType)) {
                server.quit(id);
                return null;
            }
            return server.Move(id, moveType, pos);
        } catch (Exception e) {
            System.out.println("Operate Failed, maybe the server is down");
        }
        return null;
    }

    private IGameCallback getPrimaryServer() {
        try {
            return (IGameCallback) Naming.lookup(primaryId);
        } catch (MalformedURLException | NotBoundException | RemoteException e) {
            System.out.println("Not found primary");
        }
        return null;
    }

    private IGameCallback getBackupServer() {
        try {
            return (IGameCallback) Naming.lookup(backupId);
        } catch (MalformedURLException | NotBoundException | RemoteException e) {
            System.out.println("Not found backup");
        }
        return null;
    }

    public boolean isConnected() {
        return mTracker != null;
    }

    // Entrance of Game
    public static void main(String[] args) {

        // 1.Init
        if (args.length != 3) {
            System.err.println("Missing required arguments! Usage: java Game [IP-address] [port-number] [player-id]");
            System.exit(0);
        }
        String trackerIP = args[0];
        String playerID = args[2];
        int trackerPort = Integer.parseInt(args[1]);

        final Game game = new Game(trackerIP, trackerPort, playerID);

        game.connectToTracker();
        if (!game.isConnected()) {
            System.err.println("Connect to tracker failed, please check system settings");
            return;
        }
        if (game.gameState.players.size() == 0) {
            System.err.println("Query clients pool failed, quitting...");
            return;
        }

        game.registerClientListener();
        game.gameState.players
                .stream()
                .filter(p -> p.serverType == RemoteServerImpl.ServerType.PRIMARY_SERVER)
                .findAny()
                .ifPresent(player -> {
                    game.setPrimaryId(player.playerID);
                });

        game.gameState.players
                .stream()
                .filter(p -> p.serverType == RemoteServerImpl.ServerType.BACKUP_SERVER)
                .findAny()
                .ifPresent(player -> {
                    game.setBackupId(player.playerID);
                });
        game.gameState.players.stream()
                .filter(p -> Objects.equals(p.playerID, game.playerID))
                .findAny().ifPresent(p -> {
                    game.join(p);
                    game.show();
                });

        Runtime.getRuntime().addShutdownHook(new Thread(game::quit));

        // 4.Scan used for getting input
        try {
            Scanner scan = new Scanner(System.in);
            while (scan.hasNextLine()) {
                String moveType = scan.next();
                moveType = moveType.trim();
                Point curPos = null;
                for (Player p : game.gameState.players) {
                    if (p.playerID.equals(game.playerID)) {
                        curPos = p.position;
                    }
                }
                if (curPos == null) {
                    System.out.printf("Failed to get current player %s !!!!!\n", game.playerID);
                    return;
                }
                if (game.operate(moveType, curPos)) {
                    game.quit();
                    break;
                }
            }
            scan.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class IGameClientImpl extends UnicastRemoteObject implements IGameClientListener, Serializable {

        final IGameClientListener mLocalListener;

        public IGameClientImpl(IGameClientListener listener) throws RemoteException {
            super();
            mLocalListener = listener;
        }

        @Override
        public void becomeServer(RemoteServerImpl.ServerType serverType, GameState latestGameState) throws RemoteException {
            mLocalListener.becomeServer(serverType, latestGameState);
        }

        @Override
        public void doubleCheck() throws RemoteException {

        }

        @Override
        public void onServerChanged(RemoteServerImpl.ServerType serverType, String serverName) throws RemoteException {
            mLocalListener.onServerChanged(serverType, serverName);
        }

    }

    public interface IGameClientListener extends Remote, Serializable {
        void becomeServer(RemoteServerImpl.ServerType serverType, GameState latestGameState) throws RemoteException;

        void doubleCheck() throws RemoteException;

        void onServerChanged(RemoteServerImpl.ServerType serverType, String serverName) throws RemoteException;
    }

    public static class RemoteServerImpl extends UnicastRemoteObject implements IGameCallback, Serializable {

        private final Timer mTimer = new Timer();
        private final Tracker.IRmiTrack mTracker;
        private ServerType mServerType;
        private GameState mGameState;
        private final String mPrefixURL;
        private final String mServerName;
        private final ExecutorService mThreadPool;

        public RemoteServerImpl(ExecutorService threadPool,
                                GameState gameState,
                                Tracker.IRmiTrack tracker,
                                ServerType serverType,
                                String prefixURL,
                                String servername)
                throws RemoteException {
            super();
            this.mThreadPool = threadPool;
            this.mGameState = gameState;
            this.mGameState.players.stream().filter(p -> p.playerID.equals(servername)).findAny().ifPresent(p -> p.serverType = serverType);
            this.mTracker = tracker;
            this.mServerType = serverType;
            this.mPrefixURL = prefixURL;
            this.mServerName = servername;
            if (mGameState.treasures.size() == 0) {
                generateTreasure(mGameState.K);
            }
        }

        public IGameClientListener getClientListenerById(String name) {
            try {
                return (IGameClientListener) Naming.lookup(mPrefixURL + "client/" + name);
            } catch (NotBoundException | MalformedURLException | RemoteException e) {
                System.out.println("Get client listen failed");
            }
            return null;
        }

        public void heartbeatChecking() {
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (mServerType == ServerType.PRIMARY_SERVER) {

                        // Check backup alive and assign backup
                        if (mGameState.players.size() > 1 &&
                                mGameState.players.stream().noneMatch(p -> p.serverType == ServerType.BACKUP_SERVER)) {
                            assignNormalPayerServer("", ServerType.BACKUP_SERVER);
                        }
                        Iterator<Player> iterator = mGameState.players.iterator();
                        while (iterator.hasNext()) {
                            Player p = iterator.next();
                            if (p.serverType == ServerType.PRIMARY_SERVER) {
                                continue;
                            }
                            if (checkPlayerAlive(p.playerID)) {
                                continue;
                            }
                            iterator.remove();
                            disconnectFromTracker(p.playerID);
                        }
                    } else if (mServerType == ServerType.BACKUP_SERVER) {
                        checkPrimaryAlive();
                    }
                }
            }, 0, 100);
        }

        public void stopHeartbeatChecking() {
            mTimer.cancel();
        }

        public boolean checkPlayerAlive(String playerId) {
            IGameClientListener dc = getClientListenerById(playerId);
            if (dc != null) {
                try {
                    dc.doubleCheck();
                    // The client is good
                    return true;
                } catch (RemoteException e) {
                    System.out.println("We lost him " + playerId);
                }
            }
            return false;
        }

        public void assignNormalPayerServer(final String playerId, ServerType serverType) {
            Runnable runnable = () -> {
                try {
                    String bkPlayerId = playerId;
                    if (bkPlayerId == null || bkPlayerId.equals("")) {
                        Optional<Player> backupServer = mGameState.players.stream()
                                .filter(p -> p.serverType == null)
                                .findAny();
                        if (backupServer.isPresent()) {
                            Player backupPlayer = backupServer.get();
                            bkPlayerId = backupPlayer.playerID;
                        } else {
                            // No more idle users
                            // System.out.println("No more IDLE players " + mGameState.players.size());
                            return;
                        }
                    }
                    IGameClientListener client = getClientListenerById(bkPlayerId);
                    if (client != null) {
                        String finalPlayerId = bkPlayerId;
                        mGameState.players.stream()
                                .filter(p -> p.playerID.equals(finalPlayerId))
                                .findAny().ifPresent(p -> p.serverType = serverType);
                        client.becomeServer(serverType, mGameState);
                        informAllClients(serverType, finalPlayerId);
                    } else {
                        System.out.println("The client didn't bind rmi");
                    }
                } catch (RemoteException e) {
                    System.out.println("Select Server failed ");
                }
            };
            mThreadPool.submit(runnable);
        }

        public void informAllClients(ServerType serverType, String serverName) {
            try {
                for (Player p : mGameState.players) {
                    IGameClientListener clientListener = getClientListenerById(p.playerID);
                    if (clientListener != null) {
                        clientListener.onServerChanged(serverType, serverName);
                    }
                }
            } catch (Exception e) {
                // pass
            }
        }

        @Override
        public GameState Move(String playerID, String moveType, Point pos) throws RemoteException {
            // Do nothing if the primary server still alive
            if (checkPrimaryAlive()) {
                return this.mGameState;
            }
            try {
                switch (moveType) {
                    case "0":
                        System.out.println("Refresh");
                        return this.mGameState;
                    case "1":
                        System.out.println("Move west");
                        Point newPos = new Point(pos.x - 1, pos.y);
                        boolean succeed = this.checkAndUpdate(playerID, newPos);
                        if (succeed) {
                            System.out.println("Succeed to move west");
                        } else {
                            System.out.println("Failed to move west");
                        }
                        return this.mGameState;
                    case "2":
                        System.out.println("Move south");
                        newPos = new Point(pos.x, pos.y + 1);
                        succeed = this.checkAndUpdate(playerID, newPos);
                        if (succeed) {
                            System.out.println("Succeed to move south");
                        } else {
                            System.out.println("Failed to move south");
                        }
                        return this.mGameState;
                    case "3":
                        System.out.println("Move east");
                        newPos = new Point(pos.x + 1, pos.y);
                        succeed = this.checkAndUpdate(playerID, newPos);
                        if (succeed) {
                            System.out.println("Succeed to move east");
                        } else {
                            System.out.println("Failed to move east");
                        }
                        return this.mGameState;
                    case "4":
                        System.out.println("Move north");
                        newPos = new Point(pos.x, pos.y - 1);
                        succeed = this.checkAndUpdate(playerID, newPos);
                        if (succeed) {
                            System.out.println("Succeed to move north");
                        } else {
                            System.out.println("Failed to move north");
                        }
                        return this.mGameState;
                    default:
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return this.mGameState;
        }

        @Override
        public void quit(String playerID) throws RemoteException {
            // Do nothing if the primary server still alive
            if (checkPrimaryAlive()) {
                return;
            }
            disconnectFromTracker(playerID);
            this.mGameState.players.removeIf(p -> Objects.equals(p.playerID, playerID));
        }

        @Override
        public GameState join(Player player) throws RemoteException {
            // Do nothing if the primary server still alive
            if (checkPrimaryAlive()) {
                return null;
            }
            mGameState.players.removeIf(p -> p.playerID.equals(player.playerID));
            boolean collision = true;
            Point newPos = null;
            while (collision) {
                collision = false;
                int x = (int) ((Math.random() * (mGameState.N)));
                int y = (int) ((Math.random() * (mGameState.N)));
                newPos = new Point(x, y);
                for (Player p : mGameState.players) {
                    if (p.position == newPos) {
                        collision = true;
                        break;
                    }
                }
                for (Point t : mGameState.treasures) {
                    if (t.equals(newPos)) {
                        collision = true;
                        break;
                    }
                }
            }
            player.position = newPos;
            mGameState.players.add(player);
            System.out.printf(System.currentTimeMillis() + " Player %s joined the game\n", player.playerID);
            return mGameState;
        }

        @Override
        public GameState sync() throws RemoteException {
            return mGameState;
        }

        @Override
        public void generateTreasure(int num) {
            for (int i = 0; i < num; i++) {
                boolean collision = true;
                Point newPos = null;
                while (collision) {
                    collision = false;
                    int x = (int) ((Math.random() * (mGameState.N)));
                    int y = (int) ((Math.random() * (mGameState.N)));
                    newPos = new Point(x, y);
                    for (Point t : mGameState.treasures) {
                        if (t.equals(newPos)) {
                            collision = true;
                            break;
                        }
                    }
                    for (Player p : mGameState.players) {
                        if (p.position.equals(newPos)) {
                            collision = true;
                            break;
                        }
                    }
                }
                this.mGameState.treasures.add(newPos);
            }
        }

        private boolean checkAndUpdate(String playerID, Point newPos) {

            if (newPos.x < 0 || newPos.y < 0 || newPos.x >= mGameState.N || newPos.y >= mGameState.N) {
                System.out.println("Failed to move because N limit");
                return false;
            }
            for (Player p : this.mGameState.players) {
                if (p.position.x == newPos.x && p.position.y == newPos.y) {
                    System.out.println("Failed to move because of collision with other player");
                    return false;
                }
            }
            boolean addScore = false;
            for (Point t : this.mGameState.treasures) {
                if (t.equals(newPos)) {
                    addScore = true;
                    this.mGameState.treasures.remove(t);
                    this.generateTreasure(1);
                    break;
                }
            }
            for (Player p : this.mGameState.players) {
                if (Objects.equals(p.playerID, playerID)) {
                    p.position = newPos;
                    if (addScore) {
                        p.score++;
                    }
                    return true;
                }
            }
            return false;
        }

        private boolean checkPrimaryAlive() {
            if (ServerType.PRIMARY_SERVER == mServerType) {
                return false;
            }
            // Backup server access primary server check alive
            // sync latest state
            Optional<Player> primaryPlayer = mGameState.players.stream()
                    .filter(player -> player.serverType == ServerType.PRIMARY_SERVER)
                    .findAny();
            if (primaryPlayer.isPresent()) {
                Player player = primaryPlayer.get();
                try {
                    IGameCallback primaryServer = findPrimaryServerById(player.playerID);
                    if (primaryServer != null) {
                        this.mGameState = primaryServer.sync();
                        return true;
                    } else {
                        System.out.println("PRIMARY not found " + player.playerID);
                    }
                } catch (RemoteException e) {
                    System.out.println("PRIMARY IS DOWN, IT'S TIME TO TAKE OVER   "
                            + player.playerID + "  treasure state:"
                            + mGameState.treasures.size());
                }
                mServerType = ServerType.PRIMARY_SERVER;
                mGameState.players.stream()
                        .filter(p -> p.playerID.equals(mServerName))
                        .findAny().ifPresent(p -> p.serverType = ServerType.PRIMARY_SERVER);
                mGameState.players.removeIf(p -> p.playerID.equals(player.playerID));
                disconnectFromTracker(player.playerID);
                try {
                    mTracker.updateServer(mServerName, ServerType.PRIMARY_SERVER);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        private void disconnectFromTracker(String id) {
            mThreadPool.submit(() -> {
                try {
                    mTracker.disconnect(new Player(id));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                try {
                    Naming.unbind(mPrefixURL + "client/" + id);
                } catch (RemoteException | MalformedURLException | NotBoundException e) {
                    System.out.println();
                }
                try {
                    Naming.unbind(mPrefixURL + id);
                } catch (RemoteException | MalformedURLException | NotBoundException e) {
                    System.out.println();
                }
            });
        }

        private IGameCallback findPrimaryServerById(String id) {
            try {
                return (IGameCallback) Naming.lookup(mPrefixURL + id);
            } catch (NotBoundException | MalformedURLException | RemoteException e) {
                System.out.println("The primary server is not found");
            }
            return null;
        }

        enum ServerType implements Serializable {
            PRIMARY_SERVER, BACKUP_SERVER
        }
    }
}
