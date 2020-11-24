/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sdap.ningester.configuration;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.s3.AmazonS3Client;
import org.apache.sdap.nexusproto.NexusTile;
import org.apache.sdap.ningester.configuration.properties.DatasourceProperties;
import org.apache.sdap.ningester.writer.*;
import org.apache.solr.client.solrj.SolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration;
import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.data.elasticsearch.client.RestClients;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.solr.core.SolrOperations;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;

import java.time.Duration;

@Configuration
public class DatasourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DatasourceConfig.class);

    @Bean
    @Profile("default")
    public MetadataStore metadataStore() {
        log.warn("Default metadataStore configured. All tiles generated will not be saved to persistent storage! Enable a profile to configure metadata storage (e.g. activating the 'solr' profile will save metadata to Solr)");
        return new MetadataStore() {
            @Override
            public void saveMetadata(List<? extends NexusTile> nexusTiles) {
            }

            @Override
            public void deleteMetadata(List<? extends NexusTile> nexusTiles) {
            }
        };
    }

    @Bean
    @Profile("default")
    public DataStore dataStore() {
        log.warn("Default dataStore configured. All tiles generated will not be saved to persistent storage! Enable a profile to configure data storage (e.g. activating the 'cassandra' profile will save metadata to Solr)");
        return nexusTiles -> {
        };
    }

    @Bean
    @Profile("embedded")
    public DataSource dataSource() {
        EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder();
        return builder
                .setType(EmbeddedDatabaseType.H2)
                .build();
    }


    @Configuration
    @Profile("cassandra")
    @Import({CassandraDataAutoConfiguration.class, CassandraAutoConfiguration.class})
    static class CassandraConfiguration {

        @Bean
        public DataStore dataStore(CqlSession cqlSession) {
            return new CassandraStore(new CassandraTemplate(cqlSession));
        }
    }

    @Configuration
    @Profile("dynamo")
    static class DynamoConfiguration {

        @Autowired
        private DatasourceProperties datasourceProperties;

        @Bean
        public AmazonDynamoDB dynamoClient() {
            AmazonDynamoDB dynamoClient = new AmazonDynamoDBClient();
            dynamoClient.setRegion(Region.getRegion(Regions.fromName(datasourceProperties.getDynamoStore().getRegion())));
            return dynamoClient;
        }

        @Bean
        public DataStore dataStore(AmazonDynamoDB dynamoClient) {
            return new DynamoStore(dynamoClient,
                    datasourceProperties.getDynamoStore().getTableName(),
                    datasourceProperties.getDynamoStore().getPrimaryKey());
        }
    }

    @Configuration
    @Profile("s3")
    static class S3Configuration {
        @Autowired
        private DatasourceProperties datasourceProperties;

        @Bean
        public AmazonS3Client s3client() {
            AmazonS3Client s3Client = new AmazonS3Client();
            s3Client.setRegion(Region.getRegion(Regions.fromName(datasourceProperties.getS3Store().getRegion())));
            return s3Client;
        }

        @Bean
        public DataStore dataStore(AmazonS3Client s3Client) {
            return new S3Store(s3Client, datasourceProperties.getS3Store().getBucketName());
        }
    }

    @Configuration
    @Profile("solr")
    @Import({SolrAutoConfiguration.class})
    static class SolrConfiguration {

        @Autowired
        private DatasourceProperties datasourceProperties;

        @Bean
        public SolrOperations solrTemplate(SolrClient solrClient) {
            return new SolrTemplate(solrClient);
        }


        @Bean
        public MetadataStore metadataStore(SolrOperations solrTemplate) {
            SolrStore solrStore = new SolrStore(solrTemplate);
            solrStore.setCollection(datasourceProperties.getSolrStore().getCollection());
            solrStore.setCommitWithin(Duration.ofMillis(datasourceProperties.getSolrStore().getCommitWithin()));
            solrStore.setGeoPrecision(datasourceProperties.getSolrStore().getGeoPrecision());

            return solrStore;
        }
    }
    @Configuration
    @Profile("elasticsearch")
    static class ElasticsearchConfiguration {

            @Autowired
            private DatasourceProperties datasourceProperties;

            @Autowired
            private RestClientBuilder restClientBuilder;

            @Bean
            public MetadataStore metadataStore(RestClientBuilder restClientBuilder) {

                RestHighLevelClient highLevelClient = new RestHighLevelClient(restClientBuilder);
                ElasticsearchStore elasticsearchStore = new ElasticsearchStore(highLevelClient);
                elasticsearchStore.setIndex(datasourceProperties.getElasticsearchStore().getIndex());

                return elasticsearchStore;
            }
    }
}
