package search;

import java.rmi.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public interface ClientInterface extends Remote {
    public void receiveStats(ArrayList<String> top10Searches, ConcurrentHashMap<Integer, Long> barrelsStats, ConcurrentHashMap<Integer, Double> searchTimes) throws RemoteException;
}