/*
 * Copyright © 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier: BSD-2
 */


package org.dcm;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.reactivex.Flowable;
import io.reactivex.processors.PublishProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

class KubernetesStateSync {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesStateSync.class);
    private final SharedInformerFactory sharedInformerFactory;
    private final ThreadFactory namedThreadFactory =
            new ThreadFactoryBuilder().setNameFormat("flowable-thread-%d").build();
    private final ExecutorService service = Executors.newFixedThreadPool(10, namedThreadFactory);
    private final KubernetesClient client;

    KubernetesStateSync(final KubernetesClient client) {
        this.sharedInformerFactory = client.informers();
        this.client = client;
    }

    Flowable<PodEvent> setupInformersAndPodEventStream(final DBConnectionPool dbConnectionPool) {

        final SharedIndexInformer<Node> nodeSharedIndexInformer = sharedInformerFactory
                .sharedIndexInformerFor(Node.class, NodeList.class, 30000);
        nodeSharedIndexInformer.addEventHandler(new NodeResourceEventHandler(dbConnectionPool, service));

        // Pod informer
        final PublishProcessor<PodEvent> podEventPublishProcessor = PublishProcessor.create();
        final PodResourceEventHandler podResourceEventHandler =
                new PodResourceEventHandler(podEventPublishProcessor, service);
        client.pods().watch(new PodWatcher(podResourceEventHandler));
//        final SharedIndexInformer<Pod> podInformer = sharedInformerFactory
//                .sharedIndexInformerFor(Pod.class, PodList.class, 1000);
//        podInformer.addEventHandler(new PodResourceEventHandler(podEventPublishProcessor, service));

        LOG.info("Instantiated node and pod informers. Starting them all now.");

        return podEventPublishProcessor;
    }

    void startProcessingEvents() {
        sharedInformerFactory.startAllRegisteredInformers();
    }

    void shutdown() {
        sharedInformerFactory.stopAllRegisteredInformers();
    }

    private static class PodWatcher implements Watcher<Pod> {
        private final PodResourceEventHandler podResourceEventHandler;

        public PodWatcher(final PodResourceEventHandler podResourceEventHandler) {
            this.podResourceEventHandler = podResourceEventHandler;
        }

        @Override
        public void eventReceived(final Watcher.Action action, final Pod pod) {
            switch (action) {
                case ADDED:
                    podResourceEventHandler.onAdd(pod);
                    break;
                case MODIFIED:
                    podResourceEventHandler.onUpdate(pod);
                    break;
                case DELETED:
                    podResourceEventHandler.onDelete(pod);
                    break;
                case ERROR:
                default:
                    LOG.error("Received error {}", pod.getMetadata().getName());
            }
        }

        @Override
        public void onClose(final KubernetesClientException e) {
            LOG.error("Watch closed", e);
        }
    }
}