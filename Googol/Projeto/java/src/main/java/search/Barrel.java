package search;

import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;



public class Barrel extends UnicastRemoteObject implements BarrelInterface {
    private ConfigProperties configProperties;
    private long id;
    private ConcurrentHashMap<String, ArrayList<String>> indexedItems;

    public Barrel() throws RemoteException {
        super();

        indexedItems = new ConcurrentHashMap<>();
    }

    public static void main(String[] args) {
        try {
            Barrel barrel = new Barrel();

            barrel.configProperties = new ConfigProperties("config.properties");

            int serverPort = barrel.configProperties.getIntProperty("server.port");
            String serverHost = barrel.configProperties.getProperty("server.host");
            String serverName = barrel.configProperties.getProperty("server.name");

            Index index = (Index) LocateRegistry.getRegistry(serverHost, serverPort).lookup(serverName);
            barrel.id = index.subscribeBarrel((BarrelInterface) barrel);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String addToIndex(String word, String url, Index index) throws RemoteException {
        if (indexedItems.containsKey(word)) {
            if (indexedItems.get(word).contains(url)) {
                return "ACK";   // para garantir que executou a função
            }
            indexedItems.get(word).add(url);
        } else {
            ArrayList<String> temp = new ArrayList<>();
            temp.add(url);
            indexedItems.put(word, temp);
            index.updateBarrelsStats();
        }

        return "ACK";
    }

    public ArrayList<String> searchWord(String search, ConcurrentHashMap<String, ArrayList<String>> LinkedBy, int page) throws RemoteException {
        String[] words = search.toLowerCase().trim().split("\\s+");
        if (words.length == 0) return new ArrayList<>();

        ArrayList<String> urls = new ArrayList<>(indexedItems.getOrDefault(words[0], new ArrayList<>()));

        for (int i = 1; i < words.length; i++) {
            ArrayList<String> currentUrls = indexedItems.getOrDefault(words[i], new ArrayList<>());
            ArrayList<String> temp = new ArrayList<>();

            for (String url : urls) {
                if (currentUrls.contains(url)) {
                    temp.add(url); // Apenas mantém os URLs presentes em todas as palavras
                }
            }
            if((float)temp.size()/ urls.size()<0.7) //avalia se a palavra aparece em 70% dos urls (stop word)
                urls = temp; // ignora stop words
        }

        if (urls.isEmpty()) {
            return new ArrayList<>();
        }

        // Ordenar os URLs pelo número de links apontando para eles
        urls.sort((url1, url2) -> {
            int links1 = LinkedBy.getOrDefault(url1, new ArrayList<>()).size();
            int links2 = LinkedBy.getOrDefault(url2, new ArrayList<>()).size();
            return Integer.compare(links2, links1); // Ordenação decrescente
        });

        // Definir o intervalo dos resultados a retornar
        int startIndex = page * 10;
        int endIndex = Math.min(startIndex + 10, urls.size());  // escolhe o size caso não haja links suficientes (10)

        if (startIndex >= urls.size()) {
            return new ArrayList<>(); // Se a página estiver fora do intervalo, retorna um array vazio
        }

        return new ArrayList<>(urls.subList(startIndex, endIndex));
    }

    // função usada para garantir reliable multicast
    public String call() throws RemoteException{
        return "ACK";
    }

    public long getIndexSize() throws RemoteException  {
        return indexedItems.size();
    }

    public ConcurrentHashMap<String, ArrayList<String>> getIndexedItems() throws RemoteException {
        return indexedItems;
    }

    public void updateIndexedItems(ConcurrentHashMap<String, ArrayList<String>> indexedItems) throws RemoteException {
        this.indexedItems = indexedItems;
    }


}
