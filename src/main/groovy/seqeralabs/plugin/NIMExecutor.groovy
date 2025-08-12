/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package seqeralabs.plugin

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.executor.Executor
import nextflow.processor.TaskHandler
import nextflow.processor.TaskMonitor
import nextflow.processor.TaskPollingMonitor
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus
import nextflow.util.ServiceName
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import nextflow.util.Duration
import java.time.Duration as JavaDuration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import java.security.cert.X509Certificate

/**
 * NVIDIA NIM (NVIDIA Inference Microservices) executor for Nextflow
 *
 * Supports execution of RFDiffusion tasks via NVIDIA's API service
 */
@CompileStatic
@ServiceName('nim')
@Slf4j
class NIMExecutor extends Executor {

    Map<String, String> nimEndpoints
    Map<String, String> nimApiKeys
    HttpClient httpClient

    @Override
    void register() {
        super.register()
        
        // Configure default endpoints for NIM services using NVIDIA Health API
        this.nimEndpoints = [
            'rfdiffusion': 'https://health.api.nvidia.com/v1/biology/ipd/rfdiffusion/generate',
            'alphafold2': 'https://health.api.nvidia.com/v1/biology/deepmind/alphafold2',
            'openfold': 'https://health.api.nvidia.com/v1/biology/openfold'
        ]
        
        // Initialize API keys map
        this.nimApiKeys = [:]
        
        // Configure endpoints and API keys from configuration
        def nimConfig = session.config?.nim as Map
        if (nimConfig) {
            // Global API key configuration
            if (nimConfig.apiKey) {
                log.info "Using global API key from configuration"
                // Apply global API key to all services
                this.nimEndpoints.keySet().each { service ->
                    this.nimApiKeys[service as String] = nimConfig.apiKey as String
                }
            }
            
            // Service-specific configurations
            nimConfig.each { service, config ->
                if (config instanceof Map) {
                    // Custom endpoint
                    if (config.endpoint) {
                        this.nimEndpoints[service as String] = config.endpoint as String
                        log.info "Using custom endpoint for ${service}: ${config.endpoint}"
                    }
                    
                    // Service-specific API key (overrides global)
                    if (config.apiKey) {
                        this.nimApiKeys[service as String] = config.apiKey as String
                        log.info "Using service-specific API key for ${service}"
                    }
                }
            }
        }
        
        // Configure HTTP client for API access
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(JavaDuration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
                
        log.info "NIM executor registered with endpoints: ${nimEndpoints.keySet()}"
    }

    @Override
    TaskHandler createTaskHandler(TaskRun task) {
        return new NIMTaskHandler(task, this)
    }

    @Override
    TaskMonitor createTaskMonitor() {
        return TaskPollingMonitor.create(session, 'nim', Duration.of('5sec'))
    }
    
    /**
     * Get the API key for a specific NIM service
     * Uses fallback hierarchy: service-specific config → global config → environment variable
     * 
     * @param service The NIM service name (e.g., 'rfdiffusion')
     * @return API key string, or null if none found
     */
    String getApiKey(String service) {
        // First try service-specific API key from configuration
        def serviceKey = nimApiKeys[service]
        if (serviceKey) {
            return serviceKey
        }
        
        // Fall back to environment variable
        def envKey = System.getenv('NVCF_RUN_KEY')
        if (envKey) {
            return envKey
        }
        
        // No API key found
        return null
    }
} 