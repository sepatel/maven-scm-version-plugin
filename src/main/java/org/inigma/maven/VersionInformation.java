package org.inigma.maven;

import java.text.SimpleDateFormat;
import java.util.Date;

public class VersionInformation {
    private String branchName;
    private String version;
    private boolean snapshot;
    private Date timestamp = new Date();

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public void setSnapshot(boolean snapshot) {
        this.snapshot = snapshot;
    }

    public boolean isSnapshot() {
        return snapshot;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBranchDateStyle() {
        if (snapshot) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd.hh.mm.ss");
            return branchName + "." + sdf.format(timestamp);
        }
        return version;
    }

    public String getDateStyle() {
        if (snapshot) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd.hh.mm.ss");
            return sdf.format(timestamp);
        }
        return version;
    }

    public String getBranchStyle() {
        if (snapshot) {
            if (branchName == null) {
                return version + "-SNAPSHOT";
            }
            return version + "." + branchName + "-SNAPSHOT";
        }
        return version;
    }
}
