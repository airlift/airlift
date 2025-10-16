package io.airlift.bootstrap;

import com.google.inject.Provider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class BarProvider implements Provider<BarInstance> {
    @PostConstruct
    public void postDependentInstance() {
        TestLifeCycleManager.note("postBarProvider");
    }

    @PreDestroy
    public void preDependentInstance() {
        TestLifeCycleManager.note("preBarProvider");
    }

    @Override
    public BarInstance get() {
        return new BarInstance();
    }
}
