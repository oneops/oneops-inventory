package com.oneops.inv;

import com.oneops.api.resource.model.CiResource;

public class PlatformCompute {

    private CiResource platform;
    private String computeType;

    public PlatformCompute(CiResource platform, String computeType) {
        this.platform = platform;
        this.computeType = computeType;
    }

    public CiResource getPlatform() {
        return platform;
    }

    public void setPlatform(CiResource platform) {
        this.platform = platform;
    }

    public String getComputeType() {
        return computeType;
    }

    public void setComputeType(String computeType) {
        this.computeType = computeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PlatformCompute that = (PlatformCompute) o;

        if (!platform.equals(that.platform)) return false;
        return computeType.equals(that.computeType);
    }

    @Override
    public int hashCode() {
        int result = platform.hashCode();
        result = 31 * result + computeType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "PlatformCompute{" +
                "platform=" + platform +
                ", computeType='" + computeType + '\'' +
                '}';
    }
}
