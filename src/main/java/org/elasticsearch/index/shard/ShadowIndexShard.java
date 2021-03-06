/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.index.shard;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchIllegalStateException;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.aliases.IndexAliasesService;
import org.elasticsearch.index.cache.IndexCache;
import org.elasticsearch.index.cache.bitset.ShardBitsetFilterCache;
import org.elasticsearch.index.cache.filter.ShardFilterCache;
import org.elasticsearch.index.cache.query.ShardQueryCache;
import org.elasticsearch.index.codec.CodecService;
import org.elasticsearch.index.deletionpolicy.SnapshotDeletionPolicy;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.engine.EngineClosedException;
import org.elasticsearch.index.engine.EngineFactory;
import org.elasticsearch.index.fielddata.IndexFieldDataService;
import org.elasticsearch.index.fielddata.ShardFieldData;
import org.elasticsearch.index.get.ShardGetService;
import org.elasticsearch.index.indexing.ShardIndexingService;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.merge.policy.MergePolicyProvider;
import org.elasticsearch.index.merge.scheduler.MergeSchedulerProvider;
import org.elasticsearch.index.percolator.PercolatorQueriesRegistry;
import org.elasticsearch.index.percolator.stats.ShardPercolateService;
import org.elasticsearch.index.query.IndexQueryParserService;
import org.elasticsearch.index.search.stats.ShardSearchService;
import org.elasticsearch.index.settings.IndexSettings;
import org.elasticsearch.index.settings.IndexSettingsService;
import org.elasticsearch.index.similarity.SimilarityService;
import org.elasticsearch.index.store.Store;
import org.elasticsearch.index.suggest.stats.ShardSuggestService;
import org.elasticsearch.index.termvectors.ShardTermVectorsService;
import org.elasticsearch.index.translog.Translog;
import org.elasticsearch.index.warmer.ShardIndexWarmerService;
import org.elasticsearch.indices.IndicesLifecycle;
import org.elasticsearch.indices.IndicesWarmer;
import org.elasticsearch.threadpool.ThreadPool;

/**
 * ShadowIndexShard extends {@link IndexShard} to add file synchronization
 * from the primary when a flush happens. It also ensures that a replica being
 * promoted to a primary causes the shard to fail, kicking off a re-allocation
 * of the primary shard.
 */
public final class ShadowIndexShard extends IndexShard {

    private final Object mutex = new Object();

    @Inject
    public ShadowIndexShard(ShardId shardId, @IndexSettings Settings indexSettings, IndexSettingsService indexSettingsService,
                            IndicesLifecycle indicesLifecycle, Store store, MergeSchedulerProvider mergeScheduler,
                            Translog translog, ThreadPool threadPool, MapperService mapperService,
                            IndexQueryParserService queryParserService, IndexCache indexCache,
                            IndexAliasesService indexAliasesService, ShardIndexingService indexingService,
                            ShardGetService getService, ShardSearchService searchService,
                            ShardIndexWarmerService shardWarmerService, ShardFilterCache shardFilterCache,
                            ShardFieldData shardFieldData, PercolatorQueriesRegistry percolatorQueriesRegistry,
                            ShardPercolateService shardPercolateService, CodecService codecService,
                            ShardTermVectorsService termVectorsService, IndexFieldDataService indexFieldDataService,
                            IndexService indexService, ShardSuggestService shardSuggestService, ShardQueryCache shardQueryCache,
                            ShardBitsetFilterCache shardBitsetFilterCache, @Nullable IndicesWarmer warmer,
                            SnapshotDeletionPolicy deletionPolicy, SimilarityService similarityService,
                            MergePolicyProvider mergePolicyProvider, EngineFactory factory) {
        super(shardId, indexSettings, indexSettingsService, indicesLifecycle, store, mergeScheduler,
                translog, threadPool, mapperService, queryParserService, indexCache, indexAliasesService,
                indexingService, getService, searchService, shardWarmerService, shardFilterCache,
                shardFieldData, percolatorQueriesRegistry, shardPercolateService, codecService,
                termVectorsService, indexFieldDataService, indexService, shardSuggestService,
                shardQueryCache, shardBitsetFilterCache, warmer, deletionPolicy, similarityService,
                mergePolicyProvider, factory);
    }

    /**
     * In addition to the regular accounting done in
     * {@link IndexShard#routingEntry(org.elasticsearch.cluster.routing.ShardRouting)},
     * if this shadow replica needs to be promoted to a primary, the shard is
     * failed in order to allow a new primary to be re-allocated.
     */
    @Override
    public IndexShard routingEntry(ShardRouting newRouting) {
        if (newRouting.primary() == true) {// becoming a primary
            throw new ElasticsearchIllegalStateException("can't promote shard to primary");
        }
        return super.routingEntry(newRouting);
    }

    @Override
    protected Engine newEngine() {
        assert this.shardRouting.primary() == false;
        return engineFactory.newReadOnlyEngine(config);
    }

    public boolean allowsPrimaryPromotion() {
        return false;
    }
}
