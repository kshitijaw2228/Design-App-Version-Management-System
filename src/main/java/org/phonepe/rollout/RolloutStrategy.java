package org.phonepe.rollout;

import org.phonepe.domain.Device;

public interface RolloutStrategy {
    boolean isEligible(Device device);
    String name();
}

