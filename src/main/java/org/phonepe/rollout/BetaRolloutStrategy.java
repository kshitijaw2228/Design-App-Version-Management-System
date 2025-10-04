package org.phonepe.rollout;

import org.phonepe.domain.Device;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BetaRolloutStrategy implements RolloutStrategy {
    private final Set<String> whitelistedDeviceIds;

    public BetaRolloutStrategy(Set<String> devices) {
        this.whitelistedDeviceIds = new HashSet<>(devices == null ? Collections.emptySet() : devices);
    }

    @Override
    public boolean isEligible(Device device) {
        return whitelistedDeviceIds.contains(device.getDeviceId());
    }

    @Override
    public String name() { return "BETA"; }

    @Override
    public String toString() {
        return "BetaRolloutStrategy{" + whitelistedDeviceIds + '}';
    }
}
