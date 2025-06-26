package search;

import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.concurrent.*;
import java.io.*;
import java.util.*;


public class IndexServer extends UnicastRemoteObject implements Index {
    // ### Configuração
    private ConfigProperties configProperties;

    // ### Pesquisas e dados
    private ConcurrentHashMap<String, String[]> metadata;
    private ConcurrentHashMap<String, Integer> allSearchesWithCount;
    private ArrayList<String> allSearchesWithoutCount;

    // ### Estatísticas
    private ArrayList<String> top10Searches;
    private ConcurrentHashMap<Integer, Long> barrelsStats;
    private ConcurrentHashMap<Integer, ArrayList<Long>> barrelsSearchTimes;
    private ConcurrentHashMap<Integer, Double> barrelsSearchAverage;

    // ### URLs
    private ConcurrentLinkedQueue<String> urlsToIndex;
    private ConcurrentHashMap<String, ArrayList<String>> linkedBy;
    private Set<String> seenUrls;

    // ### Identificadores
    private Integer robotId, barrelId, clientId;

    // ### Interfaces
    private ConcurrentHashMap<Integer, RobotInterface> robotInterfaces;
    private ConcurrentHashMap<Integer, ClientInterface> clientInterfaces;

    private ConcurrentHashMap<Integer, BarrelInterface> barrelInterfaces;

    private Map<Integer, Integer> failCount = new HashMap<>();

    // ### Variaveis de controle
    private Integer minimumBarrels;
    private long lastSavedTime;
    private long counter = 0, timestamp = System.currentTimeMillis();

    public IndexServer() throws RemoteException {
        super();
        initialize();
    }


