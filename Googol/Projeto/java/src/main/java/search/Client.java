package search;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class Client extends UnicastRemoteObject implements ClientInterface {
    private ConfigProperties configProperties;
    boolean statsMenuOn = false;
    private long  id;

    public Client() throws RemoteException {
        super();
    }

    public static void main(String[] args) {
        try {
            Client client = new Client();

            client.configProperties = new ConfigProperties("config.properties");

            int serverPort = client.configProperties.getIntProperty("server.port");
            String serverHost = client.configProperties.getProperty("server.host");
            String serverName = client.configProperties.getProperty("server.name");

            Index index = (Index) LocateRegistry.getRegistry(serverHost, serverPort).lookup(serverName);
            client.id = index.subscribeClient((ClientInterface) client);

            Scanner scan = new Scanner(System.in);
            String input;

            // ######################  MENU PRINCIPAL  ######################

            System.out.println("\n" +
                    "   █████████                                           ████ \n" +
                    "  ███░░░░░███                                         ░░███ \n" +
                    " ███     ░░░    ██████    ██████    ███████   ██████   ░███ \n" +
                    "░███           ███░░███  ███░░███  ███░░███  ███░░███  ░███ \n" +
                    "░███  ██████  ░███ ░███ ░███ ░███ ░███ ░███ ░███ ░███  ░███ \n" +
                    "░░███  ░░███  ░███ ░███ ░███ ░███ ░███ ░███ ░███ ░███  ░███ \n" +
                    " ░░█████████  ░░██████  ░░██████  ░░███████ ░░██████   ██████\n" +
                    "  ░░░░░░░░░    ░░░░░░    ░░░░░░    ░░░░░███  ░░░░░░   ░░░░░░ \n" +
                    "                                 ███ ░███               \n" +
                    "                                ░░██████                \n" +
                    "                                 ░░░░░░                 ");
            System.out.println();
            System.out.println("//==============================================\\\\");
            System.out.println("||  Add URLs for indexing: Start with \"http\"    ||");
            System.out.println("||  Search keywords: Start with anything else   ||");
            System.out.println("||  Press 's' for statistics                    ||");
            System.out.println("\\\\==============================================//");

            while (true) {
                System.out.print("> ");
                input = scan.nextLine();
                String[] url_begin = input.split(":");

                // ######################  STATS  ######################

                if (input.equals("s")) {
                    client.statsMenuOn = true;
                    System.out.println("=============================================");
                    System.out.println("Top 10 Pesquisas: ");
                    int i = 1;
                    ArrayList<String> top10Searches = index.getTop10Searches();
                    if (top10Searches.isEmpty()) {
                        System.out.println("Ainda não foram realizadas pesquisas!");
                    }
                    for (String search : top10Searches) {
                        System.out.println(i + ": " + search);
                        i++;
                    }

                    i = 1;
                    ConcurrentHashMap<Integer, Long> barrelsStats = index.getBarrelsStats();
                    // barrels ativos e tamanhos de índice
                    System.out.println("Lista de Barrels ativos: ");
                    if (barrelsStats.isEmpty()) {
                        System.out.println("Ainda não há barrels ativos!");
                    }
                    for (Integer id : barrelsStats.keySet()) {
                        System.out.println("Barrel ID: " + id + " -> Index Size: " + barrelsStats.get(id));
                    }

                    i = 1;
                    ConcurrentHashMap<Integer, Double> searchTimes = index.getBarrelsSearchAverage();
                    // tempo médio de resposta por Barrel
                    System.out.println("Tempo médio de resposta a pesquisas: ");
                    for (Integer id : searchTimes.keySet()) {
                        System.out.print("Barrel ID: " + id);
                        double averageTime = searchTimes.get(id);

                        if (averageTime == -1L) {   //  valor inicial de averageTime
                            System.out.println("  --> Average Time: Este barrel ainda não fez nenhuma pesquisa");
                        } else {
                            System.out.println("  --> Average Time: " + averageTime);
                        }
                    }

                    System.out.println("=============================================");

                // ######################  ADD URL  ######################

                } else if (url_begin[0].equals("https") || url_begin[0].equals("http")) {
                    client.statsMenuOn = false;

                    // Add urls for indexing
                    index.putNew(input, "");
                    System.out.println("-> Server: added " + input.substring(0, Math.min(input.length(), 50)) + "...");


                // ######################  PESQUISA  ######################

                } else if(!input.equals("")){   // ignora espaços vazios
                    client.statsMenuOn = false;
                    int page = 0;

                    while (true) {
                        // search indexed urls
                        ArrayList<String> retrieved_urls = index.searchWord(input, page);
                        ConcurrentHashMap<String, String[]> retrieved_meta = index.getMeta(retrieved_urls);

                        if (retrieved_meta.isEmpty()) System.out.println("Está empty");

                        System.out.println("-> Server: ");
                        if (!retrieved_urls.isEmpty()) {
                            System.out.println("{");
                            for (String url : retrieved_urls) {
                                System.out.println("    " + url);
                                System.out.println("    " + retrieved_meta.get(url)[0]);
                                System.out.println("    " + retrieved_meta.get(url)[1]);
                            }
                            System.out.println("}");

                            String response;
                            if (retrieved_urls.size() != 10) {  // Não há mais links
                                System.out.println("\nWrite a link to see pages that index that link or Press Enter to exit");

                                response = scan.nextLine().toLowerCase().trim();

                                url_begin = response.split(":");
                                if ((url_begin[0].equals("https") || url_begin[0].equals("http")) && retrieved_urls.contains(response)) {
                                    try {
                                        retrieved_urls = index.searchLinks(response);   // busar os links que apontam para aquele link
                                    } catch (RemoteException e) {
                                        System.out.println("Bad connection");
                                        retrieved_urls.clear();
                                    }

                                    // mostra os links que apontam para o escolhido
                                    if (!retrieved_urls.isEmpty()) {
                                        System.out.println("-> Server: Links {");
                                        for (String url : retrieved_urls) {
                                            System.out.println("    " + url);
                                        }
                                        System.out.println("}");
                                    } else {
                                        System.out.println("No page links to this url!");
                                    }
                                }
                                else break;

                            } else {
                                // Há mais que 10 urls
                                System.out.println("\nPress m to see more, write a link to see pages that index that link or Press Enter to exit");
                                response = scan.nextLine().toLowerCase().trim();

                                url_begin = response.split(":");
                                if ((url_begin[0].equals("https") || url_begin[0].equals("http")) && retrieved_urls.contains(response)) {
                                    try {
                                        retrieved_urls = index.searchLinks(response);
                                    } catch (RemoteException e) {
                                        System.out.println("Bad connection");
                                        retrieved_urls.clear();
                                    }
                                    // mostra os links que apontam para o escolhido
                                    if (!retrieved_urls.isEmpty()) {
                                        System.out.println("-> Server: Links {");
                                        for (String url : retrieved_urls) {
                                            System.out.println("    " + url);
                                        }
                                        System.out.println("}");
                                    } else {
                                        System.out.println("No page links to this url!");
                                    }
                                } else if (!response.equals("m")) break;

                                page++; // proxima pagina de 10 links
                            }

                        } else {

                            System.out.println("    No results found.");
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Bad connection");
            e.printStackTrace();
        }
    }

    public void receiveStats(ArrayList<String> top10Searches, ConcurrentHashMap<Integer, Long> barrelsStats, ConcurrentHashMap<Integer, Double> searchTimes) throws RemoteException {
        if (statsMenuOn) {
            System.out.println("=============================================");
            System.out.println("Top 10 Pesquisas: ");
            int i = 1;
            if (top10Searches.isEmpty()) {
                System.out.println("Ainda não foram realizadas pesquisas!");
            }
            for (String search : top10Searches) {
                System.out.println(i + ": " + search);
                i++;
            }

            i = 1;
            // barrels ativos e tamanhos de índice
            System.out.println("Lista de Barrels ativos: ");
            if (barrelsStats.isEmpty()) {
                System.out.println("Ainda não há barrels ativos!");
            }
            for (Integer id : barrelsStats.keySet()) {
                System.out.println("Barrel ID: " + id + " -> Index Size: " + barrelsStats.get(id));
            }

            i = 1;
            // tempo médio de resposta por Barrel
            System.out.println("Tempo médio de resposta a pesquisas: ");
            for (Integer id : searchTimes.keySet()) {
                System.out.print("Barrel ID: " + id);
                double averageTime = searchTimes.get(id);

                if (averageTime == -1L) {   //  valor inicial de averageTime
                    System.out.println("  --> Average Time: Este barrel ainda não fez nenhuma pesquisa");
                } else {
                    System.out.println("  --> Average Time: " + averageTime);
                }
            }

            System.out.println("=============================================");
        }
    }
}