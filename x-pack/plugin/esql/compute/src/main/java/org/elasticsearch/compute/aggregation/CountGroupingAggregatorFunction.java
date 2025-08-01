/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.aggregation;

import org.elasticsearch.compute.data.Block;
import org.elasticsearch.compute.data.BooleanBlock;
import org.elasticsearch.compute.data.BooleanVector;
import org.elasticsearch.compute.data.ElementType;
import org.elasticsearch.compute.data.IntArrayBlock;
import org.elasticsearch.compute.data.IntBigArrayBlock;
import org.elasticsearch.compute.data.IntVector;
import org.elasticsearch.compute.data.LongBlock;
import org.elasticsearch.compute.data.LongVector;
import org.elasticsearch.compute.data.Page;
import org.elasticsearch.compute.data.Vector;
import org.elasticsearch.compute.operator.DriverContext;

import java.util.List;

public class CountGroupingAggregatorFunction implements GroupingAggregatorFunction {

    private static final List<IntermediateStateDesc> INTERMEDIATE_STATE_DESC = List.of(
        new IntermediateStateDesc("count", ElementType.LONG),
        new IntermediateStateDesc("seen", ElementType.BOOLEAN)
    );

    private final LongArrayState state;
    private final List<Integer> channels;
    private final DriverContext driverContext;
    private final boolean countAll;

    public static CountGroupingAggregatorFunction create(DriverContext driverContext, List<Integer> inputChannels) {
        return new CountGroupingAggregatorFunction(inputChannels, new LongArrayState(driverContext.bigArrays(), 0), driverContext);
    }

    public static List<IntermediateStateDesc> intermediateStateDesc() {
        return INTERMEDIATE_STATE_DESC;
    }

    private CountGroupingAggregatorFunction(List<Integer> channels, LongArrayState state, DriverContext driverContext) {
        this.channels = channels;
        this.state = state;
        this.driverContext = driverContext;
        this.countAll = channels.isEmpty();
    }

    private int blockIndex() {
        return countAll ? 0 : channels.get(0);
    }

    @Override
    public int intermediateBlockCount() {
        return intermediateStateDesc().size();
    }

    @Override
    public AddInput prepareProcessRawInputPage(SeenGroupIds seenGroupIds, Page page) {
        Block valuesBlock = page.getBlock(blockIndex());
        if (countAll == false) {
            Vector valuesVector = valuesBlock.asVector();
            if (valuesVector == null) {
                if (valuesBlock.mayHaveNulls()) {
                    state.enableGroupIdTracking(seenGroupIds);
                }
                return new AddInput() {
                    @Override
                    public void add(int positionOffset, IntArrayBlock groupIds) {
                        addRawInput(positionOffset, groupIds, valuesBlock);
                    }

                    @Override
                    public void add(int positionOffset, IntBigArrayBlock groupIds) {
                        addRawInput(positionOffset, groupIds, valuesBlock);
                    }

                    @Override
                    public void add(int positionOffset, IntVector groupIds) {
                        addRawInput(positionOffset, groupIds, valuesBlock);
                    }

                    @Override
                    public void close() {}
                };
            }
        }
        return new AddInput() {
            @Override
            public void add(int positionOffset, IntArrayBlock groupIds) {
                addRawInput(groupIds);
            }

            @Override
            public void add(int positionOffset, IntBigArrayBlock groupIds) {
                addRawInput(groupIds);
            }

            @Override
            public void add(int positionOffset, IntVector groupIds) {
                addRawInput(groupIds);
            }

            @Override
            public void close() {}
        };
    }

