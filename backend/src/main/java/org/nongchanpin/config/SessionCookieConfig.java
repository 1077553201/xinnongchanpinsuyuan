package org.nongchanpin.config;

import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SessionCookieConfig {
    @Bean public WebServerFactoryCustomizer<TomcatServletWebServerFactory> sessionCookiePolicy() {
        return factory -> factory.addContextCustomizers(context -> {
            Rfc6265CookieProcessor cookies = new Rfc6265CookieProcessor();
            cookies.setSameSiteCookies("strict");
            context.setCookieProcessor(cookies);
        });
    }
}
