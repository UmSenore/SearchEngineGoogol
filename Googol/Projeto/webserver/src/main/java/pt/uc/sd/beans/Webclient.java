package pt.uc.sd.beans;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import search.ClientInterface;
import search.Index;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Webclient extends UnicastRemoteObject implements ClientInterface {
    private ConfigProperties configProperties;
    private SimpMessagingTemplate template;

    Index index;
    private long id;
    @Autowired
    public Webclient(SimpMessagingTemplate template) throws RemoteException {
        super();
        try {
            configProperties = new ConfigProperties("config.properties");
            int serverPort = configProperties.getIntProperty("server.port");
            String serverHost = configProperties.getProperty("server.host");
            String serverName = configProperties.getProperty("server.name");

            index = (Index) LocateRegistry.getRegistry(serverHost, serverPort).lookup(serverName);
            id = index.subscribeClient((ClientInterface) this);
        }
        catch(Exception e){
            System.out.println("Bad connection");
            e.printStackTrace();
        }
        this.template = template;
    }

    public String search(String input,int page) throws RemoteException {
        // search indexed urls
        StringBuilder response = new StringBuilder();
        ArrayList<String> retrieved_urls = index.searchWord(input, page);
        ConcurrentHashMap<String, String[]> retrieved_meta = index.getMeta(retrieved_urls);
        if (!retrieved_urls.isEmpty()) {
            for (String url : retrieved_urls) {
                response.append(String.format(
                        "<tr><td><a href='%s' target='_blank'>%s</a><br><strong>%s</strong><br>%s</td></tr>\n",
                        url, url, retrieved_meta.get(url)[0], retrieved_meta.get(url)[1]
                ));
            }
        } else {
            response.append("    No results found.");
        }
        return response.toString();
    }
    public String linkedPages(String input) throws RemoteException {
        StringBuilder builder = new StringBuilder();
        ArrayList<String> retrieved_urls = index.searchLinks(input);
        for(String i:retrieved_urls){
            builder.append(i).append("\n");
        }
        return builder.toString();
    }
    public void addURL(String input) throws RemoteException {
        System.out.println("Adding...\n");
        index.putNew(input, "");
    }

    public void receiveStats(ArrayList<String> top10Searches, ConcurrentHashMap<Integer, Long> barrelsStats, ConcurrentHashMap<Integer, Double> searchTimes) throws RemoteException {
        Map<String, Object> statsJson = new HashMap<>();


        // Top 10 pesquisas
        List<String> topSearches = new ArrayList<>();
        if (top10Searches.isEmpty()) {
            topSearches.add("Ainda não foram realizadas pesquisas!");
        } else {
            int i = 1;
            for (String search : top10Searches) {
                topSearches.add(i + ": " + search);
                i++;
            }
        }
        statsJson.put("topSearches", topSearches);

        List<Map<String,Object>> barrels = new ArrayList<>();

        // barrelsStats: Map<Integer, Long>  (id -> indexSize)
        // searchTimes: Map<Integer, Double> (id -> avg responseTime)

        Set<Integer> allBarrelIds = new HashSet<>(barrelsStats.keySet());

        for (Integer barrelId : allBarrelIds) {
            Map<String,Object> barrel = new HashMap<>();
            barrel.put("id", barrelId);
            barrel.put("indexSize", barrelsStats.getOrDefault(barrelId, 0L));

            double avgTime = searchTimes.getOrDefault(barrelId, -1.0);
            if (avgTime == -1.0) {
                barrel.put("responseTime", "Este barrel ainda não fez nenhuma pesquisa");
            } else {
                barrel.put("responseTime", avgTime);
            }

            barrels.add(barrel);
        }
        statsJson.put("barrels", barrels);


        sendStats(statsJson);
        /*
        {
          "content": {
            "topSearches": ["1: banana", "2: maçã"],
            "barrels": [
              { "id": 1, "indexSize": 120, "responseTime": 15.2 },
              { "id": 2, "indexSize": 89, "responseTime": -1 }
            ]
          }
        }
         */
    }

    public void sendStats(Map<String, Object> stats) {
        this.template.convertAndSend("/topic/statistics", stats);
    }

    public ArrayList<String> getTop10Searches() throws RemoteException{
        return index.getTop10Searches();
    }

    public ConcurrentHashMap<Integer, Long> getBarrelsStats() throws RemoteException {
        return index.getBarrelsStats();
    }

    public ConcurrentHashMap<Integer, Double> getBarrelsSearchAverage() throws RemoteException {
        return index.getBarrelsSearchAverage();
    }

}