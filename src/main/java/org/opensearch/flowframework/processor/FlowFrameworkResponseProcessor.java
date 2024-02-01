package org.opensearch.flowframework.processor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.flowframework.model.Template;
import org.opensearch.flowframework.transport.GetWorkflowAction;
import org.opensearch.flowframework.transport.WorkflowRequest;
import org.opensearch.search.pipeline.AbstractProcessor;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.search.pipeline.SearchResponseProcessor;

import java.util.Map;

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
        System.out.println("id: " + workflowId);

        WorkflowRequest workflowRequest = new WorkflowRequest(workflowId, null);
        client.execute(GetWorkflowAction.INSTANCE, workflowRequest, ActionListener.wrap(getWorkflowResponse -> {
            Template template = getWorkflowResponse.getTemplate();
            System.out.println("here: " + template.toJson());
        }, exception -> {
            logger.error("Failed to send back provision workflow exception", exception);
        }));

        return response;
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
