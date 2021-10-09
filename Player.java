import java.awt.*;
import java.io.Serializable;

public class Player implements Serializable {
    public String playerID;
    public Point position;
    public volatile Game.RemoteServerImpl.ServerType serverType;
    public int score;

    public Player(String playerID) {
        this.playerID = playerID;
        this.position = new Point(0, 0);
    }
}
