import java.util.*;
import java.awt.*;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GameState implements Serializable {
    public Set<Player> players;
    public Set<Point> treasures;
    public final int N;
    public final int K;
    private HashMap<String, Point> mPlayerPos = new HashMap<>();
    private HashMap<String, String> mPlayerScores = new HashMap<>();

    public GameState(int size, int k) {
        N = size;
        K = k;
        players = ConcurrentHashMap.newKeySet();
        treasures = ConcurrentHashMap.newKeySet();
    }

    public HashMap<String, Point> getLatestPlayers() {
        replicateLatestData();
        return mPlayerPos;
    }

    public HashMap<String, String> getLatestScores() {
        replicateLatestData();
        return mPlayerScores;
    }

    public List<Point> getTreasures() {
        return new ArrayList<>(treasures);
    }

    private synchronized void replicateLatestData() {
        mPlayerPos = new HashMap<>();
        mPlayerScores = new HashMap<>();
        for (Player p : players) {
            mPlayerPos.put(p.playerID, p.position);
            String server = "";
            if (p.serverType != null) {
                server = p.serverType.toString();
            }
            mPlayerScores.put(p.playerID, p.playerID + "  " + p.score + "  " + server);
        }
    }
}


