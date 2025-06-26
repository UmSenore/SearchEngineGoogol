package search;

import java.rmi.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public interface BarrelInterface extends Remote {
    public String addToIndex(String word, String url, Index index) throws RemoteException;
    public ArrayList<String> searchWord(String word, ConcurrentHashMap<String, ArrayList<String>> LinkedBy, int page) throws RemoteException;
    public String call() throws RemoteException;
    public long getIndexSize() throws RemoteException;
    public ConcurrentHashMap<String, ArrayList<String>> getIndexedItems() throws RemoteException;
    public void updateIndexedItems(ConcurrentHashMap<String, ArrayList<String>> indexedItems) throws RemoteException;
}
