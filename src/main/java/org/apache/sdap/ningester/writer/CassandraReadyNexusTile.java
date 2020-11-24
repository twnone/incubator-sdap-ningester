package org.apache.sdap.ningester.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.nio.ByteBuffer;

public class CassandraReadyNexusTile {

    private UUID tile_id;
    private ByteBuffer tile_blob;

    public CassandraReadyNexusTile(UUID id, ByteBuffer tileData) {
        this.tile_id = id;
        this.tile_blob = tileData;
    }

}
