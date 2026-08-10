package net.adminrunet.h9cluster;

/** Controls visibility of the factory cluster without exposing car APIs to the demo build. */
interface FactoryClusterController {
    void setEnabled(boolean enabled);

    void destroy();
}
