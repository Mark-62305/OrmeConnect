package com.ormec.myapplication.models;

import java.util.List;

public class BillingRatesResponse {
    private String status;
    private String message;
    private List<BillingRateItem> rates;
    private DbFingerprint db;

    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public List<BillingRateItem> getRates() { return rates; }
    public DbFingerprint getDb() { return db; }

    public static class DbFingerprint {
        private String db_name;
        private String mysql_host;
        private int mysql_port;

        public String getDb_name() { return db_name; }
        public String getMysql_host() { return mysql_host; }
        public int getMysql_port() { return mysql_port; }
    }
}