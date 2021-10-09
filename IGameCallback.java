import java.awt.*;
import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IGameCallback extends Remote, Serializable {
    GameState Move(String playerId, String moveType, Point pos) throws RemoteException;

    void quit(String playerId) throws RemoteException;

    GameState join(Player playerId) throws RemoteException;

    void generateTreasure(int num) throws RemoteException;

    GameState sync() throws RemoteException;
}
