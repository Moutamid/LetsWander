package com.moutamid.letswander;

public class MarkerData {
    private double latitude;
    private double longitude;
    private String markerId;
    private String title;
    private String description;
    private Boolean star;

    public MarkerData(String markerId, double latitude, double longitude, String title, String description, Boolean star) {
        this.markerId = markerId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.title = title;
        this.description = description;
        this.star = star;
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

    public String getMarkerId() {
        return markerId;
    }

    public void setMarkerId(String markerId) {
        this.markerId = markerId;
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
