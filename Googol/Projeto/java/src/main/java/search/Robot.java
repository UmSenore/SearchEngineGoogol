package search;

import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

public class Robot extends UnicastRemoteObject implements RobotInterface {
    private ConfigProperties configProperties;
    private long urlsProcessed;
    private long id;

    public Robot() throws RemoteException {
        super();
    }

    public static void main(String[] args) {
        try {
            Robot robot = new Robot();

            robot.configProperties = new ConfigProperties("config.properties");

            int serverPort = robot.configProperties.getIntProperty("server.port");
            String serverHost = robot.configProperties.getProperty("server.host");
            String serverName = robot.configProperties.getProperty("server.name");

            Index index = (Index) LocateRegistry.getRegistry(serverHost, serverPort).lookup(serverName);
            long id = index.subscribeRobot((RobotInterface) robot);

            while (true) {
                String url = index.takeNext();
                robot.processUrl(index, url);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void processUrl(Index index, String url) {
        urlsProcessed++;
        System.out.println(url);

        try {
            Document doc = Jsoup.connect(url).get();
            String title = doc.title();
            String citation = getFirstParagraphAfterHeader(doc);

            if (citation != null && citation.length() > 200) {
                citation = citation.substring(0, 200) + "...";
            }

            System.out.println(title + '\n' + citation);
            index.addMeta(url,new String[] {title,citation});

            // Indexa palavras da página
            StringTokenizer words = new StringTokenizer(doc.text(), " ,.!?:/#%");
            while (words.hasMoreElements()) {
                index.addToIndex(words.nextToken().toLowerCase(), url);
            }

            // Adiciona novos links encontrados
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                try {
                    // adiciona tambem o proprio link do site como um dos que está a apontar
                    index.putNew(link.attr("abs:href"), url);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.out.println("Bounced off url!");
        }
    }

    private static String getFirstParagraphAfterHeader(Document doc) {
        Element metaDescription = doc.selectFirst("meta[name=description]");    // primeiro vai ver se o url tme meta description
        if(metaDescription!=null){
            return metaDescription.attr("content");
        }
        Elements headers = doc.select("h1, h2, h3");

        if (!headers.isEmpty()) {
            Element firstHeader = headers.first();  // Vai procurar o primeiro paragrafo a seguir a um header

            Elements paragraphs = doc.select("p, li");
            for (Element paragraph : paragraphs) {
                if (paragraph.elementSiblingIndex() > firstHeader.elementSiblingIndex()) {
                    return paragraph.text().trim();
                }
            }
        }
        return "No description";
    }

    public String getStats() throws RemoteException {
        return "Urls processed: " + urlsProcessed;
    }

}