    // ### Inicializa variáveis
    private void initialize() {
        urlsToIndex = new ConcurrentLinkedQueue<>();
        linkedBy = new ConcurrentHashMap<>();
        allSearchesWithCount = new ConcurrentHashMap<>();
        allSearchesWithoutCount = new ArrayList<>();
        metadata = new ConcurrentHashMap<>();
        seenUrls = new CopyOnWriteArraySet<>();

        robotInterfaces = new ConcurrentHashMap<>();
        barrelInterfaces = new ConcurrentHashMap<>();
        clientInterfaces = new ConcurrentHashMap<>();
        barrelsStats = new ConcurrentHashMap<>();
        barrelsSearchTimes = new ConcurrentHashMap<>();
        barrelsSearchAverage = new ConcurrentHashMap<>();
        top10Searches = new ArrayList<>();
        robotId = barrelId = clientId = 0;

        failCount = new HashMap<>();


        try {
            loadState();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        try {
            IndexServer server = new IndexServer();

            // Server Configs
            server.configProperties = new ConfigProperties("config.properties");

            int serverPort = server.configProperties.getIntProperty("server.port");
            String serverName = server.configProperties.getProperty("server.name");
            server.minimumBarrels = server.configProperties.getIntProperty("barrels.minimum");

            // Launch Server
            Registry registry = LocateRegistry.createRegistry(serverPort);
            registry.rebind(serverName, server);

            server.lastSavedTime = System.currentTimeMillis();
            new Thread(() -> server.periodicSave()).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // ######################  Saves e Loads  ######################
    private void periodicSave() {
        while (true) {
            try {
                Thread.sleep(30000); // Guarda as informações a cada 30s
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastSavedTime >= 30000) { // confirmaçao
                    for (BarrelInterface barrel : barrelInterfaces.values()) {
                        try {
                            saveBarrels(barrel.getIndexedItems());  // salva o primeiro barrel que não der erro
                            break;
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                    saveState();
                    lastSavedTime = currentTime;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveState() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("index_state.obj"))) {
            out.writeObject(urlsToIndex);
            out.writeObject(metadata);
            out.writeObject(allSearchesWithCount);
            out.writeObject(seenUrls);
            out.writeObject(linkedBy);
            System.out.println("Estado salvo com sucesso.");
        } catch (IOException e) {
            System.err.println("Erro ao guardar o estado: " + e.getMessage());
        }
    }

    public void saveBarrels(ConcurrentHashMap<String, ArrayList<String>> indexedItems) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("barrel_state.obj"))) {
            out.writeObject(indexedItems);
            System.out.println("Estado do Barrel salvo com sucesso.");
        } catch (IOException e) {
            System.err.println("Erro ao guardar o estado do Barrel: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public void loadState() {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream("index_state.obj"))) {
            urlsToIndex = (ConcurrentLinkedQueue<String>) in.readObject();
            metadata = (ConcurrentHashMap<String, String[]>) in.readObject();
            allSearchesWithCount = (ConcurrentHashMap<String, Integer>) in.readObject();
            seenUrls = (Set<String>) in.readObject();
            linkedBy = (ConcurrentHashMap<String, ArrayList<String>>) in.readObject();

            // Recalcular variáveis derivadas
            allSearchesWithoutCount = new ArrayList<>(allSearchesWithCount.keySet());

            // Ordenar as pesquisas sem contar para top 10
            allSearchesWithoutCount.sort((search1, search2) ->
                    Integer.compare(allSearchesWithCount.get(search2), allSearchesWithCount.get(search1))
            );

            top10Searches = new ArrayList<>(allSearchesWithoutCount.subList(0, Math.min(10, allSearchesWithoutCount.size())));

            updateBarrelsStats();

            System.out.println("Estado carregado com sucesso.");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro ao carregar o estado: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public ConcurrentHashMap<String, ArrayList<String>> loadBarrels() {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream("barrel_state.obj"))) {
            ConcurrentHashMap<String, ArrayList<String>> indexedItems = (ConcurrentHashMap<String, ArrayList<String>>) in.readObject();
            System.out.println("Estado do Barrel carregado com sucesso.");
            return indexedItems;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro ao carregar o estado do Barrel: " + e.getMessage());
        }
        return new ConcurrentHashMap<>();
    }


    // ######################  Subscribes  ######################
    public long subscribeRobot(RobotInterface robot) throws RemoteException {
        robotInterfaces.put(robotId, robot);
        return robotId++;
    }

    public long subscribeClient(ClientInterface client) throws RemoteException {
        System.out.println("New client: " + clientId);
        clientInterfaces.put(clientId, client);
        return clientId++;
    }

    public long subscribeBarrel(BarrelInterface barrel) throws RemoteException {

        long biggestIndex = 0L;
        BarrelInterface biggestBarrel = barrel;
        if (!barrelInterfaces.isEmpty()) {
            for (BarrelInterface barrelTemp : barrelInterfaces.values()) {
                try {
                    long size = barrelTemp.getIndexSize();
                    if (size > biggestIndex) {
                        biggestIndex = size;
                        biggestBarrel = barrelTemp;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            barrel.updateIndexedItems(biggestBarrel.getIndexedItems());
        } else {
            barrel.updateIndexedItems(loadBarrels());
        }

        barrelInterfaces.put(barrelId, barrel);
        barrelsStats.put(barrelId, barrel.getIndexSize());
        barrelsSearchTimes.put(barrelId, new ArrayList<>());
        updateBarrelsSearchAverage();

        return barrelId++;
    }

    // ######################  stats/clients  ######################
    public void sendStatsToClients() {
        List<Integer> clientsToRemove = new ArrayList<>();
        final int MAX_FAILS = 3;  // número de tentativas falhadas antes de remover

        for (Integer clientId : clientInterfaces.keySet()) {
            ClientInterface client = clientInterfaces.get(clientId);

            try {
                client.receiveStats(top10Searches, barrelsStats, barrelsSearchAverage);
                failCount.put(clientId, 0); // reset falhas se sucesso
            } catch (Exception e) {
                int fails = failCount.getOrDefault(clientId, 0) + 1;
                failCount.put(clientId, fails);
                System.out.println("Falha comunicação com cliente " + clientId + ", tentativa " + fails);

                if (fails >= MAX_FAILS) {
                    clientsToRemove.add(clientId);
                    System.out.println("Cliente " + clientId + " removido após " + fails + " falhas consecutivas.");
                }
            }
        }

        for (Integer clientId : clientsToRemove) {
            clientInterfaces.remove(clientId);
            failCount.remove(clientId);
        }
    }

    // ######################  URLs  ######################
    public synchronized String takeNext() throws RemoteException {
        while (urlsToIndex.isEmpty()) {
            try {
                wait();  // Aguarda até que uma URL seja adicionada à fila
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String url = urlsToIndex.poll();

        counter++;
        if (counter >= 10) {
            printStats();
        }

        return url;
    }

    public synchronized void putNew(String url,String indexing) throws RemoteException {
        if(indexing.compareTo("")!=0) {
            if (linkedBy.containsKey(url)) {
                if (linkedBy.get(url).contains(indexing)) {
                    return;
                }
                linkedBy.get(url).add(indexing);
            } else {
                ArrayList<String> temp = new ArrayList<>();
                temp.add(indexing);
                linkedBy.put(url, temp);
            }
        }

        // Evita indexar URLs já vistas para nao entrar em loop
        if (seenUrls.contains(url)) {
            return;
        }

        seenUrls.add(url);
        urlsToIndex.add(url);
        notifyAll();    // Acorda os downloaders que estavam adormecidos à espera de urls
    }


    // ######################  Indexation  ######################
    public void addToIndex(String word, String url) throws RemoteException {
        int activeBarrels = 0;
        List<Integer> barrelsToRemove = new ArrayList<>();

        // há pelo menos minimumBarrels ativos e a retornar "ACK"
        while (activeBarrels < minimumBarrels) {
            activeBarrels = 0;
            barrelsToRemove.clear();

            for (Integer id : barrelInterfaces.keySet()) {
                BarrelInterface barrel = barrelInterfaces.get(id);
                int retries = 3;
                long time = 100;

                while (retries > 0) {
                    try {
                        // tenta receber acknowledge
                        if ("ACK".equals(barrel.call())) {
                            activeBarrels++;
                            break;
                        } else {
                            throw new RemoteException("Barrel " + id + " retornou sem ACK no call");
                        }
                    } catch (Exception e) {
                        retries--;
                        System.err.println("Erro a acessar barrel " + id + ". Tentativas restantes: " + retries);
                        try {
                            Thread.sleep(time); // tempo de espera
                            time *= 2; // Dobra o tempo
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt(); // Interrompe se a thread for interrompida
                        }
                    }
                }

                // se acabou as retries mete o barrel para ser removido
                if (retries == 0) {
                    barrelsToRemove.add(id);
                }
            }

            // Remover barrels problemáticos
            for (Integer id : barrelsToRemove) {
                barrelInterfaces.remove(id);
                barrelsStats.remove(id);
                barrelsSearchTimes.remove(id);
                barrelsSearchAverage.remove(id);
                updateBarrelsStats();
                updateBarrelsSearchAverage();
            }
        }

        //pelo menos minimumBarrels conseguem fazer indexação e retornar "ACK"
        int success = 0;
        barrelsToRemove.clear();

        for (Integer id : barrelInterfaces.keySet()) {
            BarrelInterface barrel = barrelInterfaces.get(id);
            int retries = 3;
            long waitTime = 100;

            while (retries > 0) {
                try {
                    // tenta receber acknowledge
                    if ("ACK".equals(barrel.addToIndex(word, url, this))) {
                        success++;
                        break;
                    } else {
                        throw new RemoteException("Barrel " + id + " não retornou ACK na indexação");
                    }
                } catch (RemoteException e) {
                    retries--;
                    System.err.println("Erro ao enviar para barrel " + id + ". Tentativas restantes: " + retries);
                    try {
                        Thread.sleep(waitTime); // Espera antes de tentar novamente
                        waitTime *= 2; // Dobra o tempo de espera a cada falha
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // Interrompe se a thread for interrompida
                    }
                }
            }

            // Se gastar as retries, mete o barrel para ser removido
            if (retries == 0) {
                barrelsToRemove.add(id);
            }
        }

        // Remover barrels problemáticos
        for (Integer id : barrelsToRemove) {
            barrelInterfaces.remove(id);
            barrelsStats.remove(id);
            barrelsSearchTimes.remove(id);
            barrelsSearchAverage.remove(id);
            updateBarrelsSearchAverage();
            updateBarrelsStats();
        }

        if (success < minimumBarrels) {
            System.err.println("Não foi possível garantir reliable multicast para " + minimumBarrels + " barrels.");
        }
    }


    // ######################  Search  ######################
    private int currentBarrelIndex = 0;  // Índice para Round Robin

    public synchronized ArrayList<String> searchWord(String word, int page) throws RemoteException {
        registerSearch(word);

        if (barrelInterfaces.isEmpty()) {
            return new ArrayList<>();
        }

        List<Integer> keys = new ArrayList<>(barrelInterfaces.keySet());
        int attempts = 0;

        while (attempts < keys.size()) {
            int key = keys.get(currentBarrelIndex);
            BarrelInterface barrel = barrelInterfaces.get(key);

            try {
                if ("ACK".equals(barrel.call())) {
                    long startTime = System.currentTimeMillis();
                    ArrayList<String> urls = barrel.searchWord(word, linkedBy, page);
                    long endTime = System.currentTimeMillis();
                    long elapsedTime = endTime - startTime;

                    if (!barrelsSearchTimes.containsKey(key)) {
                        barrelsSearchTimes.put(key, new ArrayList<>());
                    }
                    barrelsSearchTimes.get(key).add(elapsedTime);
                    updateBarrelsSearchAverage();

                    currentBarrelIndex = (currentBarrelIndex + 1) % keys.size();
                    return urls;
                }

            } catch (Exception e) {
                barrelInterfaces.remove(key);
                barrelsStats.remove(key);
                barrelsSearchTimes.remove(key);
                barrelsSearchAverage.remove(key);
                updateBarrelsSearchAverage();
                updateBarrelsStats();
                keys.remove(currentBarrelIndex);
                if (keys.isEmpty()) break;
            }

            attempts++;
            currentBarrelIndex = (currentBarrelIndex + 1) % keys.size();
        }

        return new ArrayList<>();
    }

    public synchronized void registerSearch(String search) throws RemoteException {
        if (!allSearchesWithCount.containsKey(search)) {
            allSearchesWithCount.put(search, 0);
            allSearchesWithoutCount.add(search);
        } else {
            allSearchesWithCount.put(search, allSearchesWithCount.get(search) + 1);
        }

        allSearchesWithoutCount.sort((search1, search2) -> {
            int count1 = allSearchesWithCount.get(search1);
            int count2 = allSearchesWithCount.get(search2);
            return Integer.compare(count2, count1);  // Ordem decrescente de contagem
        });

        ArrayList<String> top10Now = new ArrayList<>(allSearchesWithoutCount.subList(0, Math.min(10, allSearchesWithoutCount.size())));
        if (!top10Now.equals(top10Searches)) {
            top10Searches = top10Now;
            sendStatsToClients();
        }
    }


    // ######################  URLs/Links  ######################
    public ArrayList<String> searchLinks(String link) throws RemoteException {
        return linkedBy.getOrDefault(link, new ArrayList<>());
    }

    public void addMeta(String url, String[] strings) throws RemoteException{
        if(!metadata.containsKey(url)){
            metadata.put(url,strings);
        }
    }

    public ConcurrentHashMap<String, String[]> getMeta(ArrayList<String> retrievedUrls) throws RemoteException{
        ConcurrentHashMap<String, String[]> retrieved = new ConcurrentHashMap<>();
        for(String key:metadata.keySet()){
            if(retrievedUrls.contains(key)){
                retrieved.put(key,metadata.get(key));
            }
        }
        return retrieved;
    }


    // ######################  STATS  ######################


    // ######################  Global Stats  ######################
    public void printStats() throws RemoteException {
        counter = 0;
        System.out.print("Time:");
        System.out.println((System.currentTimeMillis() - timestamp)/10);
        timestamp = System.currentTimeMillis();
        System.out.print("Used memory:");
        System.out.println(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        System.out.print("Free memory:");
        System.out.println(Runtime.getRuntime().freeMemory());
        WorkerStatsCallback();
        System.out.println("# SeenUrls: " + seenUrls.size());
    }

    public void WorkerStatsCallback() throws RemoteException {
        System.out.println("--------\nRobot stats:");
        for (Integer id : robotInterfaces.keySet()) {
            try {
                RobotInterface robot = robotInterfaces.get(id);
                System.out.println("Robot " + id + ": " + robot.getStats());
            }
            catch(Exception e){
                System.out.println("Robot " + id + ": failed to retrieve stats");
            }
        }
    }


    // ######################  Update Stats  ######################
    public void updateBarrelsStats() throws RemoteException {
        for (Integer id : barrelInterfaces.keySet()) {
            try {
                BarrelInterface barrel = barrelInterfaces.get(id);
                if (barrel != null) {
                    long indexSize = barrel.getIndexSize();
                    barrelsStats.put(id, indexSize);
                    sendStatsToClients();
                }
            } catch (Exception e) {
                System.out.println("Erro ao acessar o Barrel " + id + ", removendo...");
                barrelInterfaces.remove(id);
                barrelsStats.remove(id);
                barrelsSearchAverage.remove(id);
                barrelsSearchTimes.remove(id);
                updateBarrelsSearchAverage();
                sendStatsToClients();
            }
        }
    }

    public synchronized void updateBarrelsSearchAverage() throws RemoteException {
        for (Integer barrelId : barrelsSearchTimes.keySet()) {
            ArrayList<Long> allTimes = barrelsSearchTimes.get(barrelId);
            double totalTime = 0.0;

            for (Long time : allTimes) {
                totalTime += time;
            }

            double averageTime = allTimes.isEmpty() ? -1.0 : (totalTime / allTimes.size())/1000.0;

            if (barrelsSearchAverage.get(barrelId) == null || barrelsSearchAverage.get(barrelId) != averageTime) {
                barrelsSearchAverage.put(barrelId, averageTime);
                sendStatsToClients();
            }
        }
    }


    // ######################  Get Stats  ######################
    public synchronized ArrayList<String> getTop10Searches() throws RemoteException{
        return top10Searches;
    }

    public synchronized ConcurrentHashMap<Integer, Long> getBarrelsStats() throws RemoteException {
        return barrelsStats;
    }

    public synchronized ConcurrentHashMap<Integer, Double> getBarrelsSearchAverage() throws RemoteException {
        return barrelsSearchAverage;
    }
}

