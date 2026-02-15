package com.caseplan.adapter.in.intake;

import com.caseplan.adapter.in.intake.adapter.BaseIntakeAdapter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Factory that returns the adapter for a given source name.
 * All BaseIntakeAdapter implementations are injected via Spring; new sources only need a new Adapter + @Component.
 */
@Component
public class AdapterFactory {

    private final List<BaseIntakeAdapter> adapters;

    public AdapterFactory(List<BaseIntakeAdapter> adapters) {
        this.adapters = adapters;
    }

    /**
     * Returns the adapter that handles the given source, or empty if none.
     */
    public Optional<BaseIntakeAdapter> getAdapter(String source) {
        if (source == null || source.isEmpty()) {
            return Optional.empty();
        }
        String normalized = source.trim();
        for (int i = 0; i < adapters.size(); i++) {
            BaseIntakeAdapter a = adapters.get(i);
            if (normalized.equalsIgnoreCase(a.getSourceName())) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }
}
