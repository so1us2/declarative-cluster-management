/*
 * Copyright © 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier: BSD-2
 */

package org.dcm;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.fabric8.kubernetes.api.model.Binding;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.dcm.k8s.generated.Tables;
import org.jooq.Record;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadFactory;


/**
 * Pod -> node binding implementation that works with a real Kubernetes cluster
 */
class KubernetesBinder implements IPodToNodeBinder {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesBinder.class);
    private final KubernetesClient client;
    private final ThreadFactory namedThreadFactory =
            new ThreadFactoryBuilder().setNameFormat("bind-thread-%d")
                    .setUncaughtExceptionHandler((t, e) -> LOG.error("Binding exception {}", t.getName(), e))
                    .build();
    private final ExecutorService service = Executors.newFixedThreadPool(10, namedThreadFactory);

    KubernetesBinder(final KubernetesClient client) {
        this.client = client;
    }

    public void bindOne(final String namespace, final String podName, final String nodeName) {
        final Binding binding = new Binding();
        final ObjectReference target = new ObjectReference();
        final ObjectMeta meta = new ObjectMeta();
        target.setKind("Node");
        target.setApiVersion("v1");
        target.setName(nodeName);
        meta.setName(podName);
        binding.setTarget(target);
        binding.setMetadata(meta);
        final long now = System.nanoTime();
        client.bindings().inNamespace(namespace).create(binding);
        LOG.info("Binding for pod {} to node {} took {}ns", podName, nodeName, (System.nanoTime() - now));
    }

    @Override
    public void bindManyAsnc(final Result<? extends Record> records) {
        ForkJoinPool.commonPool().execute(() -> records.forEach(
                r -> service.execute(() -> {
                    final String podName = r.get(Tables.PODS_TO_ASSIGN.POD_NAME);
                    final String namespace = r.get(Tables.PODS_TO_ASSIGN.NAMESPACE);
                    final String nodeName = r.get(Tables.PODS_TO_ASSIGN.CONTROLLABLE__NODE_NAME);
                    bindOne(namespace, podName, nodeName);
                }
            )
        ));
    }
}
