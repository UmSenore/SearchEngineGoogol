package pt.uc.sd.beans;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

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
