package search;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public interface ClientInterface extends Remote {
    public void receiveStats(ArrayList<String> top10Searches, ConcurrentHashMap<Integer, Long> barrelsStats, ConcurrentHashMap<Integer, Double> searchTimes) throws RemoteException;

}