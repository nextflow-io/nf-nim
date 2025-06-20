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

import nextflow.Session
import nextflow.processor.TaskRun
import spock.lang.Specification

/**
 * Tests for NIM executor
 */
class NIMExecutorTest extends Specification {

    def 'should create NIM executor with NVIDIA API endpoints'() {
        given:
        def session = Mock(Session)
        session.config >> [:]
        
        when:
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        then:
        executor.nimEndpoints['rfdiffusion'] == 'https://api.nvidia.com/v1/biology/ipd/rfdiffusion/generate'
        executor.nimEndpoints['alphafold2'] == 'https://api.nvidia.com/v1/biology/deepmind/alphafold2/predict'
        executor.nimEndpoints['esmfold'] == 'https://api.nvidia.com/v1/biology/meta/esmfold/predict'
        executor.nimEndpoints['deepvariant'] == 'https://api.nvidia.com/v1/biology/nvidia/deepvariant/call'
        executor.nimEndpoints['fq2bam'] == 'https://api.nvidia.com/v1/biology/nvidia/fq2bam/align'
    }

    def 'should create task handler'() {
        given:
        def session = Mock(Session)
        session.config >> [:]
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        def taskRun = Mock(TaskRun)
        
        when:
        def handler = executor.createTaskHandler(taskRun)
        
        then:
        handler instanceof NIMExecutor.NIMTaskHandler
    }

    @spock.lang.PendingFeature
    def 'should use custom endpoint from config'() {
        given:
        def customEndpoint = 'http://custom-nim-server:8080/biology/ipd/rfdiffusion/generate'
        def session = Mock(Session)
        session.config >> [nim: [rfdiffusion: [endpoint: customEndpoint]]]
        
        when:
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        then:
        executor.nimEndpoints['rfdiffusion'] == customEndpoint
        // Other endpoints should still use defaults
        executor.nimEndpoints['alphafold2'] == 'https://api.nvidia.com/v1/biology/deepmind/alphafold2/predict'
    }

    @spock.lang.PendingFeature
    def 'should support multiple custom endpoints'() {
        given:
        def customRFDiffusion = 'http://server1:8080/biology/ipd/rfdiffusion/generate'
        def customAlphaFold = 'http://server2:8080/biology/deepmind/alphafold2/predict'
        def session = Mock(Session)
        session.config >> [nim: [
            rfdiffusion: [endpoint: customRFDiffusion],
            alphafold2: [endpoint: customAlphaFold]
        ]]
        
        when:
        def executor = new NIMExecutor()
        executor.session = session
        executor.register()
        
        then:
        executor.nimEndpoints['rfdiffusion'] == customRFDiffusion
        executor.nimEndpoints['alphafold2'] == customAlphaFold
        executor.nimEndpoints['esmfold'] == 'https://api.nvidia.com/v1/biology/meta/esmfold/predict'
    }
} 