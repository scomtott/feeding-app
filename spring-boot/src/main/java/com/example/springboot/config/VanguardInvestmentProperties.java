package com.example.springboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "investments.vanguard")
public class VanguardInvestmentProperties {

    private boolean enabled = true;
    private String endpoint = "https://www.vanguardinvestor.co.uk/gpx/graphql";
    private String dailyCron = "0 0 18 * * *";
    private int lookbackDays = 7;
    private String consumerId;
    private String xsrfToken;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getDailyCron() {
        return dailyCron;
    }

    public void setDailyCron(String dailyCron) {
        this.dailyCron = dailyCron;
    }

    public int getLookbackDays() {
        return lookbackDays;
    }

    public void setLookbackDays(int lookbackDays) {
        this.lookbackDays = lookbackDays;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public String getXsrfToken() {
        return xsrfToken;
    }

    public void setXsrfToken(String xsrfToken) {
        this.xsrfToken = xsrfToken;
    }
}
