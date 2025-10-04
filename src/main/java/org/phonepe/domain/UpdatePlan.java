package org.phonepe.domain;

import org.phonepe.enums.UpdateType;

public record UpdatePlan(
        UpdateType type,
        AppVersion current,
        AppVersion target,
        String apkUrl,   // for INSTALL
        String diffUrl   // for UPDATE
) {
    @Override
    public String toString() {
        return "UpdatePlan{" +
                "type=" + type +
                ", current=" + (current == null ? "-" : current.getVersion()) +
                ", target=" + target.getVersion() +
                ", apkUrl=" + (apkUrl == null ? "-" : apkUrl) +
                ", diffUrl=" + (diffUrl == null ? "-" : diffUrl) +
                '}';
    }
}

