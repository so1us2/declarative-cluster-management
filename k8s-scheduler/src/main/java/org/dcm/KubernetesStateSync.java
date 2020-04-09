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
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;

class KubernetesStateSync {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesStateSync.class);
    private final SharedInformerFactory sharedInformerFactory;
    private final ThreadFactory namedThreadFactory =
            new ThreadFactoryBuilder().setNameFormat("flowable-thread-%d").build();
    private final ExecutorService service = Executors.newFixedThreadPool(10, namedThreadFactory);
    private final KubernetesClient client;
    private final BlockingQueue<PodEvent> taskQueue = new LinkedBlockingQueue<>();
    @Nullable private PodEventsToDatabase podEventsToDatabase;
    @Nullable private Watch watch;

    KubernetesStateSync(final KubernetesClient client) {
        this.sharedInformerFactory = client.informers();
        this.client = client;
    }

    BlockingQueue<PodEvent> setupInformersAndPodEventStream(final DBConnectionPool dbConnectionPool,
                                                            final PodEventsToDatabase podEventsToDatabase) {

        final SharedIndexInformer<Node> nodeSharedIndexInformer = sharedInformerFactory
                .sharedIndexInformerFor(Node.class, NodeList.class, 30000);
        nodeSharedIndexInformer.addEventHandler(new NodeResourceEventHandler(dbConnectionPool, service));
        this.podEventsToDatabase = podEventsToDatabase;
        // Pod informer
        return taskQueue;
    }

    void startProcessingEvents() {
        assert podEventsToDatabase != null;
        final PodResourceEventHandler podResourceEventHandler =
                new PodResourceEventHandler(taskQueue, podEventsToDatabase, service);
        watch = client.pods().watch(new PodWatcher(podResourceEventHandler));
        LOG.info("Instantiated node and pod informers. Starting them all now.");

        sharedInformerFactory.startAllRegisteredInformers();
    }

    void shutdown() {
        sharedInformerFactory.stopAllRegisteredInformers();
        assert watch != null;
        watch.close();
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