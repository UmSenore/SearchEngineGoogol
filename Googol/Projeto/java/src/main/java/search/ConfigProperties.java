package search;

import java.io.*;
import java.util.*;

public class ConfigProperties {
    private Properties properties;

    public ConfigProperties(String path) throws IOException {
        properties = new Properties();
        try (InputStream input = new FileInputStream(path)) {
            properties.load(input);
        }
    }
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public int getIntProperty(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }
}
