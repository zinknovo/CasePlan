package com.caseplan.adapter.in.intake.adapter;

import com.caseplan.adapter.in.intake.model.NormCaseOrder;

/**
 * Abstract adapter for parsing and transforming external intake data into NormCaseOrder.
 * Subclasses implement parse/transform/validate for a specific source format.
 */
public abstract class BaseIntakeAdapter {

    /**
     * Parses raw payload (JSON or XML string) into a source-specific structure (e.g. Map or DOM).
     */
    protected abstract Object parse(String rawData);

    /**
     * Transforms parsed data into the normalized format.
     */
    protected abstract NormCaseOrder transform(Object parsed);

    /**
     * Validates the converted NormCaseOrder. Throws if invalid.
     */
    protected abstract void validate(NormCaseOrder order);

    /**
     * Returns the source identifier this adapter handles (e.g. "sourceA", "sourceB").
     * Used by AdapterFactory to select adapter by request parameter.
     */
    public abstract String getSourceName();

    /**
     * Template method: parse → transform → set rawData → validate → return.
     */
    public final NormCaseOrder process(String rawData) {
        Object parsed = parse(rawData);
        NormCaseOrder order = transform(parsed);
        order.setRawData(rawData);
        validate(order);
        return order;
    }
}
