package org.opensearch.flowframework.processor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.flowframework.model.Template;
import org.opensearch.flowframework.model.Workflow;
import org.opensearch.flowframework.model.WorkflowNode;
import org.opensearch.flowframework.transport.GetWorkflowAction;
import org.opensearch.flowframework.transport.WorkflowRequest;
import org.opensearch.flowframework.workflow.RankSearchResultStep;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.pipeline.AbstractProcessor;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.search.pipeline.SearchResponseProcessor;
import org.opensearch.search.profile.SearchProfileShardResults;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.opensearch.flowframework.common.CommonValue.GLOBAL_CONTEXT_INDEX;
import static org.opensearch.flowframework.common.CommonValue.PROVISION_WORKFLOW;
import static org.opensearch.flowframework.common.CommonValue.SEARCH_WORKFLOW;
import static org.opensearch.ingest.ConfigurationUtils.readStringProperty;

public class FlowFrameworkResponseProcessor extends AbstractProcessor implements SearchResponseProcessor {

    private final Logger logger = LogManager.getLogger(FlowFrameworkResponseProcessor.class);


    public static final String TYPE = "flow_framework_response_processor";

    private final String workflowId;

    private final Client client;

    protected FlowFrameworkResponseProcessor(String tag,
                                             String description,
                                             boolean ignoreFailure,
                                             String workflowId,
                                             Client client) {
        super(tag, description, ignoreFailure);
        this.workflowId = workflowId;
        this.client = client;
    }

    /**
     * Transform a {@link SearchResponse}, possibly based on the executed {@link SearchRequest}.
     * <p>
     * Implement this method if the processor makes no asynchronous calls.
     *
     * @param request  the executed {@link SearchRequest}
     * @param response the current {@link SearchResponse}, possibly modified by earlier processors
     * @return a modified {@link SearchResponse} (or the input {@link SearchResponse} if no changes)
     * @throws Exception if an error occurs during processing
     */
    @Override
    public SearchResponse processResponse(SearchRequest request, SearchResponse response) throws Exception {
        SearchHits hits = response.getHits();
        if (hits.getHits().length == 0) {
            logger.info("TotalHits = 0. Returning search response without applying flow framework processor");
            return response;
        }
        GetResponse getResponse = client.get(new GetRequest(GLOBAL_CONTEXT_INDEX, workflowId)).get();
        Template template = Template.parse(getResponse.getSourceAsString());
        Workflow searchWorkflow  = template.workflows().get(SEARCH_WORKFLOW);
        WorkflowNode workflowNode = searchWorkflow.nodes().get(0);
        logger.info("Executing search workflow step: " + workflowNode.type());
        RankSearchResultStep rankSearchResultStep = new RankSearchResultStep();
        long startTime = System.nanoTime();
        SearchHits rankedSearchHits = rankSearchResultStep.rankAsec(hits);
        long rankedTimeTookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);


        final SearchResponseSections transformedSearchResponseSections = new InternalSearchResponse(rankedSearchHits,
                (InternalAggregations) response.getAggregations(), response.getSuggest(),
                new SearchProfileShardResults(response.getProfileResults()), response.isTimedOut(),
                response.isTerminatedEarly(), response.getNumReducePhases());

        final SearchResponse transformedResponse = new SearchResponse(transformedSearchResponseSections, response.getScrollId(),
                response.getTotalShards(), response.getSuccessfulShards(),
                response.getSkippedShards(), response.getTook().getMillis() + rankedTimeTookMs, response.getShardFailures(),
                response.getClusters());

        return transformedResponse;
    }

    /**
     * Gets the type of processor
     */
    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory implements Processor.Factory<SearchResponseProcessor> {
        private static final String WORKFLOW_ID = "workflow_id";

        private final Client client;

        public Factory(Client client) {
            this.client = client;
        }

        /**
         * Creates a processor based on the specified map of maps config.
         *
         * @param processorFactories Other processors which may be created inside this processor
         * @param tag                The tag for the processor
         * @param description        A short description of what this processor does
         * @param ignoreFailure
         * @param config             The configuration for the processor
         *                           <b>Note:</b> Implementations are responsible for removing the used configuration
         *                           keys, so that after creation the config map should be empty.
         * @param pipelineContext    Contextual information about the enclosing pipeline.
         */
        @Override
        public SearchResponseProcessor create(Map<String, Processor.Factory<SearchResponseProcessor>> processorFactories,
                                              String tag,
                                              String description,
                                              boolean ignoreFailure,
                                              Map<String, Object> config,
                                              PipelineContext pipelineContext) throws Exception {
            String workflowId = readStringProperty(TYPE, tag, config, WORKFLOW_ID);

            return new FlowFrameworkResponseProcessor(tag, description,ignoreFailure, workflowId, client);
        }
    }
}
