package pt.uc.sd;

        import org.springframework.context.annotation.Bean;
        import org.springframework.context.annotation.Scope;
        import org.springframework.context.annotation.ScopedProxyMode;
        import org.springframework.messaging.simp.SimpMessagingTemplate;
        import org.springframework.ui.Model;
        import org.springframework.web.bind.annotation.*;
        import org.springframework.web.context.WebApplicationContext;
        import org.springframework.web.util.HtmlUtils;
        import org.springframework.stereotype.Controller;
        import org.springframework.beans.factory.annotation.Value;
        import org.springframework.http.*;
        import org.springframework.web.client.RestTemplate;
        import search.ClientInterface;
        import pt.uc.sd.beans.Webclient;

        import java.rmi.RemoteException;
        import java.util.*;
        import java.util.concurrent.ConcurrentHashMap;


@Controller
public class MessagingController {

    @Value("AIzaSyD_bbvXyhh3E_gQkhIHSIGQK3oN7V9x778")
    private String apiKey;
    private final Webclient user;
    public MessagingController(Webclient user) {
        this.user = user;
    }
    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public Webclient user(SimpMessagingTemplate template) throws RemoteException {
        return new Webclient(template);
    }

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";
    public String complete(String data,int page) throws RemoteException {
        StringBuilder end = new StringBuilder("");
        String prompt = "Dá informação num pequeno parágrafo em portugues de portugal sobre:\n"
                + String.join("\n", data);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                GEMINI_URL + apiKey,
                entity,
                Map.class
        );

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            var candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                var content = (Map<String, Object>) candidates.get(0).get("content");
                var parts = (List<Map<String, Object>>) content.get("parts");
                end.append ((String) parts.get(0).get("text"));
            }
        }
        else end.append("Could not generate summary.");
        end.append("\n");
        end.append(user.search(data,page));
        return end.toString();
    }

    @GetMapping("/")
    public String index() {
        return "index";  // Spring procura em templates/index.html
    }

    @GetMapping("/search")
    public String greeting(@RequestParam(name = "message") String messageContent,@RequestParam(name = "page") int page, Model model) throws RemoteException {

        String result = complete(HtmlUtils.htmlEscape(messageContent), page);

        String[] resultParts = result.split("\n", 2);  // Divide em duas partes, a primeira é Gemini, a segunda são os links

        // Process the message and add it to the model
        model.addAttribute("querry", messageContent);
        model.addAttribute("geminiContent", resultParts[0]);
        model.addAttribute("searchResults", resultParts[1]);
        model.addAttribute("currentPage", page);

        // Verifica se a próxima página tem resultados
        String nextPageResult = user.search(HtmlUtils.htmlEscape(messageContent), page + 1);
        boolean hasNext = !nextPageResult.contains("No results found.");
        model.addAttribute("hasNext", hasNext);

        return "search";
    }

    @GetMapping("/links")
    public String linkedPages(@RequestParam(name = "page") String messageContent, Model model) throws RemoteException {

        String results = user.linkedPages(messageContent);

        String[] resultParts = results.split("\n");

        StringBuilder response = new StringBuilder();
        for (String url : resultParts) {
            response.append(String.format(
                    "<tr><td><a href='%s' target='_blank'>%s</a></td></tr>\n",
                    url, url
            ));
        }
        String backLinks = response.toString();

        // Process the message and add it to the model
        model.addAttribute("querry", messageContent);
        model.addAttribute("geminiContent", "No need gemini");
        model.addAttribute("searchResults", backLinks);
        model.addAttribute("currentPage", 0);

        // Verifica se a próxima página tem resultados
        boolean hasNext = false;
        model.addAttribute("hasNext", hasNext);

        return "search";
    }

    @PostMapping("/top")
    public String addtop(@RequestBody Message message) {
        System.out.println("Top requested");
        StringBuilder stringBuilder = new StringBuilder();
        String resultados = "";
        String request = HtmlUtils.htmlEscape(message.content()).toLowerCase();
        System.out.println(request);
        String url = "https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty";
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            List ids = new ArrayList<String>(response.getBody());
            for (int i = 0; i < 30; i++) {
                String current = ids.get(i).toString();
                String itemUrl = "https://hacker-news.firebaseio.com/v0/item/" + current + ".json";
                Map<String, Object> item = restTemplate.getForObject(itemUrl, Map.class);
                if (item == null || !item.containsKey("url")) continue;
                String newstitle = (String) item.get("title");
                String newsurl = (String) item.get("url");
                if (newsurl != null && newstitle.toLowerCase().indexOf(request) != -1) {
                    Map<String, String> result = new HashMap<>();
                    stringBuilder.append(newstitle);
                    stringBuilder.append("\n");
                    stringBuilder.append(newsurl);
                    stringBuilder.append("\n\n");
                    try {
                        user.addURL(newsurl);
                    }
                    catch(Exception e){
                        System.out.println("Erro a adicionar url");
                    }
                }
            }
            resultados = stringBuilder.toString();
            System.out.println(resultados);
        }
        return "index";
    }

    @GetMapping("/statistics")
    public String showStatistics(Model model) throws RemoteException {
        ArrayList<String> topSearches = user.getTop10Searches();
        ConcurrentHashMap<Integer, Long> barrelsStats = user.getBarrelsStats();
        ConcurrentHashMap<Integer, Double> searchTimes = user.getBarrelsSearchAverage();

        // Top 10 pesquisas
        List<String> top = new ArrayList<>();
        if (topSearches.isEmpty()) {
            top.add("Ainda não foram realizadas pesquisas!");
        } else {
            int i = 1;
            for (String s : topSearches) {
                top.add(i++ + ": " + s);
            }
        }

        // Estatísticas dos barrels
        List<Map<String, Object>> barrels = new ArrayList<>();

        Set<Integer> allBarrelIds = new HashSet<>();
        allBarrelIds.addAll(barrelsStats.keySet());
        allBarrelIds.addAll(searchTimes.keySet());

        if (allBarrelIds.isEmpty()) {
            Map<String, Object> noBarrels = new HashMap<>();
            noBarrels.put("id", "Não há nenhum barrel ativo");
            noBarrels.put("indexSize", "");
            noBarrels.put("responseTime", "");
            barrels.add(noBarrels);
        } else {
            for (Integer id : allBarrelIds) {
                Map<String, Object> barrel = new HashMap<>();
                barrel.put("id", id);

                Long indexSize = barrelsStats.getOrDefault(id, 0L);
                barrel.put("indexSize", indexSize);

                double avgTime = searchTimes.getOrDefault(id, -1.0);
                if (avgTime == -1.0) {
                    barrel.put("responseTime", "Este barrel ainda não fez nenhuma pesquisa");
                } else {
                    barrel.put("responseTime", avgTime);
                }

                barrels.add(barrel);
            }
        }

        model.addAttribute("topSearches", top);
        model.addAttribute("barrels", barrels);

        return "statistics";
    }


    @PostMapping("/add")
    public String addURL(@RequestBody Message message) throws RemoteException {
        String url = message.content();

        // Dividir a URL pelo caracter ":"
        String[] url_begin = url.split(":");

        // Verificar se a URL começa com "http" ou "https"
        if (url_begin.length > 0 && (url_begin[0].equals("https") || url_begin[0].equals("http"))) {
            try {
                user.addURL(url);
                System.out.println("URL added: " + url);
            } catch (Exception e) {
                System.out.println("Error adding URL: " + e.getMessage());
            }
        } else {
            System.out.println("Invalid URL format: " + url);
        }

        return "index"; // Sucesso ou erro
    }


}
