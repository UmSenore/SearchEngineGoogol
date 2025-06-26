package search;

import org.jsoup.nodes.Document;

import java.rmi.*;
import java.util.*;

public interface RobotInterface extends Remote {
    public String getStats() throws RemoteException;
}
