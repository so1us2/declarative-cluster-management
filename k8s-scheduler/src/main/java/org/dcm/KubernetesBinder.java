/*
 * Copyright © 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier: BSD-2
 */

package org.dcm;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Binding;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1ObjectReference;
import io.kubernetes.client.util.Config;
import org.dcm.k8s.generated.Tables;
import org.jooq.Record;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadFactory;


/**
 * Pod -> node binding implementation that works with a real Kubernetes cluster
 */
class KubernetesBinder implements IPodToNodeBinder {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesBinder.class);
//    private final KubernetesClient client;
    private final CoreV1Api coreV1Api;
    private final ThreadFactory namedThreadFactory =
            new ThreadFactoryBuilder().setNameFormat("bind-thread-%d")
                    .setUncaughtExceptionHandler((t, e) -> LOG.error("Binding exception {}", t.getName(), e))
                    .build();
    private final ExecutorService service = Executors.newFixedThreadPool(10, namedThreadFactory);

    KubernetesBinder(final KubernetesClient client) {
//        this.client = client;
        try {
            final ApiClient c = Config.defaultClient();
            Configuration.setDefaultApiClient(c);
            coreV1Api = new CoreV1Api();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void bindOne(final String namespace, final String podName, final String uid, final String nodeName) {
//        final Binding binding = new Binding();
//        final ObjectReference target = new ObjectReference();
//        final ObjectMeta meta = new ObjectMeta();
//        target.setKind("Node");
//        target.setApiVersion("v1");
//        target.setName(nodeName);
//        meta.setName(podName);
//        binding.setTarget(target);
//        binding.setMetadata(meta);
//        final long now = System.nanoTime();
//        client.bindings().inNamespace(namespace).create(binding).;

        final V1Binding body = new V1Binding();
        final V1ObjectReference target = new V1ObjectReference();
        target.setKind("Node");
        target.setName(nodeName);
        final V1ObjectMeta meta = new V1ObjectMeta();
        meta.setName(podName);
        meta.setNamespace(namespace);
        meta.setUid(uid);
        body.setMetadata(meta);
        body.setTarget(target);
        final long now = System.nanoTime();
        try {
            final V1Binding namespacedBinding =
                coreV1Api.createNamespacedPodBinding(podName, namespace, body, null, null, null);
            LOG.info("Binding for pod {}({}) to node {} took {}ns: response ---- {}",
                    podName, uid, nodeName, (System.nanoTime() - now), namespacedBinding);
        } catch (final ApiException e) {
            e.printStackTrace();
            LOG.error("Binding for pod {}({}) to node {} failed ({}ns)", podName, uid,
                    nodeName, (System.nanoTime() - now), e);
        }
        try {
            final V1Binding namespacedBinding =
                    coreV1Api.createNamespacedPodBinding(podName, namespace, body, null, null, null);
            LOG.info("RETRY Binding for pod {}({}) to node {} took {}ns: response ---- {}",
                    podName, uid, nodeName, (System.nanoTime() - now), namespacedBinding);
        } catch (final ApiException e) {
            e.printStackTrace();
            LOG.error("RETRY Binding for pod {}({}) to node {} failed ({}ns)", podName, uid,
                    nodeName, (System.nanoTime() - now), e);
        }
    }

    @Override
    public void bindManyAsnc(final Result<? extends Record> records) {
        ForkJoinPool.commonPool().execute(() -> records.forEach(
                r -> service.execute(() -> {
                    final String podName = r.get(Tables.PODS_TO_ASSIGN.POD_NAME);
                    final String namespace = r.get(Tables.PODS_TO_ASSIGN.NAMESPACE);
                    final String nodeName = r.get(Tables.PODS_TO_ASSIGN.CONTROLLABLE__NODE_NAME);
                    final String uid = r.get(Tables.PODS_TO_ASSIGN.UID);
                    bindOne(namespace, podName, uid, nodeName);
                }
            )
        ));
    }
}