    private void addRawInput(int positionOffset, IntVector groups, Block values) {
        int position = positionOffset;
        for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++, position++) {
            if (values.isNull(position)) {
                continue;
            }
            int groupId = groups.getInt(groupPosition);
            state.increment(groupId, values.getValueCount(position));
        }
    }

    private void addRawInput(int positionOffset, IntArrayBlock groups, Block values) {
        int position = positionOffset;
        for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++, position++) {
            if (groups.isNull(groupPosition) || values.isNull(position)) {
                continue;
            }
            int groupStart = groups.getFirstValueIndex(groupPosition);
            int groupEnd = groupStart + groups.getValueCount(groupPosition);
            for (int g = groupStart; g < groupEnd; g++) {
                int groupId = groups.getInt(g);
                state.increment(groupId, values.getValueCount(position));
            }
        }
    }

    private void addRawInput(int positionOffset, IntBigArrayBlock groups, Block values) {
        int position = positionOffset;
        for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++, position++) {
            if (groups.isNull(groupPosition) || values.isNull(position)) {
                continue;
            }
            int groupStart = groups.getFirstValueIndex(groupPosition);
            int groupEnd = groupStart + groups.getValueCount(groupPosition);
            for (int g = groupStart; g < groupEnd; g++) {
                int groupId = groups.getInt(g);
                state.increment(groupId, values.getValueCount(position));
            }
        }
    }

    /**
     * This method is called for count all.
     */
    private void addRawInput(IntVector groups) {
        for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++) {
            int groupId = groups.getInt(groupPosition);
            state.increment(groupId, 1);
        }
    }

    /**
     * This method is called for count all.
     */
    private void addRawInput(IntArrayBlock groups) {
        for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++) {
            if (groups.isNull(groupPosition)) {
                continue;
            }
            int groupStart = groups.getFirstValueIndex(groupPosition);
            int groupEnd = groupStart + groups.getValueCount(groupPosition);
            for (int g = groupStart; g < groupEnd; g++) {
                int groupId = groups.getInt(g);
                state.increment(groupId, 1);
            }
        }
    }

    /**
     * This method is called for count all.
     */
    private void addRawInput(IntBigArrayBlock groups) {
        for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++) {
            if (groups.isNull(groupPosition)) {
                continue;
            }
            int groupStart = groups.getFirstValueIndex(groupPosition);
            int groupEnd = groupStart + groups.getValueCount(groupPosition);
            for (int g = groupStart; g < groupEnd; g++) {
                int groupId = groups.getInt(g);
                state.increment(groupId, 1);
            }
        }
    }

    @Override
    public void selectedMayContainUnseenGroups(SeenGroupIds seenGroupIds) {
        state.enableGroupIdTracking(seenGroupIds);
    }

    @Override
    public void addIntermediateInput(int positionOffset, IntVector groups, Page page) {
        assert channels.size() == intermediateBlockCount();
        assert page.getBlockCount() >= blockIndex() + intermediateStateDesc().size();
        state.enableGroupIdTracking(new SeenGroupIds.Empty());
        LongVector count = page.<LongBlock>getBlock(channels.get(0)).asVector();
        BooleanVector seen = page.<BooleanBlock>getBlock(channels.get(1)).asVector();
        assert count.getPositionCount() == seen.getPositionCount();
        for (int groupPosition = 0; groupPosition < groups.getPositionCount(); groupPosition++) {
            state.increment(groups.getInt(groupPosition), count.getLong(groupPosition + positionOffset));
        }
    }

    @Override
    public void evaluateIntermediate(Block[] blocks, int offset, IntVector selected) {
        state.toIntermediate(blocks, offset, selected, driverContext);
    }

    @Override
    public void evaluateFinal(Block[] blocks, int offset, IntVector selected, GroupingAggregatorEvaluationContext evaluationContext) {
        try (LongVector.Builder builder = evaluationContext.blockFactory().newLongVectorFixedBuilder(selected.getPositionCount())) {
            for (int i = 0; i < selected.getPositionCount(); i++) {
                int si = selected.getInt(i);
                builder.appendLong(state.hasValue(si) ? state.get(si) : 0);
            }
            blocks[offset] = builder.build().asBlock();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("[");
        sb.append("channels=").append(channels);
        sb.append("]");
        return sb.toString();
    }

    @Override
    public void close() {
        state.close();
    }
}
