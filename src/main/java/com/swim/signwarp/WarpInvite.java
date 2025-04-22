package com.swim.signwarp;

public class WarpInvite {
    private final String warpName;
    private final String invitedUuid;
    private final String invitedName;
    private final String invitedAt;

    public WarpInvite(String warpName, String invitedUuid, String invitedName, String invitedAt) {
        this.warpName = warpName;
        this.invitedUuid = invitedUuid;
        this.invitedName = invitedName;
        this.invitedAt = invitedAt;
    }

    public String getWarpName() {
        return warpName;
    }

    public String getInvitedUuid() {
        return invitedUuid;
    }

    public String getInvitedName() {
        return invitedName;
    }

    public String getInvitedAt() {
        return invitedAt;
    }
}