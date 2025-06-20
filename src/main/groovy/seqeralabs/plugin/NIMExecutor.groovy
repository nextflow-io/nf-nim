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
import org.pf4j.ExtensionPoint

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration

/**
 * Generic Nextflow executor for NVIDIA NIM services
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@ServiceName('nim')  
class NIMExecutor extends Executor implements ExtensionPoint {

    private Map<String, String> nimEndpoints
    private HttpClient httpClient

    @Override
    void register() {
        super.register()
        
        // Configure endpoint for RFDiffusion NIM service using NVIDIA API
        this.nimEndpoints = [
            'rfdiffusion': 'https://api.nvidia.com/v1/biology/ipd/rfdiffusion/generate'
        ]
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build()
                
        log.info "NIM executor registered with RFDiffusion endpoint"
    }

    @Override
    protected TaskMonitor createTaskMonitor() {
        return TaskPollingMonitor.create(session, name, 100)
    }

    @Override
    TaskHandler createTaskHandler(TaskRun task) {
        return new NIMTaskHandler(task, this)
    }

    /**
     * Task handler for NIM requests
     */
    static class NIMTaskHandler extends TaskHandler {

        private final NIMExecutor executor
        private volatile boolean completed
        private volatile int exitStatus
        private volatile String outputContent

        NIMTaskHandler(TaskRun task, NIMExecutor executor) {
            super(task)
            this.executor = executor
        }

        @Override
        void submit() {
            status = TaskStatus.SUBMITTED
            
            // Execute the NIM task in a separate thread
            Thread.start {
                try {
                    executeNIMTask()
                } catch (Exception e) {
                    log.error("Failed to execute NIM task", e)
                    exitStatus = 1
                    completed = true
                }
            }
        }

        @Override
        boolean checkIfRunning() {
            if (status == TaskStatus.SUBMITTED && !completed) {
                status = TaskStatus.RUNNING
                return true
            }
            return status == TaskStatus.RUNNING && !completed
        }

        @Override
        boolean checkIfCompleted() {
            if (completed) {
                if (exitStatus == 0) {
                    status = TaskStatus.SUCCEEDED
                } else {
                    status = TaskStatus.FAILED
                }
                return true
            }
            return false
        }

        @Override
        void kill() {
            completed = true
            exitStatus = 130 // SIGINT
            status = TaskStatus.ABORTED
        }

        int getExitStatus() {
            return exitStatus
        }

        private void executeNIMTask() {
            // Get the NIM service type from task.ext.nim
            String nimService = task.config.ext?.nim as String
            if (!nimService) {
                throw new IllegalArgumentException("task.ext.nim must be specified to indicate which NIM service to use")
            }
            
            String endpoint = executor.nimEndpoints[nimService]
            if (!endpoint) {
                throw new IllegalArgumentException("Unsupported NIM service: $nimService. Supported services: ${executor.nimEndpoints.keySet()}")
            }

            log.info "Executing NIM task: ${task.name} using service: ${nimService}"

            // For now, just simulate the task execution
            // TODO: Implement actual HTTP requests to NIM endpoints
            def outputFile = task.workDir.resolve('output.pdb')
            outputFile.text = """ATOM      1  N   ALA A  20      -8.901   4.127  -0.555  1.00 11.99           N  
ATOM      2  CA  ALA A  20      -8.608   3.135  -1.618  1.00 11.82           C  
ATOM      3  C   ALA A  20      -7.117   2.964  -1.897  1.00 11.75           C  
ATOM      4  O   ALA A  20      -6.632   1.849  -2.088  1.00 12.05           O  
ATOM      5  CB  ALA A  20      -9.303   3.421  -2.953  1.00 11.56           C"""

            log.info "NIM task simulated successfully: ${task.name}"
            exitStatus = 0
            completed = true
        }

        private String buildRequestBody(String nimService) {
            if (nimService != 'rfdiffusion') {
                throw new IllegalArgumentException("Only RFDiffusion is supported. Use ext.nim = 'rfdiffusion'")
            }
            return buildRFDiffusionRequest()
        }

        private String buildRFDiffusionRequest() {
            def inputPdb = getInputPdb()
            def contigs = task.processor.session.params.contigs ?: 'A20-60/0 50-100'
            def hotspotRes = task.processor.session.params.hotspot_res ?: ['A50', 'A51', 'A52', 'A53', 'A54']
            def diffusionSteps = task.processor.session.params.diffusion_steps ?: 15

            return new JsonBuilder([
                input_pdb: inputPdb,
                contigs: contigs,
                hotspot_res: hotspotRes,
                diffusion_steps: diffusionSteps
            ]).toString()
        }

        private void processResponse(String nimService, String responseBody) {
            def jsonSlurper = new JsonSlurper()
            def responseData = jsonSlurper.parseText(responseBody) as Map

            outputContent = responseData.output_pdb as String
            def outputFile = task.workDir.resolve('output.pdb')
            outputFile.text = outputContent
        }

        private String getInputPdb() {
            // Try to read PDB content from input file
            def inputFiles = task.getInputFiles()
            for (def entry : inputFiles) {
                def file = entry.value as Path
                if (file.fileName.toString().endsWith('.pdb')) {
                    def content = file.text
                    // Filter to ATOM lines and limit to first 400 lines (like the example)
                    def atomLines = content.split('\n')
                            .findAll { it.startsWith('ATOM') }
                            .take(400)
                    return atomLines.join('\n')
                }
            }

            // If no PDB file found, use default example (1R42.pdb content)
            log.warn "No PDB input file found, using default example structure"
            return getDefaultPdbContent()
        }

        private String getDefaultPdbContent() {
            // This would contain a small subset of 1R42.pdb ATOM lines
            // For now, return a minimal example
            return '''ATOM      1  N   ALA A  20      -8.901   4.127  -0.555  1.00 11.99           N  
ATOM      2  CA  ALA A  20      -8.608   3.135  -1.618  1.00 11.82           C  
ATOM      3  C   ALA A  20      -7.117   2.964  -1.897  1.00 11.75           C  
ATOM      4  O   ALA A  20      -6.632   1.849  -2.088  1.00 12.05           O  
ATOM      5  CB  ALA A  20      -9.303   3.421  -2.953  1.00 11.56           C'''
        }
    }
} 