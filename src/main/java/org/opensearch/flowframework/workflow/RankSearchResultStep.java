package org.opensearch.flowframework.workflow;

import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class RankSearchResultStep implements WorkflowStep {

    public RankSearchResultStep() {}

    public static final String NAME = "rank_search_result";

    /**
     * Triggers the actual processing of the building block.
     *
     * @param currentNodeId      The id of the node executing this step
     * @param currentNodeInputs  Input params and content for this node, from workflow parsing
     * @param outputs            WorkflowData content of previous steps.
     * @param previousNodeInputs Input params for this node that come from previous steps
     * @return A CompletableFuture of the building block. This block should return immediately, but not be completed until the step executes, containing either the step's output data or {@link WorkflowData#EMPTY} which may be passed to follow-on steps.
     */
    @Override
    public PlainActionFuture<WorkflowData> execute(String currentNodeId,
                                                   WorkflowData currentNodeInputs,
                                                   Map<String, WorkflowData> outputs,
                                                   Map<String, String> previousNodeInputs) {
        PlainActionFuture<WorkflowData> future = PlainActionFuture.newFuture();
        future.onResponse(WorkflowData.EMPTY);
        return future;
    }

    public SearchHits rankAsec(SearchHits hits) {
        List<SearchHit> originalHits = Arrays.asList(hits.getHits());
        List<SearchHit> sortedHits = originalHits.stream()
                .sorted((hit1, hit2) -> {
                    // Extract the "aa" field values as Double from the source maps of hit1 and hit2
                    Double aa1 = toDouble(hit1.getSourceAsMap().get("sepal_length_in_cm"));
                    Double aa2 = toDouble(hit2.getSourceAsMap().get("sepal_length_in_cm"));

                    // Use Double.compare to compare the "aa" field values, handling nulls
                    return Double.compare(aa1 != null ? aa1 : Double.MIN_VALUE, aa2 != null ? aa2 : Double.MIN_VALUE);
                })
                .collect(Collectors.toList());
        SearchHits sortedSearchHits = new SearchHits(sortedHits.toArray(new SearchHit[0]), hits.getTotalHits(),hits.getMaxScore());
        return sortedSearchHits;
    }

    // Utility method to safely convert an object to Double
    private Double toDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return null;
    }

    /**
     * Gets the name of the workflow step.
     *
     * @return the name of this workflow step.
     */
    @Override
    public String getName() {
        return NAME;
    }
}
