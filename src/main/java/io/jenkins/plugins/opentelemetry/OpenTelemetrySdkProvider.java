/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.opentelemetry.autoconfigure.ConfigPropertiesUtils;
import io.jenkins.plugins.opentelemetry.opentelemetry.trace.TracerDelegate;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


@Extension
public class OpenTelemetrySdkProvider {

    private static final Logger LOGGER = Logger.getLogger(OpenTelemetrySdkProvider.class.getName());

    protected transient OpenTelemetry openTelemetry;
    protected transient OpenTelemetrySdk openTelemetrySdk;
    protected transient TracerDelegate tracer;

    protected transient SdkMeterProvider sdkMeterProvider;
    protected transient MeterProvider meterProvider;
    protected transient Meter meter;

    public OpenTelemetrySdkProvider() {
    }

    @PostConstruct
    @VisibleForTesting
    public void postConstruct() {
        initializeNoOp();
    }

    @Nonnull
    public Tracer getTracer() {
        return Preconditions.checkNotNull(tracer);
    }

    @Nonnull
    public Meter getMeter() {
        return Preconditions.checkNotNull(meter);
    }

    @VisibleForTesting
    @Nonnull
    protected SdkMeterProvider getSdkMeterProvider() {
        return Preconditions.checkNotNull(sdkMeterProvider);
    }

    @VisibleForTesting
    @Nonnull
    protected OpenTelemetrySdk getOpenTelemetrySdk() {
        return Preconditions.checkNotNull(openTelemetrySdk);
    }

    @PreDestroy
    public void preDestroy() {
        if (this.openTelemetrySdk != null) {
            this.openTelemetrySdk.getSdkTracerProvider().shutdown();
        }
        if (this.sdkMeterProvider != null) {
            this.sdkMeterProvider.shutdown();
        }
        GlobalOpenTelemetry.resetForTest();
        GlobalMeterProvider.set(MeterProvider.noop());
    }

    public void initialize(@Nonnull OpenTelemetryConfiguration configuration) {
        preDestroy(); // shutdown existing SDK

        AutoConfiguredOpenTelemetrySdkBuilder sdkBuilder = AutoConfiguredOpenTelemetrySdk.builder();
        // RESOURCE
        sdkBuilder.addResourceCustomizer((resource, configProperties) ->
            Resource.builder()
                .put(ResourceAttributes.SERVICE_VERSION, getJenkinsVersion())
                .put(JenkinsOtelSemanticAttributes.JENKINS_URL, jenkinsLocationConfiguration.getUrl())
                .putAll(resource)
                .putAll(configuration.toOpenTelemetryResource())
                .build()
        );
        // PROPERTIES
        sdkBuilder.addPropertiesSupplier(() -> configuration.toOpenTelemetryProperties());

        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk = sdkBuilder.build();
        this.openTelemetrySdk = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk();
        this.openTelemetry = this.openTelemetrySdk;
        this.tracer.setDelegate(openTelemetry.getTracer("jenkins"));

        this.meterProvider = GlobalMeterProvider.get();
        if (this.meterProvider instanceof SdkMeterProvider) {
            this.sdkMeterProvider = (SdkMeterProvider) this.meterProvider;
        } else {
            // The meterProvider created by the AutoConfiguredOpenTelemetrySdkBuilder is a NoopMeterProvider instead of a SdkMeterProvider if "otel.metrics.exporter=none"
            // See https://github.com/open-telemetry/opentelemetry-java/blob/v1.9.0/sdk-extensions/autoconfigure/src/main/java/io/opentelemetry/sdk/autoconfigure/OpenTelemetrySdkAutoConfiguration.java#L148
        }
        this.meter = this.meterProvider.get("jenkins");

        LOGGER.log(Level.INFO, () -> "OpenTelemetry initialized: " + ConfigPropertiesUtils.prettyPrintConfiguration(autoConfiguredOpenTelemetrySdk.getConfig()));
    }

    public void initializeNoOp() {
        LOGGER.log(Level.FINE, "initializeNoOp");
        preDestroy();

        this.openTelemetrySdk = null;
        this.openTelemetry = OpenTelemetry.noop();
        GlobalOpenTelemetry.resetForTest(); // hack for testing in Intellij cause by DiskUsageMonitoringInitializer
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        if (this.tracer == null) {
            this.tracer = new TracerDelegate(OpenTelemetry.noop().getTracer("noop"));
        } else {
            this.tracer.setDelegate(OpenTelemetry.noop().getTracer("noop"));
        }

        this.sdkMeterProvider = null;
        this.meterProvider = MeterProvider.noop();
        GlobalMeterProvider.set(MeterProvider.noop());
        this.meter = meterProvider.get("jenkins");
        LOGGER.log(Level.FINE, "OpenTelemetry initialized as NoOp");
    }

    private JenkinsLocationConfiguration jenkinsLocationConfiguration;

    @Inject
    public void setJenkinsLocationConfiguration(@Nonnull JenkinsLocationConfiguration jenkinsLocationConfiguration) {
        this.jenkinsLocationConfiguration = jenkinsLocationConfiguration;
    }

    /**
     * see {@code Jenkins#computeVersion(javax.servlet.ServletContext)}
     */
    @SuppressFBWarnings({"NP_LOAD_OF_KNOWN_NULL_VALUE", "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", "RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE"})
    @CheckForNull
    protected String getJenkinsVersion() {
        Properties properties = new Properties();
        try (InputStream is = Jenkins.class.getResourceAsStream("jenkins-version.properties")) {
            if (is == null) {
                return null;
            } else {
                properties.load(is);
            }
        } catch (IOException e) {
            return null;
        }
        String version = properties.getProperty("version");
        if (version == null) {
            return null;
        } else if (version.equals("${project.version}")) {
            return "development";
        } else {
            return version;
        }
    }
}
