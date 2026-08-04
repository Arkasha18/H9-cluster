package net.adminrunet.h9cluster.trip;

/** Storage boundary used by the trip coordinator. */
public interface TripSessionPersistence {
    TripSession load(long nowMs);

    void saveAsync(TripSession session);

    boolean saveSync(TripSession session);

    void clearAsync();

    boolean clearSync();
}
