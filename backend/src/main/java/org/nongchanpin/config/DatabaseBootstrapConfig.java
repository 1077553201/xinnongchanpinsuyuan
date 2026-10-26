package org.nongchanpin.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DatabaseBootstrapConfig {
    @Bean(initMethod = "initialize")
    public DatabaseBootstrap databaseBootstrap(DataSource dataSource, Environment environment) {
        return new DatabaseBootstrap(dataSource, environment);
    }

    // Mapper creation and MQTT subscription must follow schema/account initialization.
    @Bean
    public static BeanFactoryPostProcessor databaseBootstrapOrdering() {
        return factory -> {
            for (String name : List.of("sqlSessionFactory", "mqttTelemetryAdapter")) {
                if (!factory.containsBeanDefinition(name)) continue;
                var definition = factory.getBeanDefinition(name);
                List<String> dependencies = new ArrayList<>();
                if (definition.getDependsOn() != null) dependencies.addAll(Arrays.asList(definition.getDependsOn()));
                dependencies.add("databaseBootstrap");
                definition.setDependsOn(dependencies.toArray(new String[0]));
            }
        };
    }
}
