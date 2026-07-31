package com.example.springboot.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity(name = "VanguardSupportedFundEntry")
@Table(
    indexes = {
        @Index(name = "idx_vanguard_supported_fund_code", columnList = "code"),
        @Index(name = "idx_vanguard_supported_fund_enabled", columnList = "enabled")
    }
)
public class VanguardSupportedFundEntry {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String indexCode;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private String startDateVariable = "startDate";

    @Column(nullable = false)
    private String endDateVariable = "endDate";

    @Column(nullable = false)
    private String operationName;

    @Lob
    @Column(nullable = false)
    private String query;

    @Lob
    @Column(nullable = false)
    private String variablesJson = "{}";

    @Column(nullable = false)
    private String navItemsPath = "data.funds[0].pricingDetails.navPrices.items";

    @Column(nullable = false)
    private String marketGroupsPath = "data.funds[0].pricingDetails.marketPrices.items";

    @Column(nullable = false)
    private String marketItemsField = "items";

    public VanguardSupportedFundEntry() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getIndexCode() {
        return indexCode;
    }

    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStartDateVariable() {
        return startDateVariable;
    }

    public void setStartDateVariable(String startDateVariable) {
        this.startDateVariable = startDateVariable;
    }

    public String getEndDateVariable() {
        return endDateVariable;
    }

    public void setEndDateVariable(String endDateVariable) {
        this.endDateVariable = endDateVariable;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getVariablesJson() {
        return variablesJson;
    }

    public void setVariablesJson(String variablesJson) {
        this.variablesJson = variablesJson;
    }

    public String getNavItemsPath() {
        return navItemsPath;
    }

    public void setNavItemsPath(String navItemsPath) {
        this.navItemsPath = navItemsPath;
    }

    public String getMarketGroupsPath() {
        return marketGroupsPath;
    }

    public void setMarketGroupsPath(String marketGroupsPath) {
        this.marketGroupsPath = marketGroupsPath;
    }

    public String getMarketItemsField() {
        return marketItemsField;
    }

    public void setMarketItemsField(String marketItemsField) {
        this.marketItemsField = marketItemsField;
    }
}
