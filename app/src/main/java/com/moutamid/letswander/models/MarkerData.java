package com.moutamid.letswander.models;

import android.annotation.SuppressLint;

public class MarkerData {
    private double latitude;
    private double longitude;
    private String id;
    private String title;
    private String description;
    private Boolean star;

    public MarkerData(String id, double latitude, double longitude, String title, String description, Boolean star) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
        this.title = title;
        this.description = description;
        this.star = star;
    }

    public MarkerData() {
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getTitle() {
        return title;
    }

    public String getId() {
        return id;
    }

    public void setId(String markerId) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getStar() {
        return star;
    }

    public void setStar(Boolean star) {
        this.star = star;
    }
}
