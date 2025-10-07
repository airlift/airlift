package io.airlift.api.binding;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.airlift.api.ApiQuotaController;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelServices;
import io.airlift.log.Logger;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Request;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.api.ApiTrait.BETA;
import static jakarta.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static java.util.Objects.requireNonNull;

@Priority(Priorities.ENTITY_CODER)
public class JaxrsQuotaFilter
        implements ContainerRequestFilter, ContainerResponseFilter
{
    private static final Logger log = Logger.get(JaxrsQuotaFilter.class);

    private final ModelServices modelServices;

    private record Quota(ModelMethod modelMethod, Set<String> usedQuotas)
    {
        private Quota
        {
            checkArgument(usedQuotas == null, "usedQuotas must be null");
            requireNonNull(modelMethod, "modelMethod is null");

            usedQuotas = Sets.newConcurrentHashSet();
        }
    }

    @Inject
    public JaxrsQuotaFilter(ModelServices modelServices)
    {
        this.modelServices = requireNonNull(modelServices, "modelServices is null");
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
            throws IOException
    {
        checkState(activeQuota(requestContext.getRequest()).isEmpty(), "expectedQuotaKeys is not empty");

        JaxrsUtil.findApiServiceMethod(requestContext, modelServices)
                .filter(modelMethod -> !modelMethod.quotas().isEmpty())
                .ifPresent(modelMethod -> setActiveQuota(requestContext.getRequest(), Optional.of(new Quota(modelMethod, null))));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException
    {
        try {
            if (responseContext.getStatusInfo().getFamily() == SUCCESSFUL) {
                activeQuota(requestContext.getRequest())
                        .ifPresent(quota -> {
                            Set<String> missingQuotas = Sets.difference(quota.modelMethod.quotas(), quota.usedQuotas);
                            if (!missingQuotas.isEmpty()) {
                                error(quota, "This method requires quotas to be used and some/all weren't. Method: %s, Unused Quotas: %s".formatted(quota.modelMethod.method(), missingQuotas));
                            }
                        });
            }
        }
        finally {
            setActiveQuota(requestContext.getRequest(), Optional.empty());
        }
    }

    static class ApiQuotaControllerProxy
            implements ApiQuotaController
    {
        @Override
        public void recordQuotaUsage(Request request, String quotaKey)
        {
            activeQuota(request).ifPresent(quota -> {
                if (quota.modelMethod.quotas().contains(quotaKey)) {
                    quota.usedQuotas.add(quotaKey);
                }
                else {
                    error(quota, "A quota was used that is not specified by this method. Method: %s, Unspecified quota: %s".formatted(quota.modelMethod.method(), quotaKey));
                }
            });
        }
    }

    private static void error(Quota quota, String message)
    {
        if (quota.modelMethod().traits().contains(BETA)) {
            log.warn(message);
        }
        else {
            log.error(message);
            throw new IllegalStateException(message);
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<Quota> activeQuota(Request request)
    {
        return (Optional<Quota>) firstNonNull(((ContainerRequest) request).getProperty(JaxrsQuotaFilter.class.getName()), Optional.empty());
    }

    private static void setActiveQuota(Request request, Optional<Quota> quota)
    {
        ((ContainerRequest) request).setProperty(JaxrsQuotaFilter.class.getName(), quota);
    }
}
