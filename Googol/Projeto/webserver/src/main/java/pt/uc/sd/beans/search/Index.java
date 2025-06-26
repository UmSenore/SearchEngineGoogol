package search;

import search.ClientInterface;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public interface Index extends Remote {
    public  String takeNext() throws RemoteException;
    public void putNew(String url,String indexing) throws RemoteException;
    public void addToIndex(String word, String url) throws RemoteException;
    public void addMeta(String url, String[] strings) throws RemoteException;
    public ArrayList<String> searchWord(String word, int page) throws RemoteException;
    public ArrayList<String> searchLinks(String link) throws RemoteException;
    public long subscribeClient(ClientInterface client) throws RemoteException;
    public ConcurrentHashMap<String, String[]> getMeta(ArrayList<String> retrievedUrls) throws RemoteException;
    public void printStats() throws RemoteException;
    public void registerSearch(String search) throws RemoteException;
    public ArrayList<String> getTop10Searches() throws RemoteException;
    public void updateBarrelsStats() throws RemoteException;
    public  ConcurrentHashMap<Integer, Long> getBarrelsStats() throws RemoteException;
    public  ConcurrentHashMap<Integer, Double> getBarrelsSearchAverage() throws RemoteException;
    public  void updateBarrelsSearchAverage() throws RemoteException;
}
