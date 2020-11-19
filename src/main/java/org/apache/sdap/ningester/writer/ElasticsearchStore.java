package org.apache.sdap.ningester.writer;

import java.io.IOException;

import org.apache.sdap.nexusproto.NexusTile;
import org.apache.sdap.nexusproto.TileSummary;

import static org.elasticsearch.common.xcontent.XContentFactory.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.elasticsearch.common.xcontent.XContentBuilder;
//import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.DocWriteRequest;



import java.math.BigDecimal;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.lang.reflect.Field;

public class ElasticsearchStore implements MetadataStore {

    //TODO This will be refactored at some point to be dynamic per-message. Or maybe per-group.
    //private static final String INDEX_NAME = "sea_surface_temp",
    private static final String TABLE_NAME = "sea_surface_temp";
    private static final SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    static {
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private Logger log = LoggerFactory.getLogger(ElasticsearchStore.class);

    private String index = "nexustiles";
    private Integer commitWithin = 1000;

    //private ElasticsearchOperations elasticsearch;
    //public ElasticsearchStore(ElasticsearchOperations elasticsearch) {
    //    log.info("ELASTICSEARCH STORE IS CREATED");
    //    this.elasticsearch = elasticsearch;
    //}

    //private RestClientBuilder clientBuilder;

    private RestHighLevelClient elasticsearch;
    private RequestOptions requestOptions = RequestOptions.DEFAULT;


    public ElasticsearchStore(RestHighLevelClient elasticsearch) {
        this.elasticsearch = elasticsearch;
        //this.elasticsearch = new RestHighLevelClient(clientBuilder);

	try {
		if (this.elasticsearch.ping(requestOptions)) {
			log.info("ELASTICSEARCH CLIENT IS CONNECTED.");
			log.info(this.elasticsearch.info(requestOptions).getClusterName().toString());
		}
//		if (this.elasticsearch.ping()) {
//			log.info("ELASTICSEARCH CLIENT IS CONNECTED.");
//			log.info(this.elasticsearch.info().getClusterName().toString());
//		}
		else {
			log.error("ELASTICSEARCH CLIENT IS NOT CONNECTED");	
		}
	}
	catch(IOException ioe) {
		log.error("Ping failed.");
		log.error(ioe.getMessage());
	}
    }


    @Override
    public void saveMetadata(List<? extends NexusTile> nexusTiles) {
        BulkRequest bulkRequest = new BulkRequest();

//        List<XContentBuilder> elasticsearchDocs = nexusTiles.stream()
//	    .map(nexusTile -> getElasticsearchDocFromTileSummary(nexusTile.getSummary()))
//	    .collect(Collectors.toList());
//	Iterable<IndexRequest> elasticsearchDocs = nexusTiles.stream()
//	    .map(nexusTile -> getElasticsearchDocFromTileSummary(nexusTile.getSummary()))
//	    .collect(Collectors.toList());
//
//	bulkRequest.add(elasticsearchDocs);

	try {
		nexusTiles.stream()
		    .map(nexusTile -> getElasticsearchDocFromTileSummary(nexusTile.getSummary()))
		    .forEach(nexusTile -> bulkRequest.add(nexusTile));

		BulkResponse response = this.elasticsearch.bulk(bulkRequest, requestOptions);
		//BulkResponse response = this.elasticsearch.bulk(bulkRequest);
		if (response.hasFailures()) {
			log.error(response.buildFailureMessage());
		}
	}
	catch(IOException ioe) {
		log.error("Bulk operation failed");
		log.error(ioe.getMessage());
	}
    }

    @Override
    public void deleteMetadata(List<? extends NexusTile> nexusTiles) {

    }

    //public XContentBuilder getElasticsearchDocFromTileSummary(TileSummary summary) throws IOException {
    public IndexRequest getElasticsearchDocFromTileSummary(TileSummary summary) {

        TileSummary.BBox bbox = summary.getBbox();
        TileSummary.DataStats stats = summary.getStats();

        Calendar startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        startCal.setTime(new Date(stats.getMinTime() * 1000));
        Calendar endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        endCal.setTime(new Date(stats.getMaxTime() * 1000));

        String minTime = iso.format(startCal.getTime());
        String maxTime = iso.format(endCal.getTime());

        //float depth = summary.getDepth();

        String geo = determineGeo(summary);

        String granuleFileName = Paths.get(summary.getGranule()).getFileName().toString();

        try {
		XContentBuilder builder = jsonBuilder();
		IndexRequest indexRequest = new IndexRequest(this.index, TABLE_NAME);

	        builder.startObject()
	            .field("table_s", TABLE_NAME)
	            .field("geo", geo)
	            .field("id", summary.getTileId())
	            .field("solr_id_s", summary.getDatasetName() + "!" + summary.getTileId())
	            .field("dataset_id_s", summary.getDatasetUuid())
	            .field("sectionSpec_s", summary.getSectionSpec())
	            .field("dataset_s", summary.getDatasetName())
	            .field("granule_s", granuleFileName)
	            .field("tile_var_name_s", summary.getDataVarName())
	            .field("tile_min_lon", bbox.getLonMin())
	            .field("tile_max_lon", bbox.getLonMax())
	            .field("tile_min_lat", bbox.getLatMin())
	            .field("tile_max_lat", bbox.getLatMax())
	            .field("tile_min_time_dt", minTime)
	            .field("tile_max_time_dt", maxTime)
	            .field("tile_min_val_d", stats.getMin())
	            .field("tile_max_val_d", stats.getMax())
	            .field("tile_avg_val_d", stats.getMean())
	            .field("tile_count_i", Long.valueOf(stats.getCount()).intValue());
	
	        //summary.getGlobalAttributesList().forEach(attribute -> builder.field(attribute.getName(), attribute.getValuesCount() == 1 ? attribute.getValues(0) : attribute.getValuesList()));

		summary.getGlobalAttributesList().forEach(attribute -> {
			try {
				if(attribute.getValuesCount() == 1) {
					builder.field(attribute.getName(), attribute.getValues(0));
				}
				else {
					builder.field(attribute.getName(), attribute.getValuesList());
				}
			}
			catch(IOException e) {
				log.error("probleme avec XContentBuilder");
			}
		});
	
	        builder.endObject();
	        log.info("GENERATED BUILDER : " + builder);
		indexRequest.source(builder);
		log.info("GENERATED INDEXREQUEST : " + indexRequest);

		return indexRequest;
        }
	catch(IOException e) {
		log.error("probleme avec XContentBuilder");
	}

	return null;
	//return builder;
    }


    private String determineGeo(TileSummary summary) {
        //Solr cannot index a POLYGON where all corners are the same point or when there are only 2 distinct points (line).
        //Solr is configured for a specific precision so we need to round to that precision before checking equality.
        
        //Integer geoPrecision = this.geoPrecision;

        BigDecimal latMin = BigDecimal.valueOf(summary.getBbox().getLatMin());//.setScale(geoPrecision, BigDecimal.ROUND_HALF_UP);
        BigDecimal latMax = BigDecimal.valueOf(summary.getBbox().getLatMax());//.setScale(geoPrecision, BigDecimal.ROUND_HALF_UP);
        BigDecimal lonMin = BigDecimal.valueOf(summary.getBbox().getLonMin());//.setScale(geoPrecision, BigDecimal.ROUND_HALF_UP);
        BigDecimal lonMax = BigDecimal.valueOf(summary.getBbox().getLonMax());//.setScale(geoPrecision, BigDecimal.ROUND_HALF_UP);

        String geo;
        //If lat min = lat max and lon min = lon max, index the 'geo' bounding box as a POINT instead of a POLYGON
        if (latMin.equals(latMax) && lonMin.equals(lonMax)) {
            geo = "POINT(" + lonMin + " " + latMin + ")";
            log.debug("{}\t{}[{}] geo={}", summary.getTileId(), summary.getGranule(), summary.getSectionSpec(), geo);
        }
        //If lat min = lat max but lon min != lon max, then we essentially have a line.
        else if (latMin.equals(latMax)) {
            geo = "LINESTRING (" + lonMin + " " + latMin + ", " + lonMax + " " + latMin + ")";
            log.debug("{}\t{}[{}] geo={}", summary.getTileId(), summary.getGranule(), summary.getSectionSpec(), geo);
        }
        //Same if lon min = lon max but lat min != lat max
        else if (lonMin.equals(lonMax)) {
            geo = "LINESTRING (" + lonMin + " " + latMin + ", " + lonMin + " " + latMax + ")";
            log.debug("{}\t{}[{}] geo={}", summary.getTileId(), summary.getGranule(), summary.getSectionSpec(), geo);
        }
        //All other cases should use POLYGON
        else {
            geo = "POLYGON((" +
                    lonMin + " " + latMin + ", " +
                    lonMax + " " + latMin + ", " +
                    lonMax + " " + latMax + ", " +
                    lonMin + " " + latMax + ", " +
                    lonMin + " " + latMin + "))";
        }

        return geo;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public void setCommitWithin(Integer commitWithin) {
        this.commitWithin = commitWithin;
    }
}
