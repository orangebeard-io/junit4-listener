package io.orangebeard.listener.entity;

import io.orangebeard.client.entity.Status;

import java.util.UUID;

import static io.orangebeard.client.entity.Status.PASSED;

public class SuiteInfo {
    private final UUID suiteUUID;
    private Status status;

    public SuiteInfo(UUID suiteUUID) {
        this.suiteUUID = suiteUUID;
        this.status = PASSED;
    }

    public UUID getSuiteUUID() {
        return suiteUUID;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status){
        this.status = status;
    }
}
