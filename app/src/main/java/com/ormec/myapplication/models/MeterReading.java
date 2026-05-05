package com.ormec.myapplication.models;

// This class matches each row from get_meter_history.php: { "kwh": "...", "reading_date": "..." }
public class MeterReading {

    // PHP json_encode on DECIMAL usually sends kwh as a string
    private String kwh;
    private String reading_date;


    // Safely convert kwh to float for our calculations
    public float getKwh() {
        try {
            return Float.parseFloat(kwh);
        } catch (Exception e) {
            return 0f;
        }
    }

    public String getReading_date() {
        return reading_date;
    }
}
